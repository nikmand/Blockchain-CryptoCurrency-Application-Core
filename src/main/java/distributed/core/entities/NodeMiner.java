package distributed.core.entities;

import java.io.IOException;
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
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import distributed.core.beans.Block;
import distributed.core.beans.Message;
import distributed.core.beans.MsgBlock;
import distributed.core.beans.MsgInitialize;
import distributed.core.beans.MsgRequestStatus;
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
	public int nodesInitialized = 1;
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

	// for metrics
	public static int transReceived = 0; // TODO ???????????????? ????????????????
	public static int transSent = 0;
	public static int transNotBroadcasted = 0;

	public static int blocksReceived = 0;
	public static int blocksSent = 0;
	public static long timeMining = 0;

	public static int chainSizeRequestReceived = 0;
	public static int chainSizeRequestSend = 0;
	public static int chainRequestReceived = 0;
	public static int chainRequestSend = 0;
	public static int consensusRoundsSucceed = 0;
	public static int consensusRoundsFailed = 0;

	private NodeMiner() {
		this.wallet = Wallet.getInstance();
		this.currentBlock = new Block();
		alone = new AtomicBoolean(true);
		//this.nodes = new ConcurrentHashMap<PublicKey, Pair<String, Integer>>();
		this.nodesId = new ConcurrentHashMap<String, Triple<PublicKey, String, Integer>>();
		nodesPid = new HashMap<PublicKey, String>();
		set = new HashSet<String>();
		this.blockchainSizes = new ConcurrentHashMap<String, Integer>();
		LOG.debug("Try to get my ip address");
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
		Transaction deepCopy = SerializationUtils.clone(t); // deep Copy the trans so it won't get modified while being broadcasting
		MsgTrans msgTrans = new MsgTrans(deepCopy);			// we added this cause sending an already validated txns resulted in adding two times 
		synchronized (lock) {								// output transaction, but didn't create any functional issue TODO (minor)   
			boolean isTxnValid = this.getCurrentBlock().addTransaction(t, this.getBlockchain()); // validation happens here
			if (isTxnValid) {
				this.broadcastMsg(msgTrans);
				LOG.info("Trans that was sent was {}", t);
				transSent++;
				this.mineBlock();
			} else {
				LOG.warn("Transactions wasn't valid. Discarding it...");
				transNotBroadcasted++;
			}
		}
	}

	public Transaction sendFunds(PublicKey _recipient, float value) {
		return this.wallet.sendFunds(_recipient, value, blockchain.getUTXOs());
	}

	public boolean validateReceivedBlock(Block received, String curHashOfPreivousBlock, Set<String> set) {

		//InvalidHashException exception = null;
		try {
			if (!received.validateBlock(curHashOfPreivousBlock)) {
				return false;
			}
		} catch (InvalidHashException e) { // catch exception
			int indexOfPrevBlock = blockchain.getLastBlock().getIndex();
			if (received.getIndex() <= indexOfPrevBlock) { // not starting consensus if block is old
				LOG.warn(
						"Block received is on the same or previous chain level with the last of us, not starting consensus");
				return false;
			}
			throw e;
		}

		LOG.debug("Continue validation of received block");

		synchronized (lockTxn) {

			Set<String> aux = currentBlock.getTransactions().stream().map(Transaction::getTransactionId)
					.collect(Collectors.toSet());

			//ArrayList<Transaction> rollBack = new ArrayList<Transaction>();

			if (set != null) {
				aux.addAll(set);
			}

			for (Transaction t : received.getTransactions()) {
				if (t == null) {
					LOG.warn("Transaction was null, continue"); // it doesn't cause trouble
					continue;
				}
				String id = t.getTransactionId();
				if (aux.contains(id)) { // ???????????????????? ???? hash ???????????????? ?????? ?????????????????? ?????? ?????? ???????? ?????????????????? ?????????? ???? ???????????? ???? ????????????
					LOG.debug("Txn is present at current block");  // ???????? hash ?????? ???????? ????????????????
					currentBlock.removeTxn(id);
					aux.remove(id); // TODO remove from the initial set also
					//rollBack.add(t);
					continue; // meaning it has been validated
				}
				/*				if (NodeMiner.set.contains(id)) {
									LOG.info("Txn is present at set collection!");
									NodeMiner.set.remove(id);
									continue;
								}*/
				// TODO (perfection) check if inputs of TXN are used in another TXN of the current block and accepted it, reject the current
				// by this way we prevent double spending
				// TODO (minor) txns in block are already validate so we will put again txnOutputs but any other modification (eg of globl UTXOS is needed!)
				if (!t.validateTransaction(getUTXOs())) { // validate txn
					// TODO (ignore) blacklist the node that sent it
					return false;
				}
			}

			/*			if (exception != null) { // throw it only if other checks succeeded, so we don't send requests for invalid blocks
							rollBack.forEach(transaction -> currentBlock.malAdd(transaction)); // ???? current block ???? ???????????? ???? ?????????? mine ?????? ???????????? ?????????? ???????????? ???? ???????????????? ?????? ???? blockchain
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
			// ?? proceed with mine ???? ?????????????????? ?????? ????????
			if (currentBlock.proceedWithMine() == false
					&& (blockchain.getSize() >= Constants.NUM_OF_NODES || currentBlock.getTransactions().size() == 0)) {
				return;
			}
			currentBlock.setPreviousHash(blockchain.getLastHash());
			currentBlock.setIndex(blockchain.getSize()); // ?????? ???? ???????????? ???????? ???? block ???????? ???? ?????????? mine ???????? ???? ???????????????????? 
			currentBlock.setMerkleRoot();
			alone.compareAndSet(false, true);
		}
		// ???? ???????? ???? ???????????????? ???? ???????????????????? trans ???? ?????????? ?????? false ?? ?????????????????? ?????? ???? block ???? ????????????????????

		LOG.info("Check that hash of block starts with {} zeros", Constants.DIFFICULTY);
		long startTime = System.currentTimeMillis();

		while (!currentBlock.getCurrentHash().substring(0, Constants.DIFFICULTY).equals(Constants.TARGET)
				&& alone.get()) {
			currentBlock.incNonce();
			currentBlock.setCurrentHash(currentBlock.calculateHash());
		}

		long stopTime = System.currentTimeMillis();
		long duration = (stopTime - startTime);

		synchronized (lockBlockchain) {
			if (alone.get()) {
				LOG.info("Block Mined with hash value={}", currentBlock.getCurrentHash());
				timeMining += duration;
				blocksSent++;
				// add to block chain set new block as the current
				//if (currentBlock.validateBlock(blockchain.getLastHash())) { // ???? ?????????????? validate ???? ???? ???? ?????????????????? alone ?????????????????? ???? valid
				LOG.info("Block added to chain");						// ???? ???? ???????????? ?????? ???????????? ???? previousHash ?????????? ???? ???????????? ?????? ???????? ???? ????????????
				blockchain.addToChain(currentBlock);					// ???????????? ???? ???????? ?????????????? ???????? ?????????????? chain ?????? consensus ?????????????? ???? ???? ????o
				MsgBlock msgBlock = new MsgBlock(currentBlock);			// ?????? ?????????????? ???? validation ?????????? ???? ???????????? ???????? ????????
				broadcastMsg(msgBlock);									// ???????????? ???????????? ???? ?????????????? ?????? alone false ?????? ???????? chain.
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

	public void readTrans() throws IOException, InterruptedException {
		LOG.debug("Start read from file");

		Constants.FILEPATH += Constants.NUM_OF_NODES + "nodes/" + "transactions" + getId().substring(2) + ".txt";
		long startTime = System.currentTimeMillis();

		try (Stream<String> stream = Files.lines(Paths.get(Constants.FILEPATH))) {
			stream.forEach(ln -> {
				String arr[] = ln.split(" ");
				String id = arr[0];
				float amount = Float.parseFloat(arr[1]);
				//LOG.info("Trasanction received send {} noobcoins to {}", );
				this.createAndSend(id, amount); // use only this function as it handles concurency issues
			});
		}

		long endTime = System.currentTimeMillis();
		LOG.info("Size of blockchain is {}", this.getBlockchain().getSize());
		long durationSec = (endTime - startTime) / 1000;

		Thread.sleep(3000); // 5 sec

		logMetrics(durationSec);

		Thread.sleep(30000); // 15 sec

		logMetrics(durationSec);

		Thread.sleep(5 * 60 * 1000); // 30 sec

		logMetrics(durationSec);

		server.getServerSocket().close();
		server.join();

	}

	public void logMetrics(long durationSec) {

		//int totalNumOfTxns = (blockchain.getSize() - Constants.NUM_OF_NODES) * Constants.CAPACITY;
		int totalNumOfTxns = 0;
		for (Block b : blockchain.getBlockchain()) {
			int aux = b.getTransactions().size();
			totalNumOfTxns += aux;
		}
		LOG.info("\n============================ Results - Node {} ============================", getId());

		// metrics for TXNs
		LOG.info("Num of transactions performed was {}", totalNumOfTxns);
		LOG.info("In a total time of {} seconds", durationSec);
		LOG.info("Throughput of our system was {} TXNs/second", (double) totalNumOfTxns / durationSec);
		LOG.info("Trans received={}", transReceived);
		LOG.info("Trans sent={}", transSent);
		LOG.info("Invalid Trans that weren't broadcasted={}", transNotBroadcasted);
		LOG.info("Size of set, which contains leftover TXNs, is {}", set.size());

		// metrics for Blocks
		LOG.info("Blocks received and accepted={}", blocksReceived);
		LOG.info("Blocks mined={}", blocksSent);
		LOG.info("Mean mining time was {} seconds", (double) timeMining / blocksSent / 1000);

		// metrics for Consensus
		LOG.info("Requests for blockchain sent: {}", chainRequestSend);
		LOG.info("Consensus rounds succeeded: {}", consensusRoundsSucceed);
		LOG.info("Consensus rounds failed: {}", consensusRoundsFailed);

		LOG.info("My balance was: {}", this.getBalance());
	}

	public static NodeMiner initializeBackEnd(String args[]) throws IOException {
		LOG.info("START initializing backend");

		Security.addProvider(new BouncyCastleProvider());
		// String myAddress = Inet4Address.getLocalHost().getHostAddress(); // args[0];
		// ???? ???????????????????? ?????????????????????????? ???? ?????????????? ???????? ip ?????? ??????????????????
		// ???? ???????? ???? ???????????????? ???? ?????????????? ???????????????? ?????????????????????? ???????????? ??????????
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
		if (args.length == 5) {
			Constants.CAPACITY = Integer.parseInt(args[2]);
			Constants.DIFFICULTY = Integer.parseInt(args[3]);
			Constants.FILEPATH = args[4];
		}

		NodeMiner.instance = new NodeMiner(); // ??
		NodeMiner node = NodeMiner.getInstance();
		node.setPort(myPort);
		node.setBlockchain(Blockchain.getInstance());
		// Define new server
		node.setServer(new ServerThread(myPort, node));

		LOG.info("About to start server...");
		node.getServer().start(); // ?????????????????? ???? thread ?????? server ???????? ?????? ???????????????? ????????????????

		node.initiliazeNetoworkConnections();

		//server.getServerSocket().close();
		/*		try {
					server.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}*/
		return node;

	}

	public static void main(String[] args) throws Exception {

		NodeMiner node = initializeBackEnd(args);

		if (node == null) {
			return;
		}

		int sizeSoFar;
		synchronized (lockBlockchain) {
			sizeSoFar = node.getBlockchain().getSize();
		}

		while (sizeSoFar < Constants.NUM_OF_NODES) {
			Thread.sleep(5000);
			synchronized (lockBlockchain) {
				sizeSoFar = node.getBlockchain().getSize();
			}
			LOG.info("Size of blockchain={}", sizeSoFar);
		}
		if (sizeSoFar > Constants.NUM_OF_NODES) {
			LOG.warn("Size is bigger than expected. Aborting...");
			return;
		}
		node.getBlockchain().printBlockChain();
		LOG.info(
				"\n\n\n========================================= Initialization period ended =========================================\n\n\n");

		if (node.getId().equals("id0")) { // check if others completed initialization phase
			Thread.sleep(500);
			node.broadcastMsg(new MsgRequestStatus());
		}

		//node.getServer().getServerSocket().close();
		//LOG.info("Num of threads still running is {}", Thread.activeCount());
	}
}
