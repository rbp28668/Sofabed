package uk.co.alvagem.sofabed.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;

import uk.co.alvagem.sofabed.messages.client.ClusterInfoMessage;
import uk.co.alvagem.sofabed.messages.client.ClusterInfoMessageResponse;

class ClientImpl implements Client {

	private boolean connected = false;
	private final ClusterImpl clusterImpl;
	private ClientSocketProcessingThread background;
	private Semaphore lock = new Semaphore(0,false);
	private int waiting =0;
	
	ClientImpl() throws IOException{
		this.clusterImpl =  new ClusterImpl(this);
		background = new ClientSocketProcessingThread();
		new Thread(background,"Client Background Thread").start();
	}
	

	@Override
	public void connect(InetSocketAddress[] addresses) throws IOException {
		for(int i=0; i<addresses.length; ++i) {
			
			Connection connection = new Connection(this,background);
			connection.connect(addresses[i]);
			
			ClusterInfoMessage msg = new ClusterInfoMessage(0);
			connection.write(msg.getBuffer());
		}
	}
	
	@Override
	public boolean isConnected() {
		return connected;
	}
	
	@Override
	public ClusterImpl getCluster()  throws IllegalStateException {
		if(!connected) {
			throw new IllegalStateException("Cluster not connected");
		}
		return clusterImpl;
	}

	@Override
	public ClusterImpl getClusterBlocking() throws InterruptedException {
		if(!connected) {
			++waiting;
			lock.acquire();
		}
		return clusterImpl;
	}

	
	void receiveClusterInfo(ClusterInfoMessageResponse response) throws IOException {
		clusterImpl.updateConfiguration(response);
		connected = true;
		lock.release(waiting);
		waiting = 0;
	}
	
	
	

	

//	private static class Node {
//		InetAddress address;
//		int port;
//		Connection connection;
//		
//	}

}
