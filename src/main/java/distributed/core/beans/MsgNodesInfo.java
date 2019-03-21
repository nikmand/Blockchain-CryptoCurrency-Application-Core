package distributed.core.beans;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.tuple.Triple;

public class MsgNodesInfo extends Message {

	public ConcurrentHashMap<String, Triple<PublicKey, String, Integer>> nodes;
	public HashMap<PublicKey, String> nodesPid;

	public MsgNodesInfo(ConcurrentHashMap<String, Triple<PublicKey, String, Integer>> concurrentHashMap,
			HashMap<PublicKey, String> hashMap) {
		this.nodes = concurrentHashMap;
		this.nodesPid = hashMap;
	}

	public ConcurrentHashMap<String, Triple<PublicKey, String, Integer>> getNodes() {
		return nodes;
	}

	public HashMap<PublicKey, String> getNodesPid() {
		return nodesPid;
	}

}
