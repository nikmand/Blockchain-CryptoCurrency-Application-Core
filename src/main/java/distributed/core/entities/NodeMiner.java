package distributed.core.entities;

import java.io.BufferedReader;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.security.PublicKey;
import java.security.Security;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import distributed.core.beans.Block;
import distributed.core.beans.Message;
import distributed.core.beans.MsgBlock;
import distributed.core.beans.MsgInitialize;
import distributed.core.threads.ClientThread;
import distributed.core.threads.ServerThread;
import distributed.core.utilities.Constants;

/*
 * Class that represents a miner.
 */
public class NodeMiner {

	private static final Logger LOG = LoggerFactory.getLogger(NodeMiner.class.getName());

	private Wallet wallet;
	private String address;
	private int id;
	private int port;
	private int numOfNodes;
	public AtomicBoolean alone;
	private ConcurrentHashMap<PublicKey, Pair<String, Integer>> nodes; // TODO να γίνει κλάση ότι περιέχεται στο string ?
	private ConcurrentHashMap<PublicKey, Integer> blockchainSizes;
	private Blockchain blockchain;
	// private HashMap<String, TransactionOutput> allUTXOs = new HashMap<String,
	// TransactionOutput>();
	private Block currentBlock;
	private ServerThread server;

	public NodeMiner(int port) {
		this.wallet = new Wallet();
		this.currentBlock = new Block();
		alone = new AtomicBoolean(true);
		this.nodes = new ConcurrentHashMap<PublicKey, Pair<String, Integer>>();
		this.blockchainSizes = new ConcurrentHashMap<PublicKey, Integer>();
		try {
			this.address = Inet4Address.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			LOG.warn("Cannot find host address");
		}
		this.port = port;
		nodes.put(wallet.getPublicKey(), Pair.of(address, port));
		LOG.info("Starting miner in address={}:{}", address, port);
	}

	public void setSizeOfNodeChain() {
		blockchainSizes.put(this.getPublicKey(), this.blockchain.getSize());

	}

	public void setSizeOfNodeChain(PublicKey key, int num) {
		blockchainSizes.put(key, num);

	}

	public void deleteAllSizes() {
		blockchainSizes.clear();
	}

	public int getChainSizeOther() {
		return this.blockchainSizes.size();
	}

	public PublicKey getNodeLargestChain() {
		int max = 0;
		PublicKey argmax = null;
		for (Entry<PublicKey, Integer> entry : blockchainSizes.entrySet()) {
			int size = entry.getValue();
			if (size > max) {
				max = size;
				argmax = entry.getKey();
			}
		}
		if (max > blockchain.getSize()) {
			return argmax;
		} else {
			return null;
		}
	}

	public float getBalance() {
		return this.wallet.getBalance(blockchain.getUTXOs());
	}

	public Block getCurrentBlock() {
		return currentBlock;
	}

