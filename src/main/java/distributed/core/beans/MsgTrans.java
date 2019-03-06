package distributed.core.beans;

import distributed.core.entities.Transaction;

public class MsgTrans extends Message {

	private Transaction transaction;

	public MsgTrans(Transaction trans) {
		transaction = trans;
	}

	public Transaction getTransaction() {
		return transaction;
	}

}
