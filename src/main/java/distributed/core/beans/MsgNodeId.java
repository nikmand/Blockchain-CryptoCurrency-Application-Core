package distributed.core.beans;

public class MsgNodeId extends Message {

	private int id;

	public MsgNodeId(int node) {
		this.id = node;
	}

	public int getId() {
		return id;
	}

}
