package distributed.core.entities;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.security.Security;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import distributed.core.beans.Block;
import distributed.core.beans.Message;
import distributed.core.beans.MsgBlock;
import distributed.core.beans.MsgInitialize;
import distributed.core.beans.MsgTrans;
import distributed.core.threads.ClientThread;
import distributed.core.threads.ServerThread;
import distributed.core.utilities.Constants;

/* Class that represents a miner. */
public class NodeMiner {

	private static final Logger LOG = LoggerFactory.getLogger(NodeMiner.class.getName());

	private String id;
	private String address;
	private int port;
	private Wallet wallet;
	// TODO (optional) add collection map<publicKey, nodeId> gia to ui
	private int numOfNodes;
	public AtomicBoolean alone;
	//private ConcurrentHashMap<PublicKey, Pair<String, Integer>> nodes;
	private ConcurrentHashMap<String, Triple<PublicKey, String, Integer>> nodesId;
	private ConcurrentHashMap<String, Integer> blockchainSizes;
	private Blockchain blockchain;
	// private HashMap<String, TransactionOutput> allUTXOs = new HashMap<String,
	// TransactionOutput>();
	private Block currentBlock;
	private ServerThread server;
	public static String lock;
	public static String lockBlockchain;
	private static NodeMiner instance;

	private NodeMiner() {
		this.wallet = new Wallet();
		this.currentBlock = new Block();
		alone = new AtomicBoolean(true);
		//this.nodes = new ConcurrentHashMap<PublicKey, Pair<String, Integer>>();
		this.nodesId = new ConcurrentHashMap<String, Triple<PublicKey, String, Integer>>();
		this.blockchainSizes = new ConcurrentHashMap<String, Integer>();
		try {
			this.address = Inet4Address.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			LOG.warn("Cannot find host address");
		}
		//nodes.put(wallet.getPublicKey(), Pair.of(address, port));
		lock = "locked";
		lockBlockchain = "blocked";
		LOG.info("Starting miner in address={}:{}", address, port);
	}

