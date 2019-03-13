package distributed.core.beans;

import java.security.PublicKey;

public class MsgChainSizeResponse extends Message {

	private int size;
	private String id;
	//private PublicKey key;

	public MsgChainSizeResponse(int size, String id) {
		this.size = size;
		this.id = id;
	}

	public int getSize() {
		return size;
	}

	public String getId() {
		return id;
	}

	/* public PublicKey getKey() { return key; } */

}
