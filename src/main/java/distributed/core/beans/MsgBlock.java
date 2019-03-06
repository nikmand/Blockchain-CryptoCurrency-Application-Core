package distributed.core.beans;

public class MsgBlock extends Message {

	private Block block;

	public MsgBlock(Block block) {
		this.block = block;
	}

	public Block getBlock() {
		return block;
	}

}
