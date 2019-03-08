package distributed.core.beans;

import java.security.PublicKey;

public class MsgChainRequest extends Message {

	public PublicKey publicKey;

	public MsgChainRequest(PublicKey key) {
		publicKey = key;
	}

	public PublicKey getPublicKey() {
		return publicKey;
	}

}
