package uk.co.alvagem.sofabed;

import java.net.InetAddress;

/**
 * Basic information about a node in the cluster. Will be speciallised for
 * server and client views of the cluster.
 * @author rbp28668
 *
 */
public class Node {
	protected int nodeId;
	protected InetAddress address; // of this node
	protected int serverPort;      // to connect to for server to server comms
	protected int clientPort;
	private boolean isAlive;
	private long lastContact;

	/**
	 * Sets the integer node ID used for rendevous algorithm
	 * @return
	 */
	public int getNodeId() {
		return nodeId;
	}

	public void setNodeId(int nodeId) {
		this.nodeId = nodeId;
	}

	
	/**
	 * Gets the address of the node. Note implicit assumption
	 * that there's a single NIC and therefore address for 
	 * client to server and server to server comms.
	 * @return
	 */
	public InetAddress getAddress() {
		return address;
	}

	public void setAddress(InetAddress address) {
		this.address = address;
	}

	/**
	 * Gets the port the server is listening on for server to server comms
	 * @return
	 */
	public int getServerPort() {
		return serverPort;
	}

	public void setServerPort(int serverPort) {
		this.serverPort = serverPort;
	}

	/**
	 * Gets the port the server is listening on for comms from the client.
	 * @return
	 */
	public int getClientPort() {
		return clientPort;
	}

	public void setClientPort(int clientPort) {
		this.clientPort = clientPort;
	}

	/**
	 * Flag whether this node is alive. Invariably this is a node's view
	 * of another node or the clients view of a cluster node.
	 * @return
	 */
	public boolean isAlive() {
		return isAlive;
	}

	public void setAlive(boolean isAlive) {
		this.isAlive = isAlive;
	}

	/**
	 * Record the time this node last responded.  Used for marking nodes as failed
	 * if they stop responding.
	 * @return
	 */
	public long getLastContact() {
		return lastContact;
	}

	public void setLastContact(long lastContact) {
		this.lastContact = lastContact;
	}

	/**
	 * Mark this node as active at this point in time. 
	 */
	public void markAsActive() {
		isAlive = true;
		lastContact = System.currentTimeMillis();
	}
	
	/**
	 * If the node hasn't been marked as active after the
	 * threshold time then mark as inactive.
	 * @param thresholdTime
	 */
	public void checkActive(long thresholdTime) {
		if(lastContact < thresholdTime) {
			isAlive = false;
		}
	}


	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((address == null) ? 0 : address.hashCode());
		result = prime * result + nodeId;
		result = prime * result + clientPort;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ClusterNode other = (ClusterNode) obj;
		if (address == null) {
			if (other.address != null)
				return false;
		} else if (!address.equals(other.address))
			return false;
		if (nodeId != other.nodeId)
			return false;
		if (clientPort != other.clientPort)
			return false;
		return true;
	}

}
