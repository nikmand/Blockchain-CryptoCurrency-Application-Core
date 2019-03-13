package distributed.core.threads;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.security.PublicKey;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import distributed.core.beans.Block;
import distributed.core.beans.Message;
import distributed.core.beans.MsgBlock;
import distributed.core.beans.MsgChain;
import distributed.core.beans.MsgChainRequest;
import distributed.core.beans.MsgChainSizeRequest;
import distributed.core.beans.MsgChainSizeResponse;
import distributed.core.beans.MsgInitialize;
import distributed.core.beans.MsgIsAlive;
import distributed.core.beans.MsgNodeId;
import distributed.core.beans.MsgNodesInfo;
import distributed.core.beans.MsgTrans;
import distributed.core.entities.Blockchain;
import distributed.core.entities.NodeMiner;
import distributed.core.entities.Transaction;

public class ClientServerThread extends Thread {

	private static final Logger LOG = LoggerFactory.getLogger(ClientServerThread.class.getName());

	private Socket socket;
	private ServerThread server;
	private NodeMiner miner;

	public Socket getSocket() {
		return socket;
	}

	public void setSocket(Socket socket) {
		this.socket = socket;
	}

	public ClientServerThread(Socket socket, ServerThread server, NodeMiner _miner) {
		this.socket = socket;
		this.server = server;
		this.miner = _miner;
	}

