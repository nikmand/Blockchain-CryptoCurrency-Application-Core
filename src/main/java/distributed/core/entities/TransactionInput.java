package distributed.core.entities;

import java.io.Serializable;

public class TransactionInput implements Serializable {

	public String transactionOutputId; // Reference to TransactionOutputs -> transactionId
	public TransactionOutput UTXO; // Contains the Unspent transaction output

	public TransactionInput(String transactionOutputId) {
		this.transactionOutputId = transactionOutputId;
	}

	public String getTransactionOutputId() {
		return transactionOutputId;
	}

	@Override
	public String toString() {
		String aux = "{\n" + "txnId: " + transactionOutputId;
		if (UTXO != null) {
			aux += "\ntxnOutput" + UTXO.toString();
		}
		aux += "}";
		return aux;
	}

}
