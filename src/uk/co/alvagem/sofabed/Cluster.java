package uk.co.alvagem.sofabed;

import java.io.IOException;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import uk.co.alvagem.sofabed.messages.server.HeartbeatMessage;
import uk.co.alvagem.sofabed.messages.server.HeartbeatResponseMessage;

/**
 * Manages the cluster as a whole including maintaining communications between the nodes.
 * @author rbp28668
 *
 */
public class Cluster {
	private ClusterNode thisNode;
	private final ArrayList<ClusterNode> clusterNodes = new ArrayList<>();
    private final Map<Integer, ClusterNode> nodeLookup = new HashMap<>();
    private long nodeTimeout = 750; // mS
    
	public Cluster(final Settings settings, int localIndex) throws UnknownHostException {

		if(localIndex < 0 || localIndex >= settings.clusterNodes().size()) {
			throw new IllegalArgumentException("Local index doesn't refer to valid node");
		}
		
		int idx = 0;
		for(Settings.Node node : settings.clusterNodes()) {
			ClusterNode clusterNode = new ClusterNode();
			clusterNode.setAddress(node.getAddress());
			clusterNode.setServerPort(node.getClusterPort());
			clusterNode.setClientPort(node.getClientPort());
			clusterNode.setAlive(true);
			clusterNode.setLastContact(0);
			clusterNode.setNodeName(node.getNodeName());
			clusterNode.createId();
			
			// Can over-ride the node ID if needed.
			if(idx == localIndex) {
				System.out.println("Local node index " + idx + " : " + clusterNode.getNodeName());
				clusterNode.setLocalNode(true);
				thisNode = clusterNode;
			}
			clusterNodes.add(clusterNode);
			nodeLookup.put(clusterNode.getNodeId(), clusterNode);
			++idx;
		}
		
		nodeTimeout = settings.getNodeTimeout();

	}
	
	private boolean isThisNode(ClusterNode node) {
		return node == thisNode;
	}
	
	public int thisNodeId() {
		return thisNode.getNodeId();
	}

	public String thisNodeName() {
		return thisNode.getNodeName();
	}

	public int thisNodeClusterPort() {
		return thisNode.getServerPort();
	}
	
	public int thisNodeClientPort() {
		return thisNode.getClientPort();
	}

	public ArrayList<ClusterNode> getNodeInfo() {
		return clusterNodes;
	}

	/**
	 * Simple linear search for a ClusterNode with the given node id.
	 * Linear should be ok as small number of nodes.
	 * @param nodeId
	 * @return node corresponding to the ID or null if not found.
	 */
	public ClusterNode getNodeById(int nodeId) {
		for(ClusterNode node : clusterNodes) {
			if(node.nodeId == nodeId) {
				return node;
			}
		}
		return null;
	}
		
	int nodeCount() {
		return clusterNodes.size();
	}

	/**
	 * Gets the number of nodes deemed to be alive rather than the total number of 
	 * nodes in the cluster.
	 * @return the number of nodes that are alive.
	 */
	public int activeNodeCount() {
		int count = 0;
		for(ClusterNode node : clusterNodes) {
			if(node.isAlive()) ++count;
		}
		return count;
	}

	
	public void connectNodes(Server server) throws IOException {
		System.out.println("Cluster: connecting nodes");
		for(ClusterNode node : clusterNodes) {
			if(isThisNode(node)) continue;
			node.connect(server);
		}
	}
	
	/**
	 * Received a heartbeat message from another node.
	 * @param msg is the inbound message.
	 * @param processor
	 * @throws IOException 
	 */
	public void processHeartbeat(HeartbeatMessage msg, ServerMessageProcessor processor) throws IOException {
		int nodeId = msg.getNodeId();
		long correlationId = msg.getCorrelationId();
		HeartbeatResponseMessage response = new HeartbeatResponseMessage(thisNode.getNodeId(), correlationId);
		processor.write(response.getBuffer());
		markNodeAsActive(nodeId);
	}

	/**
	 * Received a response to our own heartbeat message.
	 * @param msg is the response message
	 */
	public void processHeartbeatResponse(HeartbeatResponseMessage msg) {
		int nodeId = msg.getNodeId();
		markNodeAsActive(nodeId);
	}

	private void markNodeAsActive(int nodeId) {
		ClusterNode node = nodeLookup.get(nodeId);
		if(node != null) {
			node.markAsActive();
		} else {
			// TODO Log warning as shouldn't happen
		}
		
	}

	/**
	 * Checks whether the nodes are all active i.e. have responded within the last
	 * nodeTimeout mS.
	 */
	void checkNodesActive() {
		long timeout = System.currentTimeMillis() - nodeTimeout; // must have responded after this
		for(ClusterNode node : clusterNodes) {
			if(isThisNode(node)) continue;
			node.checkActive(timeout);
		}
	}

	/**
	 * Sends a heartbeat message to all the other nodes.  Note, send a new message
	 * to each node and don't re-use because sending manipulates buffer.
	 */
	public void pingNodes() {
		for(ClusterNode node : clusterNodes) {
			if(isThisNode(node)) continue;
			HeartbeatMessage msg = new HeartbeatMessage(thisNode.getNodeId());
			node.send(msg);
		}
		
	}




	
}
