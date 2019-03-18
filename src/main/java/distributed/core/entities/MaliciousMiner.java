package distributed.core.entities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

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
			e.printStackTrace();
		}

		node.getBlockchain().printBlockChain();
		LOG.info("Size of blockchain={}", node.getBlockchain().getSize());

		// todo check what happens if we broadcast trans first

		//1st case: attaching to block a txn for which we don't have sufficient funds
		LOG.info("Setting txn with insufficient funds");
		node.getBalance();
		ArrayList<TransactionInput> inputs = new ArrayList<TransactionInput>();
		for (HashMap.Entry<String, TransactionOutput> entry : node.getWallet().getUtxids().entrySet()) {
			TransactionOutput utxo = entry.getValue();
			utxo.setValue(115);
			inputs.add(new TransactionInput(utxo.getId()));
		}
		Transaction malTrans = new Transaction(node.getPublicKey(), node.getNode("id0").getLeft(), 113, inputs);
		malTrans.generateSignature(node.getPrivateKey());

		MsgTrans msgTrans = new MsgTrans(malTrans);
		node.broadcastMsg(msgTrans);

		Block malBlock = new Block();
		malBlock.malAdd(malTrans);
		node.setCurrentBlock(malBlock);
		node.mineBlock();

		//2nd case: attaching a txn where he prenteds to be someone else and send money to himself
		/*		LOG.info("Setting txn as another node");
				Transaction malTransId = new Transaction(node.getNode("id0").getLeft(), node.getPublicKey(), 90, null);
				malTransId.generateSignature(node.getPrivateKey());
		
				MsgTrans msgTrans = new MsgTrans(malTransId);
				node.broadcastMsg(msgTrans);
		
				Block malBlockId = new Block();
				malBlockId.malAdd(malTransId);
				node.setCurrentBlock(malBlockId);
				node.mineBlock();*/

		//3rd case: send 2 valid trans with the same input trans one after the other

		try {
			Thread.sleep(13000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		node.getBlockchain().printBlockChain();
		LOG.info("My balance is {}", node.getBalance());
		LOG.info("Size of blockchain={}", node.getBlockchain().getSize());

		// TODO add txn pretending he is someone else
	}

}
