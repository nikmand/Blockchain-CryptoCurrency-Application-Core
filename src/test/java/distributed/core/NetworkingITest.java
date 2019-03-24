package distributed.core;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import distributed.core.entities.NodeMiner;
import distributed.core.utilities.Constants;

public class NetworkingITest {

	private static Logger LOG = LoggerFactory.getLogger(NetworkingITest.class.getName());

	@BeforeClass
	public static void setUp() {
		// ByteArrayInputStream in = new ByteArrayInputStream("Test test".getBytes());
		// System.setIn(in);
	}

	@Test
	public void quickTest() {
		for (int i = 0; i < Constants.NUM_OF_NODES; i++) {
			LOG.info("Starting thread {}", i);
			int j = i;
			Thread zero = new Thread() {
				@Override
				public void run() {
					String[] args2 = new String[] { String.valueOf(4050 + j) };
					try {
						NodeMiner.main(args2);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			};
			zero.start();
		}
	}

	public void test() throws Exception {
		Thread zero = new Thread() {
			@Override
			public void run() {
				String[] args2 = new String[] { "4050" };
				try {
					NodeMiner.main(args2);
				} catch (Exception e) {
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
