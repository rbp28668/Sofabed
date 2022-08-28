package uk.co.alvagem.sofabed;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * Settings class to centralise the settings used for the server.
 * @author rbp28668
 *
 */
public class Settings {

	private final static int QUORUM_COUNT = 2;
	private final static int REPLICA_COUNT = 3; 
	private final static int PING_INTERVAL_MS = 250;
	private final static long NODE_TIMEOUT_MS = 750; 
	
	
	private int quorumCount = QUORUM_COUNT;
	private int replicaCount = REPLICA_COUNT;
	private int pingIntervalMs = PING_INTERVAL_MS;
	private long nodeTimeoutMs = NODE_TIMEOUT_MS;

	
	private Node thisNode;
	private List<Node> cluster = new LinkedList<>();
	private List<Bucket> buckets = new LinkedList<>();
	
	
	public Settings() throws SocketException, UnknownHostException{
//		pingIntervalMs = 1000;  // slow down for debugging
//		nodeTimeoutMs = 3 * pingIntervalMs;
	}
	
	void setSingleNode() throws UnknownHostException {
		// Default - if nothing available, single node on localhost.
		cluster.clear();
		thisNode = new Node();
		cluster.add(thisNode);
	}

	void loadProperties(Properties properties) throws UnknownHostException, SocketException {
		quorumCount = Integer.parseInt( properties.getProperty("min_replicas", Integer.toString(quorumCount)));
		replicaCount = Integer.parseInt( properties.getProperty("replica_count", Integer.toString(replicaCount)));
		pingIntervalMs = Integer.parseInt( properties.getProperty("ping_interval_ms", Integer.toString(pingIntervalMs)));
		nodeTimeoutMs = Long.parseLong( properties.getProperty("node_timeout_ms", Long.toString(nodeTimeoutMs)));
		
		int idx = 0;
		boolean hasNode = false;
		List<InetAddress> localAddresses = getLocalAddresses();
		
		// Look for node configuration
		do {
			hasNode = false;
			String prefix = "node" + Integer.toString(idx) + ".";
			String addr = properties.getProperty(prefix + "address");
			String serverPort = properties.getProperty(prefix + "server_port", Integer.toString(Node.CLUSTER_PORT));
			String clientPort = properties.getProperty(prefix + "client_port", Integer.toString(Node.CLIENT_PORT));
			String nodeName = properties.getProperty(prefix+"name", Integer.toString(idx));
			
			if(addr != null) {
				hasNode = true;
				
				Node node = new Node(addr, serverPort, clientPort, nodeName);
				if(localAddresses.contains(node.getAddress())){
					thisNode = node;
				}
			}
			
			++idx;
		} while(hasNode);
		
		// And bucket configuration
		idx = 0;
		boolean hasBucket = false;
		do {
			hasBucket = false;
			String prefix = "bucket" + Integer.toString(idx) + ".";
			String name = properties.getProperty(prefix + "name");
			if(name != null) {
				hasBucket = true;
				Bucket bucket = new Bucket(name);
				buckets.add(bucket);
			}
		} while(hasBucket);
		
		
	}
	
	
	private List<InetAddress> getLocalAddresses() throws SocketException {
		List<InetAddress> addrList = new ArrayList<InetAddress>();
		for(Enumeration<NetworkInterface> eni = NetworkInterface.getNetworkInterfaces(); eni.hasMoreElements(); ) {
		   final NetworkInterface ifc = eni.nextElement();
		   if(ifc.isUp()) {
		      for(Enumeration<InetAddress> ena = ifc.getInetAddresses(); ena.hasMoreElements(); ) {
		        addrList.add(ena.nextElement());
		      }
		   }
		}
		return addrList;
	}
	
	
	/**
	 * Gets the quorum count. This is the minimum number of writes that must succeed for a write to 
	 * be considered a success or number of nodes that must agree on the current version.
	 * @return
	 */
	int getQuorumCount() {
		return quorumCount;
	}
	
	/**
	 * This is the number of replicas that should be written. 
	 * @return
	 */
	int getReplicaCount() {
		return replicaCount;
	}
	
	/**
	 * Time between heartbeat messages for the internal node-node checking.
	 * @return time between messages in mS
	 */
	int getPingIntervalMs() {
		return pingIntervalMs;
	}
	
	/**
	 * Gets the number of mS that have to elapse without a reply from a node
	 * for it to be marked as inactive.
	 * @return node timeout in mS.
	 */
	public long getNodeTimeout() {
		return nodeTimeoutMs;
	}

	
	/**
	 * Gets this node
	 * @return
	 * @throws UnknownHostException
	 */
	Node thisNode() throws UnknownHostException {
		return thisNode;
	}
	
	/**
	 * Gets all the nodes in the cluster.
	 * @return
	 */
	List<Node> clusterNodes() {
		return cluster;
	}
	
	/**
	 * Adds a node configuration to the cluster.
	 * @param node
	 */
	public void addNode(Node node) {
		cluster.add(node);
		
	}

	List<Bucket> buckets(){
		return buckets;
	}
	
	public void addBucket(Bucket bucket) {
		buckets.add(bucket);
	}
	
	/**
	 * Configuration of a single node.
	 * @author rbp28668
	 *
	 */
	public static class Node {
		public final static int CLUSTER_PORT = 4444;
		public final static int CLIENT_PORT = 2222;
		
		private int clusterPort = CLUSTER_PORT;
		private int clientPort = CLIENT_PORT;
		private String nodeName = null;
		InetAddress address;
		
		public Node() throws UnknownHostException{
			address = Inet4Address.getLocalHost();
		}
		
		public Node(String addr, String serverPort, String clientPort, String nodeName) throws UnknownHostException {
			this.address = InetAddress.getByName(addr);
			this.clusterPort = Integer.parseInt(serverPort);
			this.clientPort = Integer.parseInt(clientPort);
			this.nodeName = nodeName;
		}



		int getClusterPort() {
			return clusterPort;
		}
		
		int getClientPort() {
			return clientPort;
		}
		
		InetAddress getAddress() {
			return address;
		}

		String getNodeName() {
			if(nodeName == null) {
				return address.getHostAddress() + ":" + Integer.toString(clusterPort);
			}
			return nodeName;
		}


		public void setClientPort(int clientPort) {
			this.clientPort = clientPort;
		}

		public void setClusterPort(int clusterPort) {
			this.clusterPort = clusterPort;
		}
		
		public void setNodeName(String nodeName) {
			this.nodeName = nodeName;
		}
	}


	/**
	 * Configuration of a single bucket.
	 * @author rbp28668
	 *
	 */
	public static class Bucket {
		private String name;
		
		public Bucket(String name) {
			this.name = name.toLowerCase();
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name.toLowerCase();
		}
		
		
	}
}
