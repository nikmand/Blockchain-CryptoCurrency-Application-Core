package distributed.core.beans;

import java.security.PublicKey;

public class MsgChainRequest extends Message {

	//public PublicKey publicKey;
	private String id;

	public MsgChainRequest(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	/* public PublicKey getPublicKey() { return publicKey; } */

}
