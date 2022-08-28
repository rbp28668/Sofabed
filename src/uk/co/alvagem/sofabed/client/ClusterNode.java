package uk.co.alvagem.sofabed.client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.Node;

class ClusterNode extends Node {
	Connection connection;
	
	ClusterNode(InetAddress address, int port, int id) {
		super();
		this.address = address;
		this.clientPort = port;
		this.nodeId = id;
		setAlive(true);  // Assume node reachable and up until proven otherwise.
	}
	
	void connect(ClientImpl client, ClientSocketProcessingThread thread) throws IOException {
		connection = new Connection(client, thread);
		InetSocketAddress addr = new InetSocketAddress(address, clientPort);
		connection.connect(addr);
	}

	void write(ByteBuffer buffer) throws IOException {
		connection.write(buffer);
	}
	
}