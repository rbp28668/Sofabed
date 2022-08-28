package uk.co.alvagem.sofabed;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import uk.co.alvagem.sofabed.messages.client.CreateMessageResponse;

abstract class TaskBase {

	protected Server server;
	protected MessageProcessor returnChannel;
	protected boolean failed = false;

	TaskBase(Server server, MessageProcessor returnChannel){
		this.server = server;
		this.returnChannel = returnChannel;
	}
	
	
	/**
	 * For the given bucket/key, get the ordered list of nodes to store the documents.
	 * These are ordered by the rendevous algorithm as each node is given a score based
	 * on a hash of its node ID and the bucket and key of the record.
	 * @param bucketName
	 * @param key
	 * @param cluster
	 * @return Sorted node list.
	 */
	protected ArrayList<NodeScore> nodeListFor(String bucketName, Key key, Cluster cluster) {
		ArrayList<NodeScore> scores = new ArrayList<>();
		for (ClusterNode node : cluster.getNodeInfo()) {
			NodeScore score = new NodeScore(node, bucketName, key);
			scores.add(score);
		}
		Collections.sort(scores); // Target nodes should be in descending order, first is primary etc.
		return scores;
	}

	/**
	 * Gets the target nodes to write to given the current replica count, cluster, 
	 * bucket and key.  Nodes are excluded if they're offline.
	 * @param bucketName
	 * @param key
	 * @return
	 */
	protected ArrayList<ClusterNode> getAliveTargetNodes(String bucketName, Key key) {
		Cluster cluster = server.getCluster();
		Settings settings = server.getSettings();
		
		// Sorted list of nodes we could write to.
		ArrayList<NodeScore> scores = nodeListFor(bucketName, key, cluster);

		// Get the list of nodes that should be written to.
		ArrayList<ClusterNode> targetNodes = new ArrayList<>();
		Iterator<NodeScore> iter = scores.iterator();
		for(int i=0; i<settings.getReplicaCount() && iter.hasNext(); ++i){
			NodeScore ns = iter.next();
			ClusterNode node = (ClusterNode)ns.getNode();
			if(node.isAlive()) {
				targetNodes.add(node);
			} else {
				// TODO log warning
				System.out.println("WARN: target node " + ((ClusterNode)ns.getNode()).getNodeName() + " is offline ");
			}
		}
		return targetNodes;
	}

	 protected <T> Long[]  getCorrelationIds(ArrayList<ClusterNode> targetNodes, Map<Long,T> correlationStore) {
		// Get all the correlation IDs ahead of time to avoid race conditions.
		for(ClusterNode node : targetNodes) {
			if(!node.isLocalNode()) {
				long id = server.nextCorrelationId();
				correlationStore.put(id, null); // before we send so no race condition
			}
		}
		Long[] ids = correlationStore.keySet().toArray(new Long[correlationStore.keySet().size()]);
		return ids;
	}


}
