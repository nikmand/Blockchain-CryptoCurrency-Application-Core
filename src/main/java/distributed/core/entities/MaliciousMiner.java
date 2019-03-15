package distributed.core.entities;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import distributed.core.beans.Block;
import distributed.core.beans.MsgTrans;

public class MaliciousMiner {

	private static final Logger LOG = LoggerFactory.getLogger(NodeMiner.class.getName());

	public static void main(String[] args) throws IOException {

		NodeMiner node = NodeMiner.initializeBackEnd(args);

		try {
			Thread.sleep(13000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		LOG.info(Blockchain.test);
		node.getBlockchain().printBlockChain();
		LOG.info("Size of blockchain={}", node.getBlockchain().getSize());

		// TODO check what happens if we broadcast trans first

		//1st case: attaching to block a txn for which we don't have sufficient funds
		LOG.info("Setting txn with insufficient funds");
		/*		Transaction malTrans = new Transaction(node.getPublicKey(), node.getNode("id0").getLeft(), 113, null);
				Block malBlock = new Block();
				malBlock.malAdd(malTrans);
				node.setCurrentBlock(malBlock);
				node.mineBlock();*/

		//2nd case: attaching a txn where he prenteds to be someone else and send money to himself
		LOG.info("Setting txn as another node");
		Transaction malTransId = new Transaction(node.getNode("id0").getLeft(), node.getPublicKey(), 90, null);
		// TODO add inputs txns
		malTransId.generateSignature(node.getPrivateKey());
		MsgTrans msgTrans = new MsgTrans(malTransId);
		node.broadcastMsg(msgTrans);

		Block malBlockId = new Block();
		malBlockId.malAdd(malTransId);
		node.setCurrentBlock(malBlockId);
		node.mineBlock();

		try {
			Thread.sleep(13000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		LOG.info(Blockchain.test);
		node.getBlockchain().printBlockChain();
		LOG.info("My balance is {}", node.getBalance());
		LOG.info("Size of blockchain={}", node.getBlockchain().getSize());

		// TODO add txn pretending he is someone else
	}

}
