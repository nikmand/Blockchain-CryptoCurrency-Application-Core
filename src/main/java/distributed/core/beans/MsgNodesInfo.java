package distributed.core.beans;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

public class MsgNodesInfo extends Message {

	public ConcurrentHashMap<String, Triple<PublicKey, String, Integer>> nodes;

	public MsgNodesInfo(ConcurrentHashMap<String, Triple<PublicKey, String, Integer>> concurrentHashMap) {
		this.nodes = concurrentHashMap;
	}

	public ConcurrentHashMap<String, Triple<PublicKey, String, Integer>> getNodes() {
		return nodes;
	}

}
