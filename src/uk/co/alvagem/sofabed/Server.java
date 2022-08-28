package uk.co.alvagem.sofabed;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.InstanceAlreadyExistsException;
import javax.management.JMException;

import uk.co.alvagem.sofabed.messages.server.ServerMessage;
import uk.co.alvagem.sofabed.messages.server.ServerRecoveryMessage;
import uk.co.alvagem.sofabed.messages.server.ServerRecoveryResponse;

public class Server implements Runnable{
	
	// current settings for this node.
	private final Settings settings;
	
	// Info about the whole cluster (including this node)
	private final Cluster cluster;
	
	// For reporting metrics via JMX
	private final Metrics metrics = new Metrics();
	
	// Set true to shut down the server.
	private boolean shutdown = false;
	
	// These could be expanded to thread pools.  At the moment - pool of 1 thread.
	private SocketProcessingThread clusterCommsThread = null;
	private SocketProcessingThread clientCommsThread = null;

	
	// All the buckets known to this node.
	private final Map<String, Bucket> buckets = new HashMap<>();

	// Source of correlationIds for server messages.
	private final AtomicLong correlationGenerator = new AtomicLong();
	private final CorrelationMap tasks = new CorrelationMap();
	private final TimeoutThread timeoutThread = new TimeoutThread(tasks);
	
	private final RecoveryThread recoveryReceiver;

	/**
	 * Creates a new server with the given settings and node index.
	 * @param settings are the cluster settings.
	 * @param thisNodeIndex is index that identifies which of the cluster nodes
	 * this one is.
	 * @throws IOException
	 */
	public Server(Settings settings, int thisNodeIndex) throws IOException{
		this.settings = settings;

		// Initialise cluster information from the settings
		cluster = new Cluster(settings, thisNodeIndex);
		
		// Set up receiver for any cluster recovery responses. These would arrive
		// in response to this node asking the others to recover.
		recoveryReceiver = new RecoveryThread(this);
		
		try {
			// and set up any buckets defined in the settings.
			for(Settings.Bucket bucket : settings.buckets()) {
				createBucket(bucket.getName());
			}
		} catch (BucketException e) {
			throw new IOException(e.getMessage());
		}
		
	}
	
	@Override
	public void run() {

		System.out.println("Server: Running " + cluster.thisNodeName());
		registerJMX(cluster.thisNodeName());

		PingThread pinger = null;
		ClusterListener clusterListener = null;
		ClientListener clientListener = null;
		
		try {

			// TODO specify bind address for both listeners (so can have different NICs for client & inter-server comms).

			// Start thread to handle intra-cluster comms
			clusterListener = new ClusterListener(this,cluster.thisNodeClusterPort()) ;
			new Thread(clusterListener, "ClusterListenerThread-" + cluster.thisNodeName()).start();

			// Start thread to handle comms from client
			clientListener = new ClientListener(this, cluster.thisNodeClientPort());
			new Thread(clientListener, "ClientListenerThread-" + cluster.thisNodeName()).start();

			clusterCommsThread = new SocketProcessingThread();
			new Thread(clusterCommsThread, "ClusterCommshread-" + cluster.thisNodeName()).start();
			
			clientCommsThread = new SocketProcessingThread();
			new Thread(clientCommsThread, "clientCommsThread-" + cluster.thisNodeName()).start();
			
			new Thread(timeoutThread, "serverTimoutThread-" + cluster.thisNodeName()).start();
			
			// Make connections to the other nodes.
			cluster.connectNodes(this);

			// Set up inter-node ping
			pinger = new PingThread(this);
			new Thread(pinger, "PingThread-" + cluster.thisNodeName()).start(); 
		
			// Just started so may be recovering from a crash.
			startNodeRecovery();
			
		} catch (IOException e) {
			// TODO - log startup failure.
			e.printStackTrace(System.err);
			return;
		}

		
		// The real action is now delegated to the various read/write/listener threads
		// Main thread just sleeps repeatedly until shut down.
		while (!shutdown) {
			try {
				Thread.sleep(1000);
			} catch (Exception e) {
				// TODO log the exception but try to keep going
			}
		}
		
		
		try {
			
			pinger.shutdown();
			timeoutThread.shutdown();
			clientCommsThread.shutdown();
			clusterCommsThread.shutdown();
			clientListener.shutdown();
			clusterListener.shutdown();
		
		} catch (IOException e) {
			// TODO Log the exception during shutdown
		}
	}

	
	private void registerJMX(String name) {
        try {
        	metrics.register(name);
		} catch(InstanceAlreadyExistsException e) {
			System.out.println("JMX instance already exists " + e.getMessage());
		}  catch (JMException e) {
			// TODO Log error.
			System.out.println("Unable to register JMX " + e.getMessage());
		} 
	}
	
	/**
	 * Set up processing for a new connection to or from a cluster node.
	 * @param socketChannel
	 * @return the server message procesor that is managing this connection.
	 * @throws IOException
	 */
	ServerMessageProcessor processClusterConnection(SocketChannel socketChannel) throws IOException {
		ServerMessageProcessor receiver = new ServerMessageProcessor(this, socketChannel, clusterCommsThread);
		clusterCommsThread.addChannel(socketChannel, receiver);
		return receiver;
	}

