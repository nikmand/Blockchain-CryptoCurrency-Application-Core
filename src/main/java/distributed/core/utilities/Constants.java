package distributed.core.utilities;

import java.net.Inet4Address;

public class Constants {

	public static String BOOTSTRAPADDRESS = false ? "192.168.0.1" : "192.168.1.58"; //58
	public static final int BOOTSTRAPPORT = 4050;

	public static int DIFFICULTY = 4;
	public static int CAPACITY = 5;
	public static int NUM_OF_NODES = 5;
	public static final String TARGET = new String(new char[Constants.DIFFICULTY]).replace('\0', '0');
	public static String FILEPATH = "C:\\Users\\nikmand\\OneDrive\\DSML\\Κατανεμημένα\\blockchain\\src\\main\\resources\\";

}
