package distributed.core.threads;

import java.io.ObjectOutputStream;
import java.net.Socket;

import distributed.core.beans.Message;

/*
 *
 * A client Thread is instantiated whenever we read and want to send
 * a new message to the Server.
 *
 */
public class ClientThread extends Thread {

	private String address;
	private int port;
	// private String message;
	private Message message;

	/**
	 * Add data to sent to a Message object
	 * 
	 * @return the Message
	 */
	private Message createMessage() {
		return null;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public ClientThread() {
	}

	public ClientThread(String address, int port, Message message) {
		this.address = address;
		this.port = port;
		this.message = message;
	}

	@Override
	public void run() {
		try {
			Socket socket = new Socket(this.address, this.port);
			// Instantiate an Object Output Stream to send the message
			ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
			// Write the message to the output stream
			outputStream.writeObject(this.message);
			// Close the socket after the object is written to stream
			socket.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
