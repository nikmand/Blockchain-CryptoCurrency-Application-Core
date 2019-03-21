package distributed.core.threads;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import distributed.core.beans.Message;
import distributed.core.beans.MessageType;
import distributed.core.entities.NodeMiner;
import distributed.core.utilities.MessageUtilities;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerThread extends Thread {

	private static final Logger LOG = LoggerFactory.getLogger(ServerThread.class.getName());

	private ServerSocket serverSocket;
	private int port;
	private NodeMiner miner;
	private boolean isRunning;

	public ServerThread(int port, NodeMiner miner) {
		// We need to define the port that the server listens to
		this.port = port;
		this.isRunning = true;
		this.miner = miner;
		try {
			// Try to open a ServerSocket on this port
			// and catch any exception that might occur during this.
			this.serverSocket = new ServerSocket(port);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public NodeMiner getMiner() {
		return miner;
	}

	public ServerSocket getServerSocket() {
		return serverSocket;
	}

	public void setServerSocket(ServerSocket serverSocket) {
		this.serverSocket = serverSocket;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public boolean isRunning() {
		return isRunning;
	}

	public void setRunning(boolean isRunning) {
		this.isRunning = isRunning;
	}

	@Override
	public void run() {
		while (isRunning()) {
			try {
				// Get a client socket that will process the request
				Socket socket = this.serverSocket.accept();
				// Initialize a new Thread to process the result while the
				// server will wait for new incoming requests
				ClientServerThread cliSrvThread = new ClientServerThread(socket, this, this.miner);
				cliSrvThread.setDaemon(false); // set to true
				(cliSrvThread).start();
			} catch (java.net.SocketException e) {
				LOG.warn("Server is shutting down");
				this.isRunning = false;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
