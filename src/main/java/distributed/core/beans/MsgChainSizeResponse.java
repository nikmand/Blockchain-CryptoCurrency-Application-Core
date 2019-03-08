package distributed.core.beans;

import java.security.PublicKey;

public class MsgChainSizeResponse extends Message {

	private int size;
	private PublicKey key;

	public MsgChainSizeResponse(int size, PublicKey key) {
		this.size = size;
		this.key = key;
	}

	public int getSize() {
		return size;
	}

	public PublicKey getKey() {
		return key;
	}

}