	public static NodeMiner getInstance() {
		return instance;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public ServerThread getServer() {
		return server;
	}

	public void setServer(ServerThread server) {
		this.server = server;
	}

	public void setSizeOfNodeChain() {
		blockchainSizes.put(this.id, this.blockchain.getSize());

	}

	public void setSizeOfNodeChain(String id, int num) {
		blockchainSizes.put(id, num);
	}

	public void deleteAllSizes() {
		blockchainSizes.clear();
	}

	public int getChainSizeOther() {
		return this.blockchainSizes.size();
	}

	public String getNodeLargestChain() {
		int max = 0;
		String argmax = null;
		for (Entry<String, Integer> entry : blockchainSizes.entrySet()) {
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

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void createAndSend(String id, float amount) {
		Triple<PublicKey, String, Integer> aux = nodesId.get(id);
		sendTrans(sendFunds(aux.getLeft(), amount));
	}

	public void sendTrans(Transaction t) {
		Transaction deepCopy = SerializationUtils.clone(t); // deep Copy the trans so it won't get modified while being broadcasting
		MsgTrans msgTrans = new MsgTrans(deepCopy);
		synchronized (lock) {
			this.getCurrentBlock().addTransaction(t, this.getBlockchain()); // validation happens here
			// TODO handle the case that trans is not valid
			this.broadcastMsg(msgTrans); //first broadcast then validated it
			//if it isn't valid?
			if (this.getCurrentBlock().proceedWithMine()) {
				this.mineBlock();
			}
		}
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

	public ConcurrentHashMap<String, Triple<PublicKey, String, Integer>> getHashMap() {
		return nodesId;
	}

	public void setNodes(ConcurrentHashMap<String, Triple<PublicKey, String, Integer>> _nodes) {
		nodesId = _nodes;
	}

	public int numOfNodesInserted() {
		return nodesId.size();
	}

	public void addNode(Triple<PublicKey, String, Integer> value) {
		nodesId.put("id" + nodesId.size(), value);
	}

	/* public Pair<String, Integer> getNode(PublicKey key) { return nodes.get(key);
	 * } */

	public Triple<PublicKey, String, Integer> getNode(String id) {
		return nodesId.get(id);
	}

	/* public void showNodes() { for (Entry<PublicKey, Pair<String, Integer>> entry
	 * : nodes.entrySet()) { LOG.info("Node with public key={} in address={}",
	 * entry.getKey(), entry.getValue().getLeft() + ":" +
	 * entry.getValue().getRight()); } } */

	/* todo : utility to mine a new Block */
	public void mineBlock() {
		LOG.info("Starting mine block");

		// Block currentBlock = new Block(null); // todo proper call to constructor
		currentBlock.setPreviousHash(blockchain.getLastHash());
		currentBlock.setMerkleRoot();
		String target = new String(new char[Constants.DIFFICULTY]).replace('\0', '0'); // Create a string with
																						// difficulty * "0"
		LOG.info("Check that hash of block starts with {} zeros", target);
		while (!currentBlock.getCurrentHash().substring(0, Constants.DIFFICULTY).equals(target) && alone.get()) {
			currentBlock.incNonce();
			currentBlock.setCurrentHash(currentBlock.calculateHash());
		}
		LOG.info("Block Mined with hash value={}", currentBlock.getCurrentHash());
		// add to block chain set new block as the current
		synchronized (lockBlockchain) {
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
			setId("id0");
			Blockchain.test = "fouli";
			nodesId.put(id, Triple.of(wallet.getPublicKey(), address, port));
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

		for (Entry<String, Triple<PublicKey, String, Integer>> entry : nodesId.entrySet()) {
			if (entry.getValue().getLeft().equals(this.getPublicKey())) {
				// Do not send it back to myself
				continue;
			}
			(new ClientThread(entry.getValue().getMiddle(), entry.getValue().getRight(), msg)).start();
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

		NodeMiner node = initializeBackEnd(args);

		try {
			Thread.sleep(13000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		LOG.info(Blockchain.test);
		node.getBlockchain().printBlockChain();
		LOG.info("Size of blockchain={}", node.getBlockchain().getSize());
		//node.getServer().getServerSocket().close();
		//LOG.info("Num of threads still running is {}", Thread.activeCount());

		InputStream is = null;
		BufferedReader br = null;

		ServerThread server = node.getServer();
		while (server.isRunning()) { // παρακαλουθούμε την είσοδο που δίνει ο χρήστης
			// TODO check how we will get the input from file
			try {

				is = System.in;
				br = new BufferedReader(new InputStreamReader(is));

				String line = null;

				while ((line = br.readLine()) != null) {
					LOG.debug("Line read was {}", line);

					if (line.equalsIgnoreCase("exit")) {
						server.setRunning(false);
						break;
					} else if (line.equalsIgnoreCase("read")) {
						//String filename = node.getClass().getResource("transactions" + node.getId().substring(2) + ".txt").getPath();
						String path = "C:\\Users\\nikmand\\OneDrive\\DSML\\Κατανεμημένα\\blockchain\\src\\main\\resources\\";
						String filename = path + "transactions" + node.getId().substring(2) + ".txt";
						LOG.info(filename);
						/*try (BufferedReader bur = new BufferedReader(new FileReader(filename))) {
							String newLine;
							while ((newLine = bur.readLine()) != null) {
								LOG.info(newLine);
							}
						}*/
						long startTime = System.nanoTime();

						try (Stream<String> stream = Files.lines(Paths.get(filename))) {
							stream.forEach(ln -> {
								String arr[] = ln.split(" ");
								String id = arr[0];
								float amount = Float.parseFloat(arr[1]);
								//LOG.info("Trasanction received send {} noobcoins to {}", );
								node.createAndSend(id, amount);
							});
						}
						LOG.info("Size of blockchain is {}", node.getBlockchain().getSize());
						// TODO calculate throughput for trans and blocks
						long endTime = System.nanoTime();
						long duration = (endTime - startTime);
					}
				}
			} catch (IOException ioe) {

			} finally {
				// close the streams using close method
				try {
					if (br != null) {
						br.close();
					}
				} catch (IOException ioe) {
					System.out.println("Error while closing stream: " + ioe);
				}
				if (!server.isRunning()) {
					server.getServerSocket().close();
					break;
				}

			}

		}
	}

	public static NodeMiner initializeBackEnd(String args[]) throws IOException {
		LOG.info("START initializing backend");

		Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
		// String myAddress = Inet4Address.getLocalHost().getHostAddress(); // args[0];
		// δε χρειάζεται δημιουργείται εξ ορισμού στην ip της εφαρμογής
		// το πορτ θα μπορούσε να τίθεται αυτόματα διαλέγοντας κάποια πόρτα
		if (args.length < 1) {
			LOG.warn("A port number must be provided in order for the node to start. Exiting...");
			return null;
		}
		int myPort = Integer.parseInt(args[0]);
		int numOfNodes = 5;
		if (args.length < 2) {
			LOG.warn("Number of nodes wasn't specified, procedding with defaults which is {}", numOfNodes);
		} else {
			numOfNodes = Integer.parseInt(args[1]);
		}

		NodeMiner.instance = new NodeMiner();
		NodeMiner node = NodeMiner.getInstance();
		node.setPort(myPort);
		node.setNumOfNodes(numOfNodes);
		node.setBlockchain(Blockchain.getInstance());
		// Define new server
		node.setServer(new ServerThread(myPort, node));

		LOG.info("About to start server...");
		node.getServer().start(); // εκκινούμε το thread του server όπου μας έρχονται μηνύματα

		// connectToBootstrap(myAddress, myPort);
		node.initiliazeNetoworkConnections();

		InputStream is = null;
		BufferedReader br = null;

		//server.getServerSocket().close();
		/*		try {
					server.join();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}*/
		return node;

	}

}
