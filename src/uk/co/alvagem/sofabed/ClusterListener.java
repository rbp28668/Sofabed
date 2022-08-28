package uk.co.alvagem.sofabed;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * Listener thread to listen for connections from other nodes in the cluster.
 * @author rbp28668
 *
 */
public class ClusterListener implements Runnable {

	private final Server server;
	private final ServerSocketChannel clusterSocketChannel; // to receive inter-cluster calls
	private boolean terminate = false;
	
	ClusterListener(Server server, int port) throws IOException{
		this.server = server;
		
		clusterSocketChannel = ServerSocketChannel.open();
		clusterSocketChannel.socket().bind(new InetSocketAddress(port));
		clusterSocketChannel.configureBlocking(true); // now it's on a thread we want to block
	}
	
	@Override
	public void run() {
		while(!terminate) {
			try {
				SocketChannel socketChannel = clusterSocketChannel.accept();
				if(socketChannel != null) {
					System.out.println("ClusterListener: Socket channel connection received, connected:  " + socketChannel);
					server.processClusterConnection(socketChannel);
				}
			} catch (AsynchronousCloseException e) {
				// this is thrown if shutdown is called and the channel is closed.
				// Normal operation so nop.
			} catch (Exception e) {
				// TODO Unexpected so log this.
				e.printStackTrace();
			}
		}

	}
	


	public void shutdown() throws IOException {
		terminate = true;
		clusterSocketChannel.close();
	}
}
