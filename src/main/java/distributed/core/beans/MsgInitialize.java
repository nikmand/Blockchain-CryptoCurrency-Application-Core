package distributed.core.beans;

import java.security.PublicKey;

public class MsgInitialize extends Message {

	public PublicKey publicKey;
	public String ipAddress;
	public int port;

	public MsgInitialize(PublicKey publicKey, String ipAddress, int port) {
		this.publicKey = publicKey;
		this.ipAddress = ipAddress;
		this.port = port;
	}

	public PublicKey getPublicKey() {
		return publicKey;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public int getPort() {
		return port;
	}

}
