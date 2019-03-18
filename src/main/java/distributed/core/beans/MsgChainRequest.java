package distributed.core.beans;

import java.util.List;

public class MsgChainRequest extends Message {

	//public PublicKey publicKey;
	private String id;
	private List<String> hashes;

	public MsgChainRequest(String id, List<String> hashes) {
		this.id = id;
		this.hashes = hashes;
	}

	public String getId() {
		return id;
	}

	public List<String> getHashes() {
		return hashes;
	}

	/* public PublicKey getPublicKey() { return publicKey; } */

}
