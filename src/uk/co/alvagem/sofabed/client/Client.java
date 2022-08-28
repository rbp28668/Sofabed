package uk.co.alvagem.sofabed.client;

import java.io.IOException;
import java.net.InetSocketAddress;

public interface Client {

	static public Client getClient() throws IOException {
		return new ClientImpl();
	}
	
	/**
	 * Tries to connect to the cluster.  The array of addresses can contain
	 * one or more cluster nodes. The client will initialise itself from
	 * information returned by the cluster once a node replies.
	 * @param addresses array of cluster addresses to try.
	 * @throws IOException
	 */
	public void connect(InetSocketAddress[] addresses) throws IOException;
	
	/**
	 * Determines whether the client has connected to the cluster.
	 * @return true if connected;
	 */
	public boolean isConnected();

	/**
	 * Gets the cluster. Note that the client must have connected for this
	 * to return.  Check isConnected() before calling this.
	 * @return the cluster.
	 * @throws IllegalStateException if the cluster is not connected.
	 */
	public ClusterImpl getCluster() throws IllegalStateException;
	
	/**
	 * Gets the cluster but waits for it to be connected.  The calling
	 * thread will block until the cluster is connected.
	 * @return the cluster.
	 * @throws InterruptedException if this thread is interrupted.
	 */
	public ClusterImpl getClusterBlocking()  throws InterruptedException;

	
}