	public void setCurrentBlock(Block currentBlock) {
		this.currentBlock = currentBlock;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public Transaction sendFunds(PublicKey _recipient, float value) {
		return this.wallet.sendFunds(_recipient, value, blockchain.getUTXOs());
	}

	public PublicKey getPublicKey() {
		return this.wallet.getPublicKey();
	}

	public Blockchain getBlockchain() {
		return blockchain;
	}

	public void setBlockchain(Blockchain blockchain) {
		this.blockchain = blockchain;
	}

	public int getNumOfNodes() {
		return numOfNodes;
	}

	public void setNumOfNodes(int numOfNodes) {
		this.numOfNodes = numOfNodes;
	}

	public String getAddress() {
		return address;
	}

	public int getPort() {
		return port;
	}

	public ConcurrentHashMap<PublicKey, Pair<String, Integer>> getHashMap() {
		return nodes;
	}

	public void setNodes(ConcurrentHashMap<PublicKey, Pair<String, Integer>> _nodes) {
		nodes = _nodes;
	}

	public int numOfNodesInserted() {
		return nodes.size();
	}

	public void addNode(PublicKey key, Pair<String, Integer> value) {
		nodes.put(key, value);
	}

	public Pair<String, Integer> getNode(PublicKey key) {
		return nodes.get(key);
	}

	public void showNodes() {
		for (Entry<PublicKey, Pair<String, Integer>> entry : nodes.entrySet()) {
			LOG.info("Node with public key={} in address={}", entry.getKey(),
					entry.getValue().getLeft() + ":" + entry.getValue().getRight());
		}
	}

	/* todo : utility to mine a new Block */
	public void mineBlock() {
		LOG.info("Starting mine block");

		// Block currentBlock = new Block(null); // todo proper call to constructor
		currentBlock.setPreviousHash(blockchain.getLastHash());
		String target = new String(new char[Constants.DIFFICULTY]).replace('\0', '0'); // Create a string with
																						// difficulty * "0"
		LOG.info("Check that hash of block starts with {} zeros", target);
		while (!currentBlock.getCurrentHash().substring(0, Constants.DIFFICULTY).equals(target) && alone.get()) {
			currentBlock.setNonce(currentBlock.getNonce() + 1);
			currentBlock.setCurrentHash(currentBlock.calculateHash());
		}
		LOG.info("Block Mined with hash value={}", currentBlock.getCurrentHash());
		// add to block chain set new block as the current
		synchronized (blockchain) {
			if (currentBlock.validateBlock(blockchain.getLastHash())) {
				LOG.info("Block added to chain");
				blockchain.addToChain(currentBlock);
				MsgBlock msgBlock = new MsgBlock(currentBlock);
				broadcastMsg(msgBlock);
			} else {
				LOG.warn("Invalid block detected, not broadcasted, block was {}", currentBlock);
			}
		}
		currentBlock = new Block();
		alone = new AtomicBoolean(true);
	}

	/**
	 * todo : Utility to initiliaze any network connections. Call upon start
	 */
	// TODO σε περίπτωση αποτυχίας να περιμένει ένα διάστημα και έπειτα να
	// ξαναπροσπαθεί να συνθεδεί
	public void initiliazeNetoworkConnections() {
		// TODO do we need to check if server is running ? propably not we don't care
		// about our server
		if (address.equals(Constants.BOOTSTRAPADDRESS) && port == Constants.BOOTSTRAPPORT) {
			LOG.info("I am the bootstrap node");
			setId(0);
			Transaction genesisTrans = new Transaction(null, this.wallet.getPublicKey(), 100 * numOfNodes, null);
			genesisTrans.setTransactionId("0");
			// genesisTrans.generateSignature(wallet.getPrivateKey());
			TransactionOutput genesisOutputTrans = new TransactionOutput(this.wallet.getPublicKey(), 100 * numOfNodes,
					"0");
			genesisTrans.addToOutputs(genesisOutputTrans);
			blockchain.getUTXOs().put(genesisOutputTrans.getId(), genesisOutputTrans);

			Block genesisBlock = new Block("1");
			genesisBlock.setNonce(0);
			genesisBlock.addTransaction(genesisTrans, blockchain);
			genesisBlock.calculateHash();
			blockchain.addToChain(genesisBlock);

			LOG.info("Wallet balance for node-0  is {}", getBalance());
			return;
		} else {
			MsgInitialize message = new MsgInitialize(this.wallet.getPublicKey(), this.address, this.port);
			(new ClientThread(Constants.BOOTSTRAPADDRESS, Constants.BOOTSTRAPPORT, message)).start();
		}
	}

	public void broadcastMsg(Message msg) {
		LOG.info("Start broadcasting message");
		//LOG.trace("Start broadcasting message={}", msg);

		for (Entry<PublicKey, Pair<String, Integer>> entry : nodes.entrySet()) {
			if (entry.getKey().equals(this.getPublicKey())) {
				// Do not send it back to myself
				continue;
			}
			(new ClientThread(entry.getValue().getLeft(), entry.getValue().getRight(), msg)).start();
			/* try { //debug //gives catastrophic results! if (msg instanceof MsgTrans) {
			 * Thread.sleep(5000); } } catch (InterruptedException e) { // TODO
			 * Auto-generated catch block e.printStackTrace(); } */
		}
	}

	/**
	 * Function adding a new transaction to blockchain
	 *
	 * @param transaction
	 * @param broadcast
	 * @return whether the transaction was added or not
	 */
	public boolean addTransactionToBlockchain(Transaction transaction, boolean broadcast) {
		return false;
	}

	public static void main(String[] args) throws Exception {
		Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
		// String myAddress = Inet4Address.getLocalHost().getHostAddress(); // args[0];
		// // δε χρειάζεται δημιουργείται εξ
		// ορισμού στην ip της εφαρμογής
		if (args.length < 1) {
			LOG.warn("A port number must be provided in order for the node to start. Exiting...");
			return;
		}
		int myPort = Integer.parseInt(args[0]);
		int numOfNodes = 3;
		if (args.length < 2) {
			LOG.warn("Number of nodes wasn't specified, procedding with defaults which is 3!");
		} else {
			numOfNodes = Integer.parseInt(args[1]);
		}

		NodeMiner node = new NodeMiner(myPort);
		node.setNumOfNodes(numOfNodes);
		node.setBlockchain(new Blockchain());
		// Define new server
		ServerThread server = new ServerThread(myPort, node);

		LOG.info("About to start server...");
		server.start(); // εκκινούμε το thread του server όπου μας έρχονται μηνύματα

		// connectToBootstrap(myAddress, myPort);
		node.initiliazeNetoworkConnections();

		InputStream is = null;
		BufferedReader br = null;

		Thread.sleep(10000); // for debug
		node.getBlockchain().printBlockChain();
		LOG.info("Size of blockchain={}", node.getBlockchain().getSize());

		server.setRunning(false);

		/* while (server.isRunning()) { // παρακαλουθούμε την είσοδο που δίνει ο χρήστης
		 * try {
		 *
		 * is = System.in; br = new BufferedReader(new InputStreamReader(is));
		 *
		 * String line = null;
		 *
		 * while ((line = br.readLine()) != null) { LOG.info("Input was given"); (new
		 * ClientThread(BOOTSTRAPADDRESS, BOOTSTRAPPORT, line)).start(); // δημιουργούμε
		 * νέο thread για να // στείλουμε σε άλλο // χρήστη το μήνυμα που δόθηκε if
		 * (line.equalsIgnoreCase("exit")) { server.setRunning(false); break; }
		 * LOG.info("Line sending : \n\t" + line); }
		 *
		 * } catch (IOException ioe) {
		 *
		 * } finally { // close the streams using close method try { if (br != null) {
		 * br.close(); } } catch (IOException ioe) {
		 * LOG.warn("Error while closing stream: " + ioe); } if (!server.isRunning()) {
		 * server.getServerSocket().close(); break; }
		 *
		 * } } */
	}

}
