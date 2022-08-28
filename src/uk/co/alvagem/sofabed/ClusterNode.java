package uk.co.alvagem.sofabed;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.Random;

import uk.co.alvagem.sofabed.messages.server.ServerMessage;

/**
 * Contains the running state of each cluster node as seen from this node.
 * @author rbp28668
 *
 */
public class ClusterNode extends Node {
	private String nodeName;
	private boolean localNode;
	private SocketChannel channel;
	private ServerMessageProcessor messageProcessor;
	
	/**
	 * Creates a connection to this node of the cluster from this server.
	 * @param server
	 * @return
	 * @throws IOException
	 */
	public SocketChannel connect(Server server) throws IOException {
		if(channel != null) {
			if(channel.isOpen()) {
				channel.close();
			}
		}
		
		System.out.println("ClusterNode: connecting to node " + nodeName);
		// Create outbound socket connection to this cluster node.
		channel = openChannel();
		
		messageProcessor = server.processClusterConnection(channel);
		
		return channel;
	}

	/**
	 * Opens the channel.  Note connection is made in blocking mode
	 * then made non-blocking.
	 * @return
	 * @throws IOException
	 */
	private SocketChannel openChannel() throws IOException {
		
		SocketChannel socketChannel = SocketChannel.open();
	    socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, false);
		// TODO look at increasing buffer sizes
		socketChannel.configureBlocking(false);
	    SocketAddress addr = new InetSocketAddress(address, serverPort);
	    socketChannel.connect(addr);
	    System.out.println("ClusterNode.openChannel " + socketChannel);
		return socketChannel;
	}
	

	public String getNodeName() {
		return nodeName;
	}

	public void setNodeName(String nodeName) {
		this.nodeName = nodeName;
	}
	

	public boolean isLocalNode() {
		return localNode;
	}

	public void setLocalNode(boolean localNode) {
		this.localNode = localNode;
	}

	public void send(ServerMessage msg) {
		// Make sure connect(...) was called at some point.
		if(messageProcessor == null) {
			throw new IllegalStateException("Node is not connected");
		}
		
		if(!messageProcessor.isConnected()) {
			try {
				System.out.println("ClusterNode " + nodeName + " is disconnected");
				// TODO log info reconnecting.
				SocketChannel socketChannel = openChannel();
				messageProcessor.reconnectTo(socketChannel);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		messageProcessor.write(msg.getBuffer());
		
	}


	/**
	 * Create a fairly random but reproducible ID.
	 */
	public void createId() {
		long seed = 31;
		byte[] addr = address.getAddress();
		for(byte b : addr) {
			seed = seed ^ b;
			seed = seed << 1;
		}
		seed = seed ^ serverPort;
		
		Random rand = new Random(seed);
		nodeId = rand.nextInt();
	}

}
