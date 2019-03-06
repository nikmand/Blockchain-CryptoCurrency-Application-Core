package distributed.core.beans;

import distributed.core.entities.Blockchain;

public class MsgChain extends Message {

	private Blockchain chain;

	public MsgChain(Blockchain _chain) {
		chain = _chain;
	}

	public Blockchain getChain() {
		return chain;
	}

}
