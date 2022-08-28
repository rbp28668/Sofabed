package uk.co.alvagem.sofabed.client;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import uk.co.alvagem.sofabed.Key;
import uk.co.alvagem.sofabed.Node;
import uk.co.alvagem.sofabed.NodeScore;
import uk.co.alvagem.sofabed.Version;
import uk.co.alvagem.sofabed.messages.client.ClientMessage;
import uk.co.alvagem.sofabed.messages.client.ClusterInfoMessageResponse;

class ClusterImpl implements Cluster{
	
	private final ClientImpl client;
	private List<ClusterNode> clusterNodes = new LinkedList<>();
	private final AtomicLong correlationGenerator = new AtomicLong();
	private ClientSocketProcessingThread thread;
	private ClientCorrelationMap correlationMap = new ClientCorrelationMap();
	
	ClusterImpl(ClientImpl client) throws IOException {
		this.client = client;
		thread = new ClientSocketProcessingThread();
		new Thread(thread, "Client thread").start();
		new Thread(correlationMap, "Client correlation map").start();
	}


	
	@Override
	public Bucket getBucket(String name) {
		return new BucketImpl(this, name);
	}

	long nextCorrelationId() {
		return correlationGenerator.getAndIncrement();
	}


	void updateConfiguration(ClusterInfoMessageResponse response) throws IOException {
		Set<ClusterNode> checked = new HashSet<>(clusterNodes); // tally nodes that already exist
		for(ClusterInfoMessageResponse.Node msgNode :   response.getNodes()) {
			InetAddress address = msgNode.address;
			int port = msgNode.port;
			int id = msgNode.id;
			ClusterNode clusterNode = findNode(address, port);
			if(clusterNode == null) {
				clusterNode = new ClusterNode(address, port, id);
				clusterNode.connect(client, getProcessingThread());
				clusterNodes.add(clusterNode);
			} else {
				checked.remove(clusterNode);
			}
		}
		
		if(!checked.isEmpty()) {
			// Node removed from cluster that the client currently knows about.
			// TODO log this change of state.
			for(ClusterNode clusterNode : checked) {
				clusterNode.connection.close();
				clusterNodes.remove(clusterNode);
			}
		}
	}
	
	/**
	 * Gets a processing thread.  Currently single thread, if need
	 * a thread pool implement here.
	 * @return
	 */
	private ClientSocketProcessingThread getProcessingThread() {
		return thread;
	}
	
	ClusterNode findNode(InetAddress address, int port) {
		for(ClusterNode clusterNode : clusterNodes) {
			if(clusterNode.getAddress().equals(address) && clusterNode.getClientPort() == port) {
				return clusterNode;
			}
		}
		return null;
	}
	
	/**
	 * For the given bucket/key, get the ordered list of nodes to store the documents.
	 * @param bucketName
	 * @param key
	 * @param cluster
	 * @return Sorted node list.
	 */
	private ArrayList<NodeScore> nodeListFor(String bucketName, Key key) {
		ArrayList<NodeScore> scores = new ArrayList<>();
		for (Node node : clusterNodes) {
			NodeScore score = new NodeScore(node, bucketName, key);
			scores.add(score);
		}
		Collections.sort(scores); // Target nodes should be in descending order, first is primary etc.
		return scores;
	}

	ArrayList<ClusterNode> getTargetNodes(String bucketName, Key key) {

		// Sorted list of nodes we could write to.
		ArrayList<NodeScore> scores = nodeListFor(bucketName, key);

		// Get the list of nodes that should be written to.
		ArrayList<ClusterNode> targetNodes = new ArrayList<>();
		for(NodeScore ns : scores) {
			ClusterNode node = (ClusterNode)ns.getNode();
			if(node.isAlive()) {
				targetNodes.add(node);
			}
		}
		return targetNodes;
	}

	
	/**
	 * Registers a client task against a (message) correlation Id so that the
	 * correct task can receive the reply from the server.
	 * @param cid
	 * @param clientTask
	 */
	void registerTask(long cid, ClientTask clientTask) {
		correlationMap.addClientTask(cid, clientTask);
	}

	/**
	 * Handle the response from the server.  Any message passed here should have a
	 * correlationId that matches a valid task.
	 * @param msg
	 * @throws IOException
	 */
	void handleResponse(ClientMessage msg) throws IOException {
		long id = msg.getCorrelationId();
		ClientTask task = correlationMap.getAndRemoveClientTask(id);
		if(task != null) {
			task.process(msg);
		} else {
			throw new IllegalArgumentException("Response received without matching task");
		}
	}
	
	
	
	
	Future<Version> create(String bucketName, Record record) throws IOException {
		return new CreateTask(this, bucketName, record);
	}

	Future<Record> read(String bucketName, Key key) throws IOException  {
		return new ReadTask(this,bucketName, key);
	}

	Future<Version>  write(String bucketName, Record record) throws IOException  {
		return new UpdateTask(this, bucketName, record);
	}

	Future<Void> delete(String bucketName, Key key, Version version) throws IOException {
		return new DeleteTask(this, bucketName, key, version);
	}


	Future<Version> lock(String bucketName, Key key) throws IOException  {
		// TODO Auto-generated method stub
		return null;
	}










}