	/**
	 * Set up processing for a new connection from a client node.
	 * @param socketChannel
	 * @return The ClientMessageProcessor that is managing this connection.
	 * @throws IOException
	 */
	ClientMessageProcessor processClientConnection(SocketChannel socketChannel) throws IOException {
		ClientMessageProcessor receiver = new ClientMessageProcessor(this, socketChannel, clientCommsThread);
		clientCommsThread.addChannel(socketChannel, receiver);
		return receiver;
	}

	public Cluster getCluster() {
		return cluster;
	}
	
	public Settings getSettings() {
		return settings;
	}
	
	public Metrics getMetrics() {
		return metrics;
	}
	
	Bucket getBucket(String name) throws BucketException {
		name = name.toLowerCase();
		Bucket bucket = null;
		synchronized(buckets) {
			bucket = buckets.get(name);
		}
		if (bucket == null) {
			throw new BucketException("Bucket " + name + " not found", MessageStatus.UNKNOWN_BUCKET);
		}
		return bucket;
	}

	void createBucket(String name) throws BucketException{
		name = name.toLowerCase();
		synchronized(buckets) {
			if (buckets.containsKey(name)) {
				throw new BucketException("Bucket " + name + " already exists", MessageStatus.DUPLICATE_BUCKET);
			}
			Bucket bucket = new Bucket(name);
			buckets.put(name, bucket);
		}
	}

	Collection<Bucket> getBuckets(){
		return buckets.values();
	}
	
	long nextCorrelationId() {
		return correlationGenerator.getAndIncrement();
	}

	public void shutdown() {
		shutdown = true;
	}
	
	/**
	 * Starts off a node recovery process by sending recovery messages to all the other nodes in the cluster.
	 * The other nodes should respond with a stream of recovery messages that contain bucket/key/version for
	 * each record they think this node should have.
	 */
	public void startNodeRecovery() {
		
		for(ClusterNode cn : getCluster().getNodeInfo()) {
			if(cn.isLocalNode()) {
				continue;
			}
			
			long correlationId = nextCorrelationId();
			recoveryReceiver.addInitialCorrelationId(correlationId);
			ServerRecoveryMessage msg = new ServerRecoveryMessage(getCluster().thisNodeId(), correlationId);
			cn.send(msg);
		}
	}
	
	/**
	 * Starts the response to a recovery message from a different node.  The recovery thread runs and sends back
	 * recovery response messages to tell the requesting node all the records this node thinks the requesting
	 * node should have.
	 * @param msg is the initiating message.
	 * @param processor
	 */
	public void processRecoveryMessage(ServerRecoveryMessage msg, ServerMessageProcessor processor) {
		RecoveryThread rt = new RecoveryThread(this, msg, processor);
		new Thread(rt, "Recovery thread " + cluster.thisNodeName()).start();
	}

	public void processRecoveryResponse(ServerRecoveryResponse msg) {
		recoveryReceiver.receiveRecoveryResponse(msg);
	}

	///////////////////////////////////////////////////////////////////////////////
	// Message Processing for client to server messages
	///////////////////////////////////////////////////////////////////////////////


	///////////////////////////////////////////////////////////////////////////////
	// Message Processing for server to server messages
	///////////////////////////////////////////////////////////////////////////////
	
	
	/**
	 * Adds the task against a given message correlation ID.  When the response
	 * is returned with that id the response will be passed to the task.
	 * @param id is the correlation ID of the message being sent (and hence of the reply)
	 * @param task is the task to handle the reply
	 */
	void addTask(long id, Task task) {
		tasks.addTask(id, task);
	}
	
	
	/**
	 * Passes this message to the task as identified by the message's correlation ID.
	 * The task is removed from the correlationMap once processed.
	 * @param message
	 * @throws IOException
	 */
	void sendMessageToTask(ServerMessage message) throws IOException {
		long id = message.getCorrelationId();
		
		if(tasks.containsTask(id)) {
			Task task = tasks.getAndRemoveTask(id);
			task.process(message);
		} else {
			// Possible if task has been timed out and late response.
			// TODO log this
		}
		
	}



	
	///////////////////////////////////////////////////////////////////////////////
	// Main - server startup
	///////////////////////////////////////////////////////////////////////////////

	public static void main(String[] args) {
		try {
			int localNodes = 3;
			int clusterPort = Settings.Node.CLUSTER_PORT;
			int clientPort = Settings.Node.CLIENT_PORT;
			
			for(String arg : args) {
				
			}

			System.out.println("OP_ACCEPT " + SelectionKey.OP_ACCEPT);
			System.out.println("OP_CONNECT " + SelectionKey.OP_CONNECT);
			System.out.println("OP_READ " + SelectionKey.OP_READ);
			System.out.println("OP_WRITE " + SelectionKey.OP_WRITE);
			
			Settings settings = new Settings();
			
			
			for(int i=0; i<localNodes; ++i) {
				Settings.Node node = new Settings.Node();
				node.setClientPort(clientPort);
				node.setClusterPort(clusterPort);
				node.setNodeName("Node" + Integer.toString(i));
				settings.clusterNodes().add(node);
				++clientPort;
				++clusterPort;
			}
				
			// Fire up the main processing threads for all the servers that
			// should be running on this hardware node.
			List<Thread> threads = new LinkedList<Thread>();	
			for(int i=0; i<localNodes; ++i) {
				Server server = new Server(settings, i);
				Thread thread = new Thread(server, "SofabedServer" + Integer.toString(i));
				threads.add(thread);
				System.out.println("starting " + thread.getName());
				thread.start();
			}

			// Wait for all the threads to die.
			for(Thread thread : threads) {
				thread.join();
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}






}
