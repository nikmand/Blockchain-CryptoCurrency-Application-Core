package distributed.core.entities;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.GsonBuilder;

import distributed.core.beans.Block;

/**
 * Blockchain will be part of a node-miner. It should be able to be sent to a
 * new miner joining the network
 */
public class Blockchain implements Serializable {
	// it's singleton

	private static Logger LOG = LoggerFactory.getLogger(Blockchain.class.getName());

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
		/*		String blockchainJson = new GsonBuilder().setPrettyPrinting().create().toJson(blockchain);
				LOG.info(blockchainJson);*/
		String aux = "Blockchain: [\n";
		for (Block b : blockchain) {
			aux += b.toString();
			aux += "\n";
		}
		aux += "\n]";
		LOG.info(aux);
	}

	public Pair<ArrayList<Block>, Integer> findDiff(List<String> otherHashes) {
		ArrayList<Block> blocks = null;
		int i = 0, j;
		boolean flag = false;
		for (j = 0; j < otherHashes.size(); j++) {
			String otherHash = otherHashes.get(j);
			if (i == blockchain.size()) {
				LOG.warn("Comparison ended since otherhashes is longer!!");
				return null;
			}

			if (blockchain.get(i).getCurrentHash().equals(otherHash)) {
				i++;
				continue;
			} else {
				flag = true;
				break;
			}
		}
		if (flag || blockchain.size() > otherHashes.size()) { // αν είναι ίδια όλα πιθανώς λείπουν κάποια 
			blocks = new ArrayList<Block>(blockchain.subList(i, blockchain.size()));
		} else {
			LOG.warn("No difference found");
		}
		return Pair.of(blocks, j);
	}

	public void handleBlocks(ArrayList<Block> otherChain, int index, NodeMiner miner) {
		LOG.debug("START handleBlocks");

		ArrayList<Block> a = new ArrayList<Block>(blockchain.subList(0, index));
		ArrayList<Block> b = new ArrayList<Block>(blockchain.subList(index, blockchain.size()));

		//Set<String> set = new HashSet<String>();
		blockchain = a;
		b.forEach(block -> block.getTransactions().forEach(t -> NodeMiner.set.add(t.getTransactionId())));
		// TODO (critical) όσες εγγραφές του hashset επιβιώσουν θα πρέπει να μπουν στο επόμενο block μαζί με όσες
		// είναι εκείνη τη στιγμή στο current block. Όσες δε χωράνε θα πρέπει να περιμένουν, ή να δημιουργηθεί ένα μεγαλύτερο μπλοκ
		// ή να γίνουν revertana

		for (Block block : otherChain) {
			boolean wasValid = miner.validateReceivedBlock(block, getLastHash(), null);
			if (wasValid) {
				miner.alone.compareAndSet(true, false);
				miner.getBlockchain().addToChain(block);
				LOG.info("Block that was received with consensus added to chain. Block was {}", block);
			} else {
				LOG.warn("Invalid block detected during handleBlocks. Aborting Consensus procedure..., block was {}",
						block); // ίσως να μη μας έχει έρθει το txn που περιέχεται στο μεγαλύτερο block
				break; // δεν έχει νόημα να συνεχίσουμε καθώς σίγουρα το επόμενο θα αποριφθεί λόγω previousHash
			}
		}
	}

	/**
	 * Method checking if the list of blocks contained in this object is creates
	 * a valid blockchain
	 *
	 * @return True, if the blockchain is valid, else false
	 */
	public boolean validateChain() { // we use another method when we receive just some blocks
		LOG.info("START validateChain");

		Block currentBlock;
		Block previousBlock = blockchain.get(0);

		// loop through blocks to check hashes:
		for (int i = 1; i < blockchain.size(); i++) { // skip the first block
			currentBlock = blockchain.get(i);
			if (!currentBlock.validateBlock(previousBlock.getCurrentHash())) {
				return false;
			}
			previousBlock = currentBlock;
		}
		return true;
	}
}
