package distributed.core.utilities;

import java.net.Inet4Address;

public class Constants {

	public static String BOOTSTRAPADDRESS = false ? "10.124.209.234" : "192.168.1.58"; //58
	public static final int BOOTSTRAPPORT = 4050;

	public static int DIFFICULTY = 4;
	public static int CAPACITY = 1;
	public static int NUM_OF_NODES = 5;
	public static int CAPACITY_ONE;
	public static final float MIN_VALUE = 0; // minimum value that can be sent in a block (we need this?)
	public static final String TARGET = new String(new char[Constants.DIFFICULTY]).replace('\0', '0');

}
