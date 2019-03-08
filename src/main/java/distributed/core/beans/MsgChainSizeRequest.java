package distributed.core.beans;

import java.security.PublicKey;

public class MsgChainSizeRequest extends Message {

	public PublicKey publicKey;

	public MsgChainSizeRequest(PublicKey key) {
		this.publicKey = key;
	}

	public PublicKey getPublicKey() {
		return publicKey;
	}

}
