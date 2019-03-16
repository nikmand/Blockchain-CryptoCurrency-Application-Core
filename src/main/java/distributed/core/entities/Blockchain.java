package distributed.core.entities;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;

import com.google.gson.GsonBuilder;

import distributed.core.beans.Block;

/**
 * Blockchain will be part of a node-miner. It should be able to be sent to a
 * new miner joining the network
 */
public class Blockchain implements Serializable {
	// it's singleton

	private static Logger LOG = Logger.getLogger(Blockchain.class.getName());

	private ArrayList<Block> blockchain = new ArrayList<Block>();

	private ConcurrentHashMap<String, TransactionOutput> UTXOs = new ConcurrentHashMap<String, TransactionOutput>(); 	// every node keeps unspent TXNs
	private static Blockchain instance = new Blockchain();

	private Blockchain() {
	}

	public static Blockchain getInstance() {
		return instance;
	}

	public int getSize() {
		return blockchain.size();
	}

	public Block getLastBlock() {
		int aux = blockchain.size();
		if (aux > 0) {
			return blockchain.get(aux - 1); // indexing is O(1) for ArrayList
		} else {
			LOG.warn("Blockchain is empty");
			return null;
		}
	}

	public List<Transaction> getTransLastBlock() {
		Block last = getLastBlock();
		if (last != null) {
			return last.getTransactions();
		} else {
			LOG.warn("Blockchain is empty");
			return null;
		}
	}

	public String getLastHash() {
		Block last = getLastBlock();
		if (last != null) {
			return last.getCurrentHash();
		} else {
			LOG.warn("Blockchain is empty");
			return null;
		}
	}

	public void addToChain(Block b) {
		blockchain.add(b);
	}

	public List<Block> getBlockchain() {
		return blockchain;
	}

	public void setBlockchain(ArrayList<Block> blockchain) {
		this.blockchain = blockchain;
	}

	public ConcurrentHashMap<String, TransactionOutput> getUTXOs() {
		return UTXOs;
	}

	public void setUTXOs(ConcurrentHashMap<String, TransactionOutput> uTXOs) {
		UTXOs = uTXOs;
	}

	public void printBlockChain() {
		String blockchainJson = new GsonBuilder().setPrettyPrinting().create().toJson(blockchain);
		LOG.info(blockchainJson);
	}

	public void findDifference(ArrayList<Block> OtherChain, NodeMiner miner) {
		Iterator<Block> it_a = blockchain.iterator();
		Iterator<Block> it_b = blockchain.iterator();
		Block remaining_a = null;
		Block remaining_b = null;
		boolean flag = false;
		while (it_a.hasNext() && it_b.hasNext()) { // TODO η εύρεση της διαφοράς να συμβαίνει στη μεριά του αποστολέα
			remaining_a = it_a.next();
			remaining_b = it_a.next();
			if (remaining_a.equals(remaining_b)) {
				continue;
			}
			flag = true;
			break;
		}
		// τα txn από τα παλια πααραπανίσια μπλοκ να γίνουν hashset
		// ta txn από τα καινουρια μπλοκ να γίνουν validate όπως received block με το παραπάνω hashset
		if (flag) {
			Set<String> set = new HashSet<String>();
			remaining_a.getTransactions().forEach(t -> set.add(t.getTransactionId()));
			it_a.forEachRemaining(block -> block.getTransactions().forEach(t -> set.add(t.getTransactionId())));

			// TODO θεωρούμε ότι το chain που δεχτήκαμε έχει σε σωστή σειρά τα blocks, ίσως πρέπει να το ελέγχουσε προηγουμένως! με την απλή validate
			// TODO use our hashet !
			miner.validateReceivedBlock(remaining_b, remaining_b.getPreviousHash());
			it_b.forEachRemaining(block -> miner.validateReceivedBlock(block, block.getPreviousHash()));
		}
	}

	/**
	 * Method checking if the list of blocks contained in this object is creates
	 * a
	 * valid blockchain
	 *
	 * @return True, if the blockchain is valid, else false
	 */
	public boolean validateChain() {
		LOG.info("START validateChain");

		Block currentBlock;
		Block previousBlock = blockchain.get(0);

		// loop through blocks to check hashes:
		for (int i = 1; i < blockchain.size(); i++) { // skip the first block
			currentBlock = blockchain.get(i);
			if (!currentBlock.validateBlock(previousBlock.getCurrentHash())) { // TODO check if we need to use the validateReceivedBlock
				return false;
			}
			previousBlock = currentBlock;
		}
		return true;
	}
}
