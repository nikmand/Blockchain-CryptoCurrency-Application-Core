package distributed.core.beans;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.GsonBuilder;

import distributed.core.entities.Blockchain;
import distributed.core.entities.NodeMiner;
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
	private String merkleRoot;
	private ArrayList<Transaction> transactions = new ArrayList<Transaction>();

	public Block() {
		this.timestamp = new Date().getTime();
	}

	public Block(String previousHash) {
		this.previousHash = previousHash;
		this.timestamp = new Date().getTime();
		// this.currentHash = calculateHash(); // δεν έχει νόημα να υπολογίζεται τώρα
		// προτού προσθεθούν trans
	}

	public String getMerkleRoot() {
		return merkleRoot;
	}

	public void setMerkleRoot() {
		this.merkleRoot = StringUtilities.getMerkleRoot(transactions);
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
				.applySha256(previousHash + Long.toString(timestamp) + Integer.toString(nonce) + merkleRoot);
		// TODO add and content to obtain proper hash... + data
		// anti na parei to hash olwn twn trans pou apoteloun to block pairnei kati pou
		// legetai merkle tree
		return calculatedhash;
	}

	private String prepareContent() {
		String aux = previousHash + Long.toString(timestamp);
		for (Transaction t : transactions) {
			aux += t.getTransactionId();
		}
		return aux;
	}

	/**
	 * Addition of any txn in block without
	 * validation
	 *
	 * @param transaction
	 * @return
	 */
	public boolean malAdd(Transaction transaction) {
		LOG.info("START maladd");

		transactions.add(transaction);
		return true;
	}

	/* todo: Function that adds a Transaction on the current block if it is valid */
	public boolean addTransaction(Transaction transaction, Blockchain blockchain) {
		LOG.info("Start addTransaction");

		if (transaction == null) {
			LOG.info("Transaction was null, not added");
			return false;
		} else if (proceedWithMine()) { // we cannot add txn if it is already full
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
		LOG.info("START validate block with previousHash={}", previousHash);
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

	public boolean validateReceivedBlock(String hash, NodeMiner miner) {

		if (!validateBlock(hash)) {
			return false;
		}

		LOG.info("Contining validation of received block");

		HashSet<String> aux = new HashSet<String>(); // store id of txns of current block in a set for O(1) retrival
		Iterator<Transaction> it = miner.getCurrentBlock().getTransactions().iterator();
		while (it.hasNext()) { // TODO check if throws concurent modification exception
			aux.add(it.next().getTransactionId());
		}

		for (Transaction t : transactions) {
			if (t == null) {
				LOG.debug("Transaction was null, continue"); // it doesn't cause trouble
				continue;
			}
			if (aux.contains(t.getTransactionId())) { // ελέγχοντας το hash σημαίνει ότι πρόκειται για την ίδια συναλλαγή καθώς δε μπροεί να βρεθεί
				LOG.debug("Txn is present at current block");  // ίδιο hash από άλλα δεδομένα
				continue; // meaning it has been validated
			}
			if (!t.processTransaction(miner.getBlockchain())) { // validate txn
				return false;
			}
		}

		return true;
	}

	@Override
	public String toString() {
		return new GsonBuilder().setPrettyPrinting().create().toJson(this);
	}

	public void incNonce() {
		this.nonce++;

	}

}
