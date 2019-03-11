package distributed.core;

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
		Thread zero = new Thread() {
			@Override
			public void run() {
				String[] args2 = new String[] { "4050" };
				try {
					NodeMiner.main(args2);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};
		zero.start();
		// se thread δεν συνεχίζει η εκτέλεση

		LOG.info("Starting second node");
		// se thread
		Thread one = new Thread() {
			@Override
			public void run() {
				String[] args2 = new String[] { "4051" };
				try {
					NodeMiner.main(args2);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};
		one.start();

		LOG.info("Starting third node");
		// se thread
		Thread two = new Thread() {
			@Override
			public void run() {
				String[] args2 = new String[] { "4052" };
				try {
					NodeMiner.main(args2);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};
		two.start();

		zero.join();
		one.join();

	}

	@AfterClass
	public static void cleanUp() {
		// System.setIn(System.in);
	}
}
