package blockchain;

import java.io.ByteArrayInputStream;

import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import distributed.core.entities.NodeMiner;

public class NetworkingITest {

	private static Logger LOG = Logger.getLogger(NetworkingITest.class.getName());

	@BeforeClass
	public static void setUp() {
		// ByteArrayInputStream in = new ByteArrayInputStream("Test test".getBytes());
		// System.setIn(in);
	}

	@Test
	public void test() throws Exception {
		String[] args = new String[] { "10000", "20000" };
		NodeMiner.main(args);
		// se thread δεν συνεχίζει η εκτέλεση
		LOG.info("ela");
		// se thread
		String[] args2 = new String[] { "20000", "10000" };
		NodeMiner.main(args2);
	}

	@AfterClass
	public static void cleanUp() {
		// System.setIn(System.in);
	}
}
