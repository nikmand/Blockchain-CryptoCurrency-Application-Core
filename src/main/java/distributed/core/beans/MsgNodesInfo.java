package distributed.core.beans;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.tuple.Pair;

public class MsgNodesInfo extends Message {

	public ConcurrentHashMap<PublicKey, Pair<String, Integer>> nodes;

	public MsgNodesInfo(ConcurrentHashMap<PublicKey, Pair<String, Integer>> _nodes) {
		this.nodes = _nodes;
	}

	public ConcurrentHashMap<PublicKey, Pair<String, Integer>> getNodes() {
		return nodes;
	}

}
