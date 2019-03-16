package distributed.core.beans;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.GsonBuilder;

import distributed.core.entities.Blockchain;
import distributed.core.entities.NodeMiner;
import distributed.core.entities.Transaction;
import distributed.core.entities.TransactionOutput;
import distributed.core.exceptions.InvalidHashException;
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
	private int nonce; // todo check if it is better to compute it randomly
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

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public void setTransactions(ArrayList<Transaction> transactions) {
		this.transactions = transactions;
	}

	public String getMerkleRoot() {
		return merkleRoot;
	}

	public void setMerkleRoot() {
		this.merkleRoot = StringUtilities.getMerkleRoot(transactions);
	}

	public ArrayList<Transaction> getTransactions() {
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

	public void removeTxn(String id) {
		transactions.removeIf(x -> x.getTransactionId().equals(id));
	}

	public boolean proceedWithMine() { // TODO check what we can do for marchall trans when capacity and numOfNodes are not convinient
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

	private String prepareContent() { // to be deleted 
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

	/* Function that adds a Transaction on the current block if it is valid */
	public boolean addTransaction(Transaction transaction, Blockchain blockchain) {
		LOG.debug("Start addTransaction");

		if (transaction == null) {
			LOG.warn("Transaction was null, not added");
			return false;
		} else if (proceedWithMine()) { // we cannot add txn if it is already full
			LOG.warn("Block is full it should have alreary been mined!! Aborting...");
			return false;
		}
		if ((previousHash != "1")) { // if genesis block ignore
			synchronized (NodeMiner.lockTxn) {
				if ((transaction.validateTransaction(blockchain.getUTXOs()) != true)) {
					LOG.warn("Transaction failed to process. Discarded.");
					return false;
				}
			}
		}
		transactions.add(transaction);
		LOG.info("Transaction added to block");
		// TODO check if block is full and if yes mine it, now we do this manuallly
		return true;
	}

	public boolean validateBlock(String hashOfPreivousBlock) {
		LOG.debug("START validate block with hashOfPreivousBlock={}", hashOfPreivousBlock);

		if (!currentHash.equals(calculateHash())) { // compare registered hash and calculated hash
			LOG.warn("Current Hashes not equal");
			return false;
		}
		if (!hashOfPreivousBlock.equals(previousHash)) { 		// compare previous hash and registered previous hash
			throw new InvalidHashException("Previous Hash not equals the last one from our blockchain"); 			// call resolveConflict() ?
		}
		return true;
	}

	private boolean validateReceivedBlock(String hash, ConcurrentHashMap<String, TransactionOutput> UTXOs,
			Block current) {

		if (!validateBlock(hash)) {
			return false;
		}

		LOG.info("Continue validation of received block");

		Set<String> aux = current.getTransactions().stream().map(Transaction::getTransactionId)
				.collect(Collectors.toSet());

		for (Transaction t : transactions) {
			if (t == null) {
				LOG.debug("Transaction was null, continue"); // it doesn't cause trouble
				continue;
			}
			synchronized (NodeMiner.lockTxn) {
				String id = t.getTransactionId();
				if (aux.contains(id)) { // ελέγχοντας το hash σημαίνει ότι πρόκειται για την ίδια συναλλαγή καθώς δε μπροεί να βρεθεί
					LOG.debug("Txn is present at current block");  // ίδιο hash από άλλα δεδομένα
					current.removeTxn(id);
					continue; // meaning it has been validated
				}
				if (!t.validateTransaction(UTXOs)) { // validate txn
					// TODO blacklist the node that sent it
					return false;
				}
			}
		}

		return true;
	}

	public void incNonce() {
		this.nonce++;

	}

	@Override
	public String toString() {
		return new GsonBuilder().setPrettyPrinting().create().toJson(this);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((currentHash == null) ? 0 : currentHash.hashCode());
		result = prime * result + index;
		result = prime * result + ((merkleRoot == null) ? 0 : merkleRoot.hashCode());
		result = prime * result + nonce;
		result = prime * result + ((previousHash == null) ? 0 : previousHash.hashCode());
		result = prime * result + (int) (timestamp ^ (timestamp >>> 32));
		result = prime * result + ((transactions == null) ? 0 : transactions.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Block other = (Block) obj;
		if (currentHash == null) {
			if (other.currentHash != null) {
				return false;
			}
		} else if (!currentHash.equals(other.currentHash)) {
			return false;
		}
		if (index != other.index) {
			return false;
		}
		if (merkleRoot == null) {
			if (other.merkleRoot != null) {
				return false;
			}
		} else if (!merkleRoot.equals(other.merkleRoot)) {
			return false;
		}
		if (nonce != other.nonce) {
			return false;
		}
		if (previousHash == null) {
			if (other.previousHash != null) {
				return false;
			}
		} else if (!previousHash.equals(other.previousHash)) {
			return false;
		}
		if (timestamp != other.timestamp) {
			return false;
		}
		if (transactions == null) {
			if (other.transactions != null) {
				return false;
			}
		} /*else if (!transactions.equals(other.transactions)) { // TODO specify equallity for TXN
			return false;
			}*/
		return true;
	}

}
