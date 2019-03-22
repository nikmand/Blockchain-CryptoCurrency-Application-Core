package distributed.core.entities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
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
import distributed.core.exceptions.InvalidHashException;
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
	//private int numOfNodes;
	public AtomicBoolean alone;
	public static Set<String> set; // a collection of validates TXNs that were put aside
	public static HashMap<PublicKey, String> nodesPid;
	private ConcurrentHashMap<String, Triple<PublicKey, String, Integer>> nodesId;
	private ConcurrentHashMap<String, Integer> blockchainSizes;
	private Blockchain blockchain;
	private Block currentBlock;
	private ServerThread server;
	public static String lock = "locked";
	public static String lockBlockchain = "blocked";
	public static String lockTxn = "mining";
	private static NodeMiner instance;
	public static int transReceived = 0; // TODO προσθήκη μετρικών
	public static int transSent = 0;
	public static int blocksReceived = 0;
	public static int blocksSent = 0;
	public static int chainSizeRequestReceived = 0;
	public static int chainSizeRequestSend = 0;
	public static int chainRequestReceived = 0;
	public static int chainRequestSend = 0;

	private NodeMiner() {
		this.wallet = Wallet.getInstance();
		this.currentBlock = new Block();
		alone = new AtomicBoolean(true);
		//this.nodes = new ConcurrentHashMap<PublicKey, Pair<String, Integer>>();
		this.nodesId = new ConcurrentHashMap<String, Triple<PublicKey, String, Integer>>();
		this.nodesPid = new HashMap<PublicKey, String>();
		set = new HashSet<String>();
		this.blockchainSizes = new ConcurrentHashMap<String, Integer>();
		try {
			this.address = Inet4Address.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			LOG.warn("Cannot find host address");
		}
		//nodes.put(wallet.getPublicKey(), Pair.of(address, port));
		LOG.info("Starting miner in address={}:{}", address, port);
	}

	public static NodeMiner getInstance() {
		return instance;
	}

	public String getIdFromKey(PublicKey key) {
		return nodesPid.get(key);
	}

	public HashMap<PublicKey, String> getNodesPid() {
		return nodesPid;
	}

	public void setNodesPid(HashMap<PublicKey, String> nodesPid) {
		this.nodesPid = nodesPid;
	}

	public ConcurrentHashMap<String, TransactionOutput> getUTXOs() {
		return blockchain.getUTXOs();

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

	public void createAndSend(String _id, float amount) {
		if (_id.equals(id)) {
			LOG.warn("I do not send money to myself!");
			return;
		}
		Triple<PublicKey, String, Integer> aux = nodesId.get(_id);
		sendTxnMine(sendFunds(aux.getLeft(), amount));
	}

	public void sendTxnMine(Transaction t) {
		Transaction deepCopy = SerializationUtils.clone(t); // deep Copy the trans so it won't get modified while being broadcasting, why ? can't remember
		MsgTrans msgTrans = new MsgTrans(deepCopy);
		synchronized (lock) {
			boolean isTxnValid = this.getCurrentBlock().addTransaction(t, this.getBlockchain()); // validation happens here
			if (isTxnValid) {
				this.broadcastMsg(msgTrans);
				NodeMiner.transSent++;
				this.mineBlock();
			} else {
				LOG.warn("Transactions wasn't valid. Discarding it...");
			}
		}
	}

	public Transaction sendFunds(PublicKey _recipient, float value) {
		return this.wallet.sendFunds(_recipient, value, blockchain.getUTXOs());
	}

	public boolean validateReceivedBlock(Block received, String hashOfPreivousBlock, Set<String> set) {

		InvalidHashException exception = null;
		try {
			if (!received.validateBlock(hashOfPreivousBlock)) {
				return false;
			}
		} catch (InvalidHashException e) { // catch exception
			//exception = e; // TODO (optional) avoid starting consensus for cases that are on the same chain level
			throw e;		 // we have to be extremely careful to rollback all the collections if the only problem is the hash
		}

		LOG.debug("Continue validation of received block");

		synchronized (lockTxn) {

			Set<String> aux = currentBlock.getTransactions().stream().map(Transaction::getTransactionId)
					.collect(Collectors.toSet());

			ArrayList<Transaction> rollBack = new ArrayList<Transaction>();

			if (set != null) {
				aux.addAll(set);
			}

			for (Transaction t : received.getTransactions()) {
				if (t == null) {
					LOG.warn("Transaction was null, continue"); // it doesn't cause trouble
					continue;
				}
				String id = t.getTransactionId();
				if (aux.contains(id)) { // ελέγχοντας το hash σημαίνει ότι πρόκειται για την ίδια συναλλαγή καθώς δε μπροεί να βρεθεί
					LOG.debug("Txn is present at current block");  // ίδιο hash από άλλα δεδομένα
					currentBlock.removeTxn(id);
					aux.remove(id); // TODO remove from the initial set also
					//rollBack.add(t);
					continue; // meaning it has been validated
				}
				if (NodeMiner.set.contains(id)) {
					LOG.info("Txn is present at set collection!");
					NodeMiner.set.remove(id);
					continue;
				}
				// TODO (perfection) check if inputs of TXN are used in another TXN of the current block and accepted it, reject the current
				// by this way we prevent double spending
				// TODO (minor) txns in block are already validate so we will put again txnOutputs but any other modification (eg of globl UTXOS is needed!)
				if (!t.validateTransaction(getUTXOs())) { // validate txn
					// TODO (ignore) blacklist the node that sent it
					return false;
				}
			}

			/*			if (exception != null) { // throw it only if other checks succeeded, so we don't send requests for invalid blocks
							rollBack.forEach(transaction -> currentBlock.malAdd(transaction)); // το current block δε μπορεί να γίνει mine στο μεταξύ γιατί έχουμε το κλείδωμα για το blockchain
							LOG.debug("Current block after malad is {}", currentBlock);
							throw exception;
						}*/
		}
		return true;
	}

	public Wallet getWallet() {
		return wallet;
	}

	public PublicKey getPublicKey() {
		return this.wallet.getPublicKey();
	}

	public PrivateKey getPrivateKey() {
		return this.wallet.getPrivateKey();
	}

	public Blockchain getBlockchain() {
		return blockchain;
	}

	public void setBlockchain(Blockchain blockchain) {
		this.blockchain = blockchain;
	}

	/*	public int getNumOfNodes() {
			return numOfNodes;
		}

		public void setNumOfNodes(int numOfNodes) {
			this.numOfNodes = numOfNodes;
		}*/

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
		String id = "id" + nodesId.size();
		nodesId.put(id, value);
		nodesPid.put(value.getLeft(), id);
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

	public void mineBlock() {
		LOG.debug("START mining block");

		ArrayList<Transaction> aux = null;
		synchronized (lockBlockchain) {
			// η proceed with mine να βρίσκεται εδώ μέσα
			if (currentBlock.proceedWithMine() == false
					&& (blockchain.getSize() >= Constants.NUM_OF_NODES || currentBlock.getTransactions().size() == 0)) {
				return;
			}
			currentBlock.setPreviousHash(blockchain.getLastHash());
			currentBlock.setIndex(blockchain.getSize()); // από τη στιγμή αυτή το block είτε θα γίνει mine είτε θα απορριφθεί 
			currentBlock.setMerkleRoot();
			alone.compareAndSet(false, true);
		}
		// σε αυτό το διάστημα αν αφαιρεθούν trans θα γίνει και false η μεταβλητή και το block θα απορριφθεί

		//String target = new String(new char[Constants.DIFFICULTY]).replace('\0', '0'); // Create a string with difficulty * "0"

		LOG.info("Check that hash of block starts with {} zeros", Constants.DIFFICULTY);
		while (!currentBlock.getCurrentHash().substring(0, Constants.DIFFICULTY).equals(Constants.TARGET)
				&& alone.get()) {
			currentBlock.incNonce();
			currentBlock.setCurrentHash(currentBlock.calculateHash());
		}
		synchronized (lockBlockchain) {
			if (alone.get()) {
				LOG.info("Block Mined with hash value={}", currentBlock.getCurrentHash());
				// add to block chain set new block as the current
				//if (currentBlock.validateBlock(blockchain.getLastHash())) { // το κάνουμε validate γτ με τη μεταβλητή alone παραγουμε μη valid
				LOG.info("Block added to chain");						// απ τη στιγμή που τέθηκε το previousHash μεχρι τι στιγμή που πάμε να μπούμε
				blockchain.addToChain(currentBlock);					// μπορεί να έχει αλλάξει λόγω έλευσης chain από consensus θέλουμε να το απo
				MsgBlock msgBlock = new MsgBlock(currentBlock);			// άρα δούλευε το validation μεχρι να βρούμε κάτι άλλο
				broadcastMsg(msgBlock);									// αλλιώς πρέπει να κάνουμε και alone false στη λήψη chain.
			} else {
				LOG.warn("Invalid block detected, not broadcasted, block was {}", currentBlock);
				aux = currentBlock.getTransactions();
			}

			currentBlock = new Block();
			if (aux != null) {
				currentBlock.setTransactions(aux); // include unseen TXNs from rejected block which are already validated 
			}
		}
		// if non of the txns was in the block that was received and accepted start mining right away
		mineBlock();

		//alone = new AtomicBoolean(true); // δεν έχει νόημα αφού θα τεθεί όταν το νέο block πάει να γίνει mine
	}

	/**
	 * Utility to initiliaze any network connections. Call upon start
	 */
	public void initiliazeNetoworkConnections() {
		LOG.debug("START initialization of network connections");

		if (address.equals(Constants.BOOTSTRAPADDRESS) && port == Constants.BOOTSTRAPPORT) {
			LOG.info("I am the bootstrap node");
			setId("id0");
			nodesId.put(id, Triple.of(wallet.getPublicKey(), address, port));
			nodesPid.put(wallet.getPublicKey(), id);
			Transaction genesisTrans = new Transaction(null, this.wallet.getPublicKey(), 100 * Constants.NUM_OF_NODES,
					null);
			genesisTrans.setTransactionId("0");
			// genesisTrans.generateSignature(wallet.getPrivateKey());
			TransactionOutput genesisOutputTrans = new TransactionOutput(this.wallet.getPublicKey(),
					100 * Constants.NUM_OF_NODES, "0");
			genesisTrans.addToOutputs(genesisOutputTrans);
			blockchain.getUTXOs().put(genesisOutputTrans.getId(), genesisOutputTrans);

			Block genesisBlock = new Block("1");
			genesisBlock.setNonce(0);
			genesisBlock.setIndex(0);
			genesisBlock.addTransaction(genesisTrans, blockchain);
			genesisBlock.calculateHash();
			blockchain.addToChain(genesisBlock);

			LOG.info("Wallet balance for node-0  is {}", getBalance());
			return;
		} else {
			MsgInitialize message = new MsgInitialize(this.wallet.getPublicKey(), this.address, this.port);
			(new ClientThread(Constants.BOOTSTRAPADDRESS, Constants.BOOTSTRAPPORT, message)).start(); // retry if connection fails ?
		}
	}

	public void broadcastMsg(Message msg) {
		LOG.debug("Start broadcasting message");

		for (Entry<String, Triple<PublicKey, String, Integer>> entry : nodesId.entrySet()) {
			if (entry.getValue().getLeft().equals(this.getPublicKey())) {
				continue;
			}
			(new ClientThread(entry.getValue().getMiddle(), entry.getValue().getRight(), msg)).start();
			/* try { //debug //gives catastrophic results! if (msg instanceof MsgTrans) {
			 * Thread.sleep(5000); } } catch (InterruptedException e) { e.printStackTrace(); } */
		}
	}

	public static void main(String[] args) throws Exception {

		NodeMiner node = initializeBackEnd(args);

		try {
			Thread.sleep(13000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		node.getBlockchain().printBlockChain();
		LOG.info("Size of blockchain={}", node.getBlockchain().getSize());
		//node.getServer().getServerSocket().close();
		//LOG.info("Num of threads still running is {}", Thread.activeCount());

		InputStream is = null;
		BufferedReader br = null;

		ServerThread server = node.getServer();
		while (server.isRunning()) { // παρακαλουθούμε την είσοδο που δίνει ο χρήστης
			try {

				is = System.in;
				br = new BufferedReader(new InputStreamReader(is));

				String line = null;

				while ((line = br.readLine()) != null) {
					LOG.debug("Line read was {}", line);

					if (line.equalsIgnoreCase("exit")) {
						server.setRunning(false);
						break;
					} else if (line.equalsIgnoreCase("r")) {
						//String filename = node.getClass().getResource("transactions" + node.getId().substring(2) + ".txt").getPath();
						String path = "C:\\Users\\nikmand\\OneDrive\\DSML\\Κατανεμημένα\\blockchain\\src\\main\\resources\\";
						String filename = path + "transactions" + node.getId().substring(2) + ".txt";
						LOG.info("Start reading from file={}", filename);
						/*try (BufferedReader bur = new BufferedReader(new FileReader(filename))) {
							String newLine;
							while ((newLine = bur.readLine()) != null) {
								LOG.info(newLine);
							}
						}*/
						long startTime = System.currentTimeMillis();

						try (Stream<String> stream = Files.lines(Paths.get(filename))) {
							stream.forEach(ln -> {
								String arr[] = ln.split(" ");
								String id = arr[0];
								float amount = Float.parseFloat(arr[1]);
								//LOG.info("Trasanction received send {} noobcoins to {}", );
								node.createAndSend(id, amount); // use only this function as it handles concurency issues
							});
						}
						LOG.info("Size of blockchain is {}", node.getBlockchain().getSize());
						// calculate throughput for trans
						//and mean mining time for blocks
						long endTime = System.currentTimeMillis();
						int totalNumOfTxns = (node.blockchain.getSize() - Constants.NUM_OF_NODES) * Constants.CAPACITY;
						long durationSec = (endTime - startTime) / 1000;
						LOG.info("Num of transactions performed was {}", totalNumOfTxns);
						LOG.info("In a total time of {} seconds", durationSec);
						LOG.info("Throughput of our system was {} TXNs/second", (double) totalNumOfTxns / durationSec);
						LOG.info("Trans received={}", NodeMiner.transReceived);
						LOG.info("Trans sent={}", NodeMiner.transSent);

						try {
							Thread.sleep(5000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}

						totalNumOfTxns = (node.blockchain.getSize() - Constants.NUM_OF_NODES) * Constants.CAPACITY;
						LOG.info("Num of transactions performed was {}", totalNumOfTxns);
						LOG.info("In a total time of {} seconds", durationSec);
						LOG.info("Throughput of our system was {} TXNs/second", (double) totalNumOfTxns / durationSec);
						LOG.info("Trans received={}", NodeMiner.transReceived);
						LOG.info("Trans sent={}", NodeMiner.transSent);
						LOG.info("Size of set, which contains leftover TXNs, is {}", set.size());

						try {
							Thread.sleep(25000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}

						totalNumOfTxns = (node.blockchain.getSize() - Constants.NUM_OF_NODES) * Constants.CAPACITY;
						LOG.info("Num of transactions performed was {}", totalNumOfTxns);
						LOG.info("In a total time of {} seconds", durationSec);
						LOG.info("Throughput of our system was {} TXNs/second", (double) totalNumOfTxns / durationSec);
						LOG.info("Trans received={}", NodeMiner.transReceived);
						LOG.info("Trans sent={}", NodeMiner.transSent);
						LOG.info("Size of set, which contains leftover TXNs, is {}", set.size());

						try {
							Thread.sleep(5 * 60 * 1000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}

						totalNumOfTxns = (node.blockchain.getSize() - Constants.NUM_OF_NODES) * Constants.CAPACITY;
						LOG.info("Num of transactions performed was {}", totalNumOfTxns);
						LOG.info("In a total time of {} seconds", durationSec);
						LOG.info("Throughput of our system was {} TXNs/second", (double) totalNumOfTxns / durationSec);
						LOG.info("Trans received={}", NodeMiner.transReceived);
						LOG.info("Trans sent={}", NodeMiner.transSent);
						LOG.info("Size of set, which contains leftover TXNs, is {}", set.size());
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
			LOG.info("Provide arguments: $port $numOfNodes $capacity $difficulty");
			LOG.warn("A port number must be provided in order for the node to start. Exiting...");
			return null;
		}
		int myPort = Integer.parseInt(args[0]);

		if (args.length < 2) {
			LOG.warn("Number of nodes wasn't specified, procedding with defaults which is {}", Constants.NUM_OF_NODES);
		} else {
			Constants.NUM_OF_NODES = Integer.parseInt(args[1]);
		}

		if (args.length == 4) {
			Constants.CAPACITY = Integer.parseInt(args[2]);
			Constants.DIFFICULTY = Integer.parseInt(args[3]);
		}

		/*		Constants.CAPACITY_ONE = (Constants.NUM_OF_NODES - 1) % Constants.CAPACITY != 0 ? (Constants.NUM_OF_NODES - 1)
						: Constants.CAPACITY;*/
		NodeMiner.instance = new NodeMiner(); // ??
		NodeMiner node = NodeMiner.getInstance();
		node.setPort(myPort);
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
					e.printStackTrace();
				}*/
		return node;

	}

}
