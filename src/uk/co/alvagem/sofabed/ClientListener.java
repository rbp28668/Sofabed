package uk.co.alvagem.sofabed;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class ClientListener implements Runnable {

	private final Server server;
	private ServerSocketChannel clientSocketChannel; // to receive client calls
	private boolean terminate = false;

	public ClientListener(Server server, int thisNodeClientPort) throws IOException {
		this.server = server;
		
		clientSocketChannel = ServerSocketChannel.open();
		clientSocketChannel.socket().bind(new InetSocketAddress(thisNodeClientPort));
		clientSocketChannel.configureBlocking(true);

	}

	@Override
	public void run() {
		while(!terminate) {
			try {
				SocketChannel socketChannel = clientSocketChannel.accept();
				if(socketChannel != null) {
					server.processClientConnection(socketChannel);
				}
			} catch (AsynchronousCloseException e) {
				// Normal if terminating as shutdown closes the socket.
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	
	public void shutdown() throws IOException {
		terminate = true;
		clientSocketChannel.close();
	}



}
