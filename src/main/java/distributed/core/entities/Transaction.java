package distributed.core.entities;

import java.io.Serializable;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.GsonBuilder;

import distributed.core.utilities.StringUtilities;

public class Transaction implements Serializable {
	public static final Logger LOG = LoggerFactory.getLogger(Transaction.class.getName());

	private String transactionId; // this is also the hash of the transaction.
	private PublicKey senderAddress; // senders address/public key.
	private PublicKey receiverAddress; // Recipients address/public key.
	private float amount;
	private byte[] signature; // this is to prevent anybody else from spending funds in our wallet.

	private ArrayList<TransactionInput> inputs = new ArrayList<TransactionInput>();
	private ArrayList<TransactionOutput> outputs = new ArrayList<TransactionOutput>();

	public Transaction(PublicKey senderAddress, PublicKey receiverAddress, float amount,
			ArrayList<TransactionInput> inputs) {
		this.senderAddress = senderAddress;
		this.receiverAddress = receiverAddress;
		this.amount = amount;
		this.inputs = inputs;
	}

	public PublicKey getSenderAddress() {
		return senderAddress;
	}

	public PublicKey getReceiverAddress() {
		return receiverAddress;
	}

	public float getAmount() {
		return amount;
	}

	public String getTransactionId() {
		return transactionId;
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
	// This happens during validation by each node
	private String calulateHash() {
		return StringUtilities.applySha256(formData());
	}

	private String formData() {
		return StringUtilities.getStringFromKey(senderAddress) + StringUtilities.getStringFromKey(receiverAddress)
				+ Float.toString(amount)
				+ (inputs == null ? ""
						: inputs.stream().map(TransactionInput::getTransactionOutputId)
								.collect(Collectors.joining("")));
	}

	// Signs all the data we dont wish to be tampered with.
	public void generateSignature(PrivateKey privateKey) {
		String data = StringUtilities.getStringFromKey(senderAddress)
				+ StringUtilities.getStringFromKey(receiverAddress) + Float.toString(amount);
		signature = StringUtilities.applyECDSASig(privateKey, formData());
	}

	// Verifies the data we signed hasnt been tampered with
	public boolean verifiySignature() {
		String data = StringUtilities.getStringFromKey(senderAddress)
				+ StringUtilities.getStringFromKey(receiverAddress) + Float.toString(amount);
		return StringUtilities.verifyECDSASig(senderAddress, formData(), signature);
	}

	// Returns true if new transaction could be created.
	// It also checks the validity of a transaction
	public boolean validateTransaction(ConcurrentHashMap<String, TransactionOutput> UTXOs) {
		LOG.info("START validateTransaction");	// needs exclusive access to lock and lockTxn

		if (verifiySignature() == false) {
			LOG.warn("Transaction Signature failed to verify");
			return false;
		}

		// gather transaction inputs (Make sure they are unspent):
		for (TransactionInput i : inputs) { // τα έχει δημιουργήσει η sendFunds του wallet
			LOG.debug("Size of unspent trans = {}", UTXOs.size());
			TransactionOutput aux = UTXOs.get(i.transactionOutputId);
			if (aux == null) {
				LOG.warn("Output txn of input txn it's not in UTXOs. Aborting txn... Input txn was {}", i);
				return false;
			}
			i.UTXO = aux;  // θέτει το unspend output για τα trans

		}
		LOG.debug("Size of input trans ={}", inputs.size());

		// generate transaction outputs:
		float leftOver = getInputsValue() - amount; // get value of inputs then the left over change:
		if (leftOver < 0) {
			LOG.warn("Value of input txns is not enough. Aborting txn....");
			return false;
		}
		LOG.debug("InputValue ={}", getInputsValue());
		transactionId = calulateHash();
		outputs.add(new TransactionOutput(this.receiverAddress, amount, transactionId)); // send value to recipient
		outputs.add(new TransactionOutput(this.senderAddress, leftOver, transactionId)); // send the left over 'change'
																						// back to sender

		// add outputs to Unspent list
		for (TransactionOutput o : outputs) {
			// TODO αν υπάρχει ήδη το output return false μας πρόλαβαν
			UTXOs.put(o.getId(), o);
		}

		// remove transaction inputs from UTXO lists as spent:
		for (TransactionInput i : inputs) {
			if (i.UTXO == null) {
				LOG.warn("Input transactions was not found!");
				continue; // if Transaction can't be found skip it // isn't it a problem ?
			}
			// αν δεν υπάρχει πριν το remove to input μας πρόλαβαν abort
			UTXOs.remove(i.UTXO.getId()); // λογικα δεν κινδυνεύουμε να σβήνει ο ένας ένα trans και ο 
		}												  // άλλος άλλο οπότε να φάνε άκυρο και οι δύο καθώς αυτά εξετάζονται με την ίδια σειρά  	
		// αφού είναι το ίδιο τρανς ίδια Input list klp
		return true;
	}

	// returns sum of inputs(UTXOs) values
	public float getInputsValue() {
		float total = 0;
		for (TransactionInput i : inputs) {
			if (i.UTXO == null) {
				LOG.warn("Input transaction was not found!!");
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

	/*	@Override
		public String toString() {
			return new GsonBuilder().setPrettyPrinting().create().toJson(this);
		}*/

	@Override
	public String toString() {
		String aux = "{\n" + "id: " + transactionId + "\nsender: " + NodeMiner.nodesPid.get(senderAddress)
				+ "\nreceiver: " + NodeMiner.nodesPid.get(receiverAddress) + "\namount: " + amount + "\nTxnInputs [\n";
		if (inputs != null) {
			for (TransactionInput t : inputs) {
				if (t == null) {
					continue;
				}
				aux += t.toString();
			}
		}
		aux += "]" + "\nTxnOutputs: [\n";
		if (outputs != null) {
			for (TransactionOutput t : outputs) {
				if (t == null) {
					continue;
				}
				aux += t.toString();
			}
		}
		aux += "]" + "}";
		return aux;
	}

}
