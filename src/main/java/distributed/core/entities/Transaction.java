package distributed.core.entities;

import java.io.Serializable;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.GsonBuilder;

import distributed.core.utilities.StringUtilities;

public class Transaction implements Serializable {
	public static final Logger LOG = LoggerFactory.getLogger(Transaction.class.getName());

	public String transactionId; // this is also the hash of the transaction.
	public PublicKey senderAddress; // senders address/public key.
	public PublicKey receiverAddress; // Recipients address/public key.
	public float amount;
	public byte[] signature; // this is to prevent anybody else from spending funds in our wallet.

	public ArrayList<TransactionInput> inputs = new ArrayList<TransactionInput>();
	public ArrayList<TransactionOutput> outputs = new ArrayList<TransactionOutput>();

	public Transaction(PublicKey senderAddress, PublicKey receiverAddress, float amount,
			ArrayList<TransactionInput> inputs) {
		this.senderAddress = senderAddress;
		this.receiverAddress = receiverAddress;
		this.amount = amount;
		this.inputs = inputs;
	}

	public void setTransactionId(String id) {
		this.transactionId = id;
	}

	public TransactionOutput getOutputTrans(int index) {
		return outputs.get(index);
	}

	public void addToOutputs(TransactionOutput output) {
		outputs.add(output);
	}

	// This Calculates the transaction hash (which will be used as its Id)
	private String calulateHash() {
		// TODO handle the case where two identical transactions have the same hash
		return StringUtilities.applySha256(StringUtilities.getStringFromKey(senderAddress)
				+ StringUtilities.getStringFromKey(receiverAddress) + Float.toString(amount) // + sequence
		);
	}

	// Signs all the data we dont wish to be tampered with.

	public void generateSignature(PrivateKey privateKey) {
		String data = StringUtilities.getStringFromKey(senderAddress)
				+ StringUtilities.getStringFromKey(receiverAddress) + Float.toString(amount);
		signature = StringUtilities.applyECDSASig(privateKey, data);
	}

	// Verifies the data we signed hasnt been tampered with
	public boolean verifiySignature() {
		String data = StringUtilities.getStringFromKey(senderAddress)
				+ StringUtilities.getStringFromKey(receiverAddress) + Float.toString(amount);
		return StringUtilities.verifyECDSASig(senderAddress, data, signature);
	}

	// Returns true if new transaction could be created.
	// It also checks the validity of a transaction
	public boolean processTransaction(Blockchain blockchain) { // rename to validateTransactions
		LOG.info("START validate transaction");

		if (verifiySignature() == false) {
			LOG.warn("Transaction Signature failed to verify");
			return false;
		}

		// gather transaction inputs (Make sure they are unspent):
		for (TransactionInput i : inputs) { // τα έχει δημιουργήσει η sendFunds του wallet
			LOG.debug("Size of unspent trans = {}", blockchain.getUTXOs().size());
			i.UTXO = blockchain.getUTXOs().get(i.transactionOutputId); // θέτει το unspend output για τα trans

		}
		LOG.debug("Size of input trans ={}", inputs.size());

		// εμείς δε φαίνεται να έχουμε περιορισμό στο ελάχιστο ποσό που μπορούμε να
		// στείλουμε
		/*
		 * if (getInputsValue() < Blockchain.getMinimumTransaction()) {
		 * LOG.warn("Transaction Inputs insuficient: " + getInputsValue());
		 * LOG.info("Aborting transaction..."); return false; }
		 */

		// TODO check if I have suffiecient funds to send ???

		// generate transaction outputs:
		float leftOver = getInputsValue() - amount; // get value of inputs then the left over change:
		LOG.debug("InputValue ={}", getInputsValue());
		LOG.debug("Residual ={}", leftOver);
		transactionId = calulateHash();
		outputs.add(new TransactionOutput(this.receiverAddress, amount, transactionId)); // send value to recipient
		outputs.add(new TransactionOutput(this.senderAddress, leftOver, transactionId)); // send the left over 'change'
																							// back to sender

		// add outputs to Unspent list
		for (TransactionOutput o : outputs) {
			blockchain.getUTXOs().put(o.getId(), o);
		}

		// remove transaction inputs from UTXO lists as spent:
		for (TransactionInput i : inputs) {
			if (i.UTXO == null) {
				continue; // if Transaction can't be found skip it // isn't it a problem ?
			}
			blockchain.getUTXOs().remove(i.UTXO.getId());
		}

		return true;
	}

	// returns sum of inputs(UTXOs) values
	public float getInputsValue() {
		float total = 0;
		for (TransactionInput i : inputs) {
			if (i.UTXO == null) {
				continue; // if Transaction can't be found skip it
			}
			total += i.UTXO.getValue();
		}
		return total;
	}

	// returns sum of outputs:
	public float getOutputsValue() {
		float total = 0;
		for (TransactionOutput o : outputs) {
			total += o.getValue();
		}
		return total;
	}

	@Override
	public String toString() {
		return new GsonBuilder().setPrettyPrinting().create().toJson(this);
	}

}