	private void handleMessage(Message message) {
		// Function that handles the received message
		if (message instanceof MsgInitialize) { // μηνύματα initialize θα λάβει μόνο ο boostrap node
			MsgInitialize msg = (MsgInitialize) message;
			PublicKey peerKey = msg.getPublicKey();
			LOG.info("Node is a newbie and his public key is {}", peerKey);
			miner.addNode(Triple.of(msg.getPublicKey(), msg.getIpAddress(), msg.getPort()));
			int nodesSoFar = miner.numOfNodesInserted();
			MsgNodeId newMsg = new MsgNodeId(nodesSoFar - 1);
			(new ClientThread(msg.getIpAddress(), msg.getPort(), newMsg)).start();
			// την απάντηση μπορούμε να τη στείλουμε στο ίδιο socket?
			/* LOG.info("peer address = {}",
			 * socket.getInetAddress().toString().substring(1));
			 * LOG.info("peer address = {}", socket.getRemoteSocketAddress());
			 * LOG.info("peer address = {}", socket.getPort()); */
			// Pair<String, Integer> aux = miner.getNode(peerKey); // δε χρειάζεται να τα
			// ψάξουμε

			if (nodesSoFar == miner.getNumOfNodes()) { // ενέργειες που κάνουμε μόλις εισαχθούν όλοι
				// server.getMiner().broadcastMsg(new MsgIsAlive());
				LOG.info("All nodes sent their info, replying with the collection of nodes and the blockchain");
				MsgNodesInfo msgInfo = new MsgNodesInfo(miner.getHashMap());
				miner.broadcastMsg(msgInfo); // broadcast τη δομή με τα στοιχεία των κόμβων
				MsgChain msgChain = new MsgChain(miner.getBlockchain());
				//LOG.debug("num of utxos = {}", miner.getBlockchain().getUTXOs().size());
				miner.broadcastMsg(msgChain); // broadcast το blockchain
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				// broadcast n-1 transactions
				ConcurrentHashMap<String, Triple<PublicKey, String, Integer>> _nodes = miner.getHashMap();
				for (Entry<String, Triple<PublicKey, String, Integer>> entry : _nodes.entrySet()) {
					if (entry.getKey().equals(miner.getId())) {
						LOG.info("I do not send money to myself!");
						continue;
					}
					LOG.info("Sending money to node with public key={} in address={}", entry.getValue().getLeft(),
							entry.getValue().getMiddle() + ":" + entry.getValue().getRight());
					Transaction marshallPlan = miner.sendFunds(entry.getValue().getLeft(), 100);
					Transaction deepCopy = SerializationUtils.clone(marshallPlan); // deep Copy the trans so it won't get modified while being broadcasting
					MsgTrans msgTrans = new MsgTrans(deepCopy);
					miner.getCurrentBlock().addTransaction(marshallPlan, miner.getBlockchain()); // validation happens here
					miner.broadcastMsg(msgTrans); //first broadcast then validated it
					//if it isn't valid?
					if (miner.getCurrentBlock().proceedWithMine()) {
						miner.mineBlock();
					}
					LOG.debug("New balance is ={}", miner.getBalance());
					//break; // for debug
				}
				/* for (;;) { MsgTrans msgTrans = new MsgTrans(new Transaction());
				 * miner.broadcastMsg(msgTrans); } */
			}
			// socket.getPort(), message)).start();
			/* ObjectOutputStream outputStream; try { outputStream = new
			 * ObjectOutputStream(this.socket.getOutputStream());
			 * outputStream.writeObject(new_msg); } catch (IOException e) { // TODO
			 * Auto-generated catch block e.printStackTrace(); } */
		} else if (message instanceof MsgNodeId) {

			MsgNodeId msg = (MsgNodeId) message;
			int id = msg.getId();
			LOG.info("Hello me id is {}", id);
			miner.setId("id" + id);

		} else if (message instanceof MsgNodesInfo) {

			MsgNodesInfo msg = (MsgNodesInfo) message;
			miner.setNodes(msg.getNodes());
			// miner.showNodes();

		} else if (message instanceof MsgChain) {

			LOG.info("Message sending us the blockchain was received");
			MsgChain msg = (MsgChain) message;
			synchronized (NodeMiner.lockBlockchain) {
				miner.setBlockchain(msg.getChain()); // we set the blockchain!!! thead safety alert!
				Blockchain blockchain = miner.getBlockchain();
				blockchain.printBlockChain();
				//LOG.debug("num of utxos after send = {}", blockchain.getUTXOs().size());
				if (blockchain.isBlockchainValid()) {
					LOG.info("BlockChain is valid");
				} else {
					LOG.warn("BlockChain invalid!!");
				}
			}

		} else if (message instanceof MsgTrans) {

			MsgTrans msg = (MsgTrans) message;
			Transaction trans = msg.getTransaction();
			LOG.info("A transaction was received! {}", trans);
			// validate it !
			synchronized (NodeMiner.lock) {
				miner.getCurrentBlock().addTransaction(trans, miner.getBlockchain()); // validation happens here

				if (miner.getCurrentBlock().proceedWithMine()) {
					miner.mineBlock();
				} else {
					LOG.warn("Probably block is not full yet!");
				}
			}
			//LOG.debug("New balance is ={}", miner.getBalance());

		} else if (message instanceof MsgBlock) {

			MsgBlock msgBlock = (MsgBlock) message;
			Block block = msgBlock.getBlock();
			LOG.info("A block was received! {}", block);
			synchronized (NodeMiner.lockBlockchain) {
				if (block.validateBlock(miner.getBlockchain().getLastHash())) {
					miner.alone.set(false); // TODO check what happens if a block isn't mined at that time!!
					miner.getBlockchain().addToChain(block);
					LOG.info("Block that was received added to chain");
				} else {
					LOG.warn("Invalid block received, block was {}", block);
					MsgChainSizeRequest msgSize = new MsgChainSizeRequest(miner.getId());
					LOG.info("Sending requests for blockchain size");
					miner.setSizeOfNodeChain();
					miner.broadcastMsg(msgSize);
				}
			}

		} else if (message instanceof MsgChainSizeRequest) {

			LOG.info("Request for blockchain's size received");
			MsgChainSizeRequest msgSize = (MsgChainSizeRequest) message;

			String id = msgSize.getId();
			Triple<PublicKey, String, Integer> value = miner.getNode(id);

			MsgChainSizeResponse newMsg = new MsgChainSizeResponse(miner.getBlockchain().getSize(), miner.getId());
			(new ClientThread(value.getMiddle(), value.getRight(), newMsg)).start();

		} else if (message instanceof MsgChainSizeResponse) {

			LOG.info("Msg with size of blockchain received");
			MsgChainSizeResponse msgSize = (MsgChainSizeResponse) message;
			miner.setSizeOfNodeChain(msgSize.getId(), msgSize.getSize());
			if (miner.getChainSizeOther() == miner.getNumOfNodes()) {
				LOG.info("All sizes received, checking if there is bigger blockchain");
				synchronized (NodeMiner.lockBlockchain) { // sync so our blockchain don't expand while on this step
					String id = miner.getNodeLargestChain();
					if (id != null) {
						LOG.info("Bigger chain detected, sending request");
						Triple<PublicKey, String, Integer> value = miner.getNode(id);
						MsgChainRequest chainReq = new MsgChainRequest(miner.getId()); // λάθος η αίτηση περιέχει τα στοιχεία του node με τη μεγαλύτερη αλυσίδα
						(new ClientThread(value.getMiddle(), value.getRight(), chainReq)).start(); // send message to node with largest chain
					} else {
						LOG.info("There isn't a bigger chain");
					}
					miner.deleteAllSizes();
					LOG.info("Clear the hash map from sizes");
				}
			}

		} else if (message instanceof MsgChainRequest) {
			//TODO send only blocks that are missing from peer not the whole blockchain
			LOG.info("Message requesting blockchain received");
			Blockchain deepcopy = SerializationUtils.clone(miner.getBlockchain());
			MsgChain msgChain = new MsgChain(deepcopy);

			MsgChainRequest msg = (MsgChainRequest) message;
			String peerId = msg.getId();
			Triple<PublicKey, String, Integer> value = miner.getNode(peerId);
			(new ClientThread(value.getMiddle(), value.getRight(), msgChain)).start();

		} else if (message instanceof MsgIsAlive) { // just for check

			MsgIsAlive msg = (MsgIsAlive) message;
			LOG.info("Server says: {}", msg.echo);
			LOG.info("Ohh yes I am here");
		}

	}

	@Override
	public void run() {
		try {
			// Open an input Stream to read the object arrived on the incoming socket
			ObjectInputStream objectInputStream = new ObjectInputStream(this.socket.getInputStream());

			// Cast the object to its expected format! From ObjectInputStream anything is read as the abstract Object type
			Message msg = (Message) objectInputStream.readObject();

			// Handle the incoming message
			//LOG.debug("Message received from node {}:{}", socket.getInetAddress().getHostAddress(), socket.getPort());
			handleMessage(msg);

			// Close the socket since we got its input
			this.socket.close();
		} catch (IOException e) {
			e.printStackTrace();

		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

	}
}
