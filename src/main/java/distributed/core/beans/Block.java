package distributed.core.beans;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.GsonBuilder;

import distributed.core.entities.Blockchain;
import distributed.core.entities.Transaction;
import distributed.core.utilities.Constants;
import distributed.core.utilities.StringUtilities;

/**
 * Block class represents the basic part of the blockchain
 *
 * Implements the Serializable inteface in order to be sent above network when a
 * new miner joins the blockchain network
 */
public class Block implements Serializable {

	private static final Logger LOG = LoggerFactory.getLogger(Block.class.getName());

	private int index; // just an incremental number
	private String previousHash;
	private long timestamp;
	private String currentHash = "111111111111111111111";
	private int nonce;
	private List<Transaction> transactions = new ArrayList<Transaction>();

	public Block() {
		this.timestamp = new Date().getTime();
	}

	public Block(String previousHash) {
		this.previousHash = previousHash;
		this.timestamp = new Date().getTime();
		// this.currentHash = calculateHash(); // δεν έχει νόημα να υπολογίζεται τώρα
		// προτού προσθεθούν trans
	}

	public List<Transaction> getTransactions() {
		return transactions;
	}

	public String getPreviousHash() {
		return previousHash;
	}

	public void setPreviousHash(String previousHash) {
		this.previousHash = previousHash;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public String getCurrentHash() {
		return currentHash;
	}

	public void setCurrentHash(String currentHash) {
		this.currentHash = currentHash;
	}

	public int getNonce() {
		return nonce;
	}

	public void setNonce(int nonce) {
		this.nonce = nonce;
	}

	public boolean proceedWithMine() {
		if (transactions.size() == Constants.CAPACITY) {
			return true;
		} else if (transactions.size() < Constants.CAPACITY) {
			return false;
		} else {
			LOG.warn("Block is oversized!!!");
			return false;
		}

	}

	/* todo: Function that calculates the hash on the current block */
	public String calculateHash() {
		String calculatedhash = StringUtilities
				.applySha256(previousHash + Long.toString(timestamp) + Integer.toString(nonce));
		// add and content to obtain proper hash... + data
		// anti na parei to hash olwn twn trans pou apoteloun to block pairnei kati pou
		// legetai merkle tree
		return calculatedhash;
	}

	/* todo: Function that adds a Transaction on the current block if it is valid */
	public boolean addTransaction(Transaction transaction, Blockchain blockchain) {
		LOG.info("Start addTransaction");

		if (transaction == null) {
			LOG.info("Transaction was null, not added");
			return false;
		} else if (proceedWithMine()) {
			LOG.warn("Block is full it should have alreary been mined!! Aborting...");
			return false;
		}
		if ((previousHash != "1")) { // if genesis block ignore
			if ((transaction.processTransaction(blockchain) != true)) {
				LOG.info("Transaction failed to process. Discarded.");
				return false;
			}
		}
		transactions.add(transaction);
		LOG.info("Transaction added to block");
		// TODO check if block is full and if yes mine it, now we do this manuallly
		return true;
	}

	public boolean validateBlock(String previousHash) {
		// compare registered hash and calculated hash:
		if (!this.getCurrentHash().equals(this.calculateHash())) {
			LOG.info("Current Hashes not equal");
			return false;
		}
		// compare previous hash and registered previous hash
		if (!previousHash.equals(this.getPreviousHash())) {
			// TODO call resolveConflict()
			LOG.info("Previous Hashes not equal");
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return new GsonBuilder().setPrettyPrinting().create().toJson(this);
	}

}
