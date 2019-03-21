package distributed.core.entities;

import java.io.Serializable;
import java.security.PublicKey;

import distributed.core.utilities.StringUtilities;

public class TransactionOutput implements Serializable {

	private String id;
	private PublicKey reciepient; // also known as the new owner of these coins.
	private float value; // the amount of coins they own
	private String parentTransactionId; // the id of the transaction this output was created in

	public TransactionOutput(PublicKey reciepient, float value, String parentTransactionId) {
		this.reciepient = reciepient;
		this.value = value;
		this.parentTransactionId = parentTransactionId;
		this.id = StringUtilities.applySha256(
				StringUtilities.getStringFromKey(reciepient) + Float.toString(value) + parentTransactionId);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public PublicKey getReciepient() {
		return reciepient;
	}

	public void setReciepient(PublicKey reciepient) {
		this.reciepient = reciepient;
	}

	public float getValue() {
		return value;
	}

	public void setValue(float value) {
		this.value = value;
	}

	public String getParentTransactionId() {
		return parentTransactionId;
	}

	public void setParentTransactionId(String parentTransactionId) {
		this.parentTransactionId = parentTransactionId;
	}

	// Check if coin belongs to you
	public boolean isMine(PublicKey publicKey) {
		return (publicKey.equals(reciepient));
	}

	@Override
	public String toString() {
		return "{\n" + "id: " + id + "\nreceiver: " + NodeMiner.nodesPid.get(reciepient) + "\namount: " + value
				+ "\nparentId: " + parentTransactionId + "}";
	}

}
