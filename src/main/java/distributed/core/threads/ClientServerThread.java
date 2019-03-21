package distributed.core.threads;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import distributed.core.beans.Block;
import distributed.core.beans.Message;
import distributed.core.beans.MsgBlock;
import distributed.core.beans.MsgBlocks;
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
import distributed.core.exceptions.InvalidHashException;
import distributed.core.utilities.Constants;

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

	// Function that handles the received message
	private void handleMessage(Message message) {
		LOG.trace("START handle message");

		if (message instanceof MsgInitialize) { // μηνύματα initialize θα λάβει μόνο ο boostrap node
			MsgInitialize msg = (MsgInitialize) message;
			PublicKey peerKey = msg.getPublicKey();

			LOG.info("Node is a newbie and his public key is {}", peerKey);
			miner.addNode(Triple.of(msg.getPublicKey(), msg.getIpAddress(), msg.getPort()));
			int nodesSoFar = miner.numOfNodesInserted();

			MsgNodeId newMsg = new MsgNodeId(nodesSoFar - 1);
			(new ClientThread(msg.getIpAddress(), msg.getPort(), newMsg)).start();

			if (nodesSoFar == Constants.NUM_OF_NODES) { // ενέργειες που κάνουμε μόλις εισαχθούν όλοι

				LOG.info("All nodes sent their info, replying with the collection of nodes and the blockchain");
				MsgNodesInfo msgInfo = new MsgNodesInfo(miner.getHashMap(), miner.getNodesPid());
				miner.broadcastMsg(msgInfo); // broadcast τη δομή με τα στοιχεία των κόμβων

				MsgChain msgChain = new MsgChain(miner.getBlockchain());
				miner.broadcastMsg(msgChain); // broadcast blockchain

				try {
					Thread.sleep(1000);	// wait in order others to catch up
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				// broadcast n-1 transactions
				for (int i = 1; i < nodesSoFar; i++) {
					miner.createAndSend("id" + i, 100);
					LOG.debug("New balance is ={}", miner.getBalance());
					//break; // for debug
				}
			}
		} else if (message instanceof MsgNodeId) {

			MsgNodeId msg = (MsgNodeId) message;
			int id = msg.getId();
			LOG.info("Hello me id is {}", id);
			miner.setId("id" + id);

		} else if (message instanceof MsgNodesInfo) {

			MsgNodesInfo msg = (MsgNodesInfo) message;
			miner.setNodes(msg.getNodes());
			miner.setNodesPid(msg.getNodesPid());
			// miner.showNodes();

		} else if (message instanceof MsgChain) {

			LOG.info("Message sending us the blockchain was received");
			MsgChain msg = (MsgChain) message;
			synchronized (NodeMiner.lockBlockchain) {
				Blockchain blockchain = msg.getChain();
				LOG.info("Blockchain received is:");
				blockchain.printBlockChain();
				//LOG.debug("num of utxos after send = {}", blockchain.getUTXOs().size());
				if (blockchain.validateChain()) {
					//synchronized (NodeMiner.mining) {
					LOG.info("BlockChain is valid");
					miner.alone.compareAndSet(true, false);
					miner.setBlockchain(blockchain);
					//}
				} else {
					LOG.warn("BlockChain invalid!!");
				}
			}

		} else if (message instanceof MsgTrans) {

			MsgTrans msg = (MsgTrans) message;
			Transaction trans = msg.getTransaction();
			LOG.info("A transaction was received! {}", trans);
			NodeMiner.transReceived++;
			// validate it !
			synchronized (NodeMiner.lock) { // lock εδώ, ώστε κάθε στιγμή να μπορεί να προστεθεί μόνο μία txn στο μπλοκ
				miner.getCurrentBlock().addTransaction(trans, miner.getBlockchain()); // validation happens here

				//if (miner.getCurrentBlock().proceedWithMine()) {
				miner.mineBlock();
				/*} else {
					LOG.warn("Probably block is not full yet!");
				}*/
			}
			//LOG.debug("New balance is ={}", miner.getBalance());

		} else if (message instanceof MsgBlock) {

			MsgBlock msgBlock = (MsgBlock) message;
			Block block = msgBlock.getBlock();
			LOG.info("A block was received! {}", block);
			boolean flag;
			synchronized (NodeMiner.lockBlockchain) {
				try {
					flag = miner.validateReceivedBlock(block, miner.getBlockchain().getLastHash(), null);
				} catch (InvalidHashException e) {
					LOG.warn("Invalid block received, block was {}", block);
					MsgChainSizeRequest msgSize = new MsgChainSizeRequest(miner.getId());
					LOG.info("Sending requests for blockchain size");
					miner.setSizeOfNodeChain();
					miner.broadcastMsg(msgSize);
					return;
				}
				if (flag) {
					miner.alone.compareAndSet(true, false);
					miner.getBlockchain().addToChain(block);
					LOG.info("Block that was received added to chain");
				} else {
					LOG.warn("Invalid block received, block was {}", block);
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
			miner.setSizeOfNodeChain(msgSize.getId(), msgSize.getSize()); // race condition on size collection add synchronized
			if (miner.getChainSizeOther() == Constants.NUM_OF_NODES) {
				LOG.info("All sizes received, checking if there is bigger blockchain");
				synchronized (NodeMiner.lockBlockchain) { // sync so our blockchain don't expand while on this step
					String id = miner.getNodeLargestChain();
					if (id != null) {
						LOG.info("Bigger chain detected, sending request to node {}", id);
						Triple<PublicKey, String, Integer> value = miner.getNode(id);
						List<String> hashes = miner.getBlockchain().getBlockchain().stream().map(Block::getCurrentHash)
								.collect(Collectors.toList());
						MsgChainRequest chainReq = new MsgChainRequest(miner.getId(), hashes);
						(new ClientThread(value.getMiddle(), value.getRight(), chainReq)).start(); // send message to node with largest chain
					} else {
						LOG.info("There isn't a bigger chain");
					}
					miner.deleteAllSizes();
					LOG.info("Clear the hash map from sizes");
				}
			}

		} else if (message instanceof MsgChainRequest) {

			MsgChainRequest msg = (MsgChainRequest) message;
			String peerId = msg.getId();
			LOG.info("Message requesting blockchain received from node with id={}", peerId);
			List<String> hashes = msg.getHashes();
			Pair<ArrayList<Block>, Integer> aux = null;
			synchronized (NodeMiner.lockBlockchain) {
				aux = miner.getBlockchain().findDiff(hashes);
			}
			ArrayList<Block> blocks = aux.getKey();
			Integer index = aux.getValue();
			if (blocks == null || index == null) {
				LOG.warn("Unable to find difference in blockchain. Aborting...");
				return;
			}
			LOG.info("Sending blocks to node with id={}", peerId);

			MsgBlocks msgBlocks = new MsgBlocks(blocks, index);
			Triple<PublicKey, String, Integer> value = miner.getNode(peerId);
			(new ClientThread(value.getMiddle(), value.getRight(), msgBlocks)).start();

		} else if (message instanceof MsgBlocks) {

			LOG.info("Message sending blocks was received");
			MsgBlocks msg = (MsgBlocks) message;
			synchronized (NodeMiner.lockBlockchain) { // we operate on the chain we NEED the lock
				miner.getBlockchain().handleBlocks(msg.getBlocks(), msg.getIndex(), miner);
			}

		} else if (message instanceof MsgIsAlive) { // just for check

			MsgIsAlive msg = (MsgIsAlive) message;
			LOG.info("Server says: {}", msg.echo);
			LOG.info("Ohh yes I am here");
		}

	}

	@Override
	public void run() {
		try {
			ObjectInputStream objectInputStream = new ObjectInputStream(this.socket.getInputStream());
			Message msg = (Message) objectInputStream.readObject();
			handleMessage(msg);
			this.socket.close();
		} catch (IOException e) {
			e.printStackTrace();

		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

	}
}
