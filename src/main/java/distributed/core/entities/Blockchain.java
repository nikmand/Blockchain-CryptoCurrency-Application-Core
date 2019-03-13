package distributed.core.entities;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;

import com.google.gson.GsonBuilder;

import distributed.core.beans.Block;

/**
 * Blockchain will be part a node-miner. It should be able to be sent to a new
 * miner joining the network
 */
public class Blockchain implements Serializable {
	// TODO make it singleton

	private static Logger LOG = Logger.getLogger(Blockchain.class.getName());

	private List<Block> blockchain = new ArrayList<Block>();
	private int difficulty;

	private HashMap<String, TransactionOutput> UTXOs = new HashMap<String, TransactionOutput>();
	private static Blockchain instance = new Blockchain();
	public static String test;
	// every node keeps unspend transactions

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
			LOG.warn("Blockchain");
			return null;
		}
	}

	public List<Transaction> getTransLastBlock() {
		Block last = getLastBlock();
		if (last != null) {
			return last.getTransactions();
		} else {
			return null;
		}
	}

	public String getLastHash() {
		Block last = getLastBlock();
		if (last != null) {
			return last.getCurrentHash();
		} else {
			return null;
		}
	}

	public void addToChain(Block b) {
		blockchain.add(b);
	}

	public List<Block> getBlockchain() {
		return blockchain;
	}

	public void setBlockchain(List<Block> blockchain) {
		this.blockchain = blockchain;
	}

	public HashMap<String, TransactionOutput> getUTXOs() {
		return UTXOs;
	}

	public void setUTXOs(HashMap<String, TransactionOutput> uTXOs) {
		UTXOs = uTXOs;
	}

	public void printBlockChain() {
		String blockchainJson = new GsonBuilder().setPrettyPrinting().create().toJson(blockchain);
		LOG.info("Our block chain so far:");
		LOG.info(blockchainJson);
	}

	/**
	 * Method checking if the list of blocks contained in this object is creates
	 * a
	 * valid blockchain
	 *
	 * @return True, if the blockchain is valid, else false
	 */
	public boolean isBlockchainValid() { //TODO rename to validateChain
		LOG.info("START isBlockchainValid");

		Block currentBlock;
		Block previousBlock;

		// loop through blockchain to check hashes:
		for (int i = 1; i < blockchain.size(); i++) {
			currentBlock = blockchain.get(i);
			previousBlock = blockchain.get(i - 1);
			if (!currentBlock.validateBlock(previousBlock.getCurrentHash())) {
				return false;
			}
		}
		return true;
	}

}
