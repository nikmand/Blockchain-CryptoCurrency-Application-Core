package distributed.core.beans;

import java.security.PublicKey;

public class MsgChainSizeRequest extends Message {

	//public PublicKey publicKey;
	public String id;

	public MsgChainSizeRequest(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	/* public PublicKey getPublicKey() { return publicKey; } */

}
