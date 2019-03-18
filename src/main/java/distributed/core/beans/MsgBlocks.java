package distributed.core.beans;

import java.util.ArrayList;

public class MsgBlocks extends Message {

	private ArrayList<Block> blocks;
	private int index;

	public MsgBlocks(ArrayList<Block> blocks, int j) {
		this.blocks = blocks;
		this.index = j;
	}

	public ArrayList<Block> getBlocks() {
		return blocks;
	}

	public int getIndex() {
		return index;
	}

}
