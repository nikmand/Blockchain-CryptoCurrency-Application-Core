package distributed.core.beans;

import java.security.PublicKey;
import java.util.HashMap;

import org.apache.commons.lang3.tuple.Pair;

public class MsgNodesInfo extends Message {

	public HashMap<PublicKey, Pair<String, Integer>> nodes;

	public MsgNodesInfo(HashMap<PublicKey, Pair<String, Integer>> _nodes) {
		this.nodes = _nodes;
	}

	public HashMap<PublicKey, Pair<String, Integer>> getNodes() {
		return nodes;
	}

}
