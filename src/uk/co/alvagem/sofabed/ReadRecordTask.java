package uk.co.alvagem.sofabed;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import uk.co.alvagem.sofabed.messages.client.ReadMessage;
import uk.co.alvagem.sofabed.messages.client.ReadMessageResponse;
import uk.co.alvagem.sofabed.messages.server.ServerMessage;
import uk.co.alvagem.sofabed.messages.server.ServerReadMessage;
import uk.co.alvagem.sofabed.messages.server.ServerReadResponse;
import uk.co.alvagem.sofabed.messages.server.ServerReadVersionMessage;
import uk.co.alvagem.sofabed.messages.server.ServerReadVersionResponse;

/**
 * Server side task to read a record.  To ensure quorum it's necessary to send
 * version messages to the other nodes.  In normal operation a. there'll be a 
 * quorum and b. this node will have the right version.  If not.... have to sort
 * out the resulting mess.
 * @author rbp28668
 *
 */
public class ReadRecordTask extends TaskBase implements Task {

	private String bucketName;
	private Key key;
	private ArrayList<ClusterNode> targetNodes; // Nodes to talk to.
	private Map<Long, ResponseInfo> sentVersion = new HashMap<>(); // track received version info
	private long fetchCorrelationId = -1; // if need to get data from another node.
	private boolean complete = false;     // set true if complete (sent some form of response to client).
	private long clientCorrelationId;
	
	public ReadRecordTask(Server server, ReadMessage msg, ClientMessageProcessor processor) throws IOException {
		super(server, processor);
		this.bucketName = msg.getBucket().toLowerCase();
		this.key = msg.getKey();
		this.clientCorrelationId = msg.getCorrelationId();
		
		getVersions();
	}

	private void getVersions() throws IOException {
		try {
			Settings settings = server.getSettings();

			server.getBucket(bucketName); // abort early if bucket name doesn't exist.

			targetNodes = getAliveTargetNodes(bucketName, key);

			// If we haven't reached a quorum of nodes then forget it as we can't manage
			// consistency without a quorum
			if (targetNodes.size() < settings.getQuorumCount()) {
				sendFailureMessage(MessageStatus.INSUFFICIENT_NODES, bucketName, key);
				return;
			}

			// Set up the sentVersions map ahead of time. Once set up the map
			// won't be mutated so then won't need to be synchronised.
			for (ClusterNode node : targetNodes) {
				if (!node.isLocalNode()) {
					long cid = server.nextCorrelationId();
					ResponseInfo info = new ResponseInfo(node);
					sentVersion.put(cid, info);
				}
			}

			// Send messages to other servers to get version information.
			for (Map.Entry<Long, ResponseInfo> entry : sentVersion.entrySet()) {
				long correlationId = entry.getKey().longValue();
				ServerReadVersionMessage msg = new ServerReadVersionMessage(correlationId, bucketName, key);
				server.addTask(correlationId, this); // **before ** we actually send the message (race condition).
				entry.getValue().node.send(msg);
				System.out.println("ReadRecordTask: Send version message to " + entry.getValue().node.getNodeName() + " cid " + correlationId);
			}

			checkVersionsComplete(); // If quorum is 1 (debug/dev mode) may be able to send now.

		} catch (BucketException e) {
			sendFailureMessage(e.getStatus(), bucketName, key);
		} catch (IOException e) {
			throw e;
		}

	}



	/**
	 * Tries to send a failure message back to the client.  Last chance here so if this fails
	 * an error is logged.
	 * @param status
	 * @param bucketName
	 * @param key
	 */
	protected void sendFailureMessage(MessageStatus status, String bucketName, Key key)  {
		
		if(complete) return; // already sent
		
		try {
			complete = failed = true; // so abort processing after this
			System.out.println("ReadRecordTask: Returning failure " + status + " using correlationId " + clientCorrelationId);
			ReadMessageResponse response = new ReadMessageResponse(clientCorrelationId, status, bucketName, key,Version.NONE, new byte[0]);
			returnChannel.write(response.getBuffer());
		} catch (IOException e) {
			// At this point just log the error.
			String msg = "ReadRecordTask: Exception sending failure return from " + server.getCluster().thisNodeName();
			System.out.println(msg);
			// TODO - log error
		}
	}

	

	@Override
	public void abort(long correlationId) {
		if(correlationId == fetchCorrelationId) {
			// If this happens we've tried to get the record from another node and it's timed
			// out.  So give up at this point as we're pretty much out of options.  Note - 
			// could try all the other nodes that have it but grasping at straws at this point.
			sendFailureMessage(MessageStatus.NO_RESPONSE, bucketName, key);
		} else { // it's one of the version requests.
			ResponseInfo info = sentVersion.get(correlationId);
			info.status = MessageStatus.NO_RESPONSE;
			info.version = Version.NONE;
			checkVersionsComplete();
		}
	}

	@Override
	public void process(ServerMessage message) throws IOException {
		
		// May have already managed to send a response if quorum reached earlier
		if(complete) {
			return;
		}
		
		if(message.getType() == MessageType.SVR_VERSION_RESPONSE.getCode()) {
			ServerReadVersionResponse response = (ServerReadVersionResponse)message;

			System.out.println("ReadRecordTask: received version response status "  + response.getStatus() 
			+ " version " + response.getVersion() 
			+ " cid " + response.getCorrelationId());
			// Record the version response.
			ResponseInfo info = sentVersion.get(message.getCorrelationId());
			info.version = response.getVersion();
			info.status = response.getStatus();

			// And see if we've got a quorum
			checkVersionsComplete();
				
		} else if(message.getType() == MessageType.SVR_READ_RESPONSE.getCode()) {
			 //just received the doc from the other node
			try {
				complete = true;
				
				// store it locally 
				ServerReadResponse response = (ServerReadResponse)message;
				Document doc = new Document(key, response.getVersion(), response.getPayload());
				Bucket bucket = server.getBucket(bucketName);
				bucket.writeDocument(doc);
				
				// and send back to the client.
				returnDocumentToClient(doc);
			} catch (BucketException e) {
				sendFailureMessage(e.getStatus(), bucketName, key);
			} catch (IOException e) {
				throw e;
			}
		}
	}

	
	/**
	 * See if the process can be completed.  Ideally there are enough responses logged
	 * so that this node's document can be returned. If it's not quorate though then 
	 * check to see if the other nodes are quorate. If this is the case then ask the first
	 * node with the correct version.   If none of those apply and there are no more 
	 * messages to receive then we can't reach quorum so fail. 
	 */
	private void checkVersionsComplete() {
		try {
			// So have we got a quorum of versions?
			int quorumCount = server.getSettings().getQuorumCount();

			MessageStatus status = MessageStatus.OK;
		
		
			// Normal case - Does this node's document have the version that is quorate?
			// Normally the primary node will have the document and just needs confirmation
			// of version from one (perhaps more) node to reach quorum.
			try {
				Bucket bucket = server.getBucket(bucketName);
				Document doc = bucket.getDocument(key);
				
				// Normal case - This node is the current version so just confirm.
				int matches = 1; // Count this node - matches itself.
				for(ResponseInfo info : sentVersion.values()) {
					if(info.status == MessageStatus.OK && info.version.equals(doc.getVersion())) {
						++matches;
					}
				}
				
				// If we've found enough equal versions to validate this node's document
				// then we can just return the response tot he client and mark the
				// task as complete (so that we can ignore any subsequent messages).
				if(matches >= quorumCount) {
					System.out.println("Read quorum on " + server.getCluster().thisNodeName() + " version " + doc.getVersion().toString());
					returnDocumentToClient(doc);
					return;
				}
			} catch (BucketException e) {
				status = e.getStatus();
			}
			

			// Count of messages yet to receive.
			int toReceive = 0;
			for(ResponseInfo info : sentVersion.values()) {
				if(info.status == null) { 
					++toReceive;
				}
			}
		
			// If no more message to receive then see if we can find a quorate version in the 
			// responses received from the other nodes.  If so, fetch the result otherwise fail.
			
			if(toReceive == 0) {		
				// This isn't good as we've received all the responses and this node doesn't agree
				// with enough of the others to reach quorum.  It's possible then that there's been
				// a failure in the past and the other nodes have the quorum with this one being out
				// of step.
				
				// So look through the version info received from the other nodes. Don't fold in
				// this node's version as it's already determined that this node's version doesn't
				// have a quorum.
				Map<Version, Integer> counts = new HashMap<>();
					for(ResponseInfo info : sentVersion.values()) {
					if(MessageStatus.OK == info.status  ) {
						Version v = info.version;
						if(counts.containsKey(v)) {
							Integer count = counts.get(v);
							counts.put(v, Integer.valueOf(count.intValue()+1));
						} else {
							counts.put(v, Integer.valueOf(1));
						}
					}
				}
				
				// So, did any of the versions reach quorum?
				Version currentVersion = null;
				for(Map.Entry<Version, Integer> entry : counts.entrySet()) {
					if(entry.getValue().intValue() >= quorumCount) {
						currentVersion = entry.getKey();
						break;
					}
				}
	
				if(currentVersion == null) {
					// Buggered....
					if(status == MessageStatus.OK)status =  MessageStatus.READ_QUORUM_NOT_REACHED;
					sendFailureMessage(status, bucketName, key);
				} else {
					System.out.println("Read Primary not quorate " + server.getCluster().thisNodeName() + " requesting version " + currentVersion.toString());
					fetchDocFromOtherNode(currentVersion);
				}
			}
		} catch (IOException e) {
			sendFailureMessage(MessageStatus.SERVER_EXCEPTION, bucketName, key);
		}

	}
	/**
	 * Find the first node that stores the given version and ask that node for it.
	 * @param currentVersion
	 * @throws IOException 
	 */
	private void fetchDocFromOtherNode(Version currentVersion) throws IOException {
		for(ResponseInfo info : sentVersion.values()) {
			if(info.version != null && info.version.equals(currentVersion)) {
				ClusterNode node = info.node;
				fetchCorrelationId = server.nextCorrelationId();
				ServerReadMessage msg = new ServerReadMessage(fetchCorrelationId, bucketName, key);
				server.addTask(fetchCorrelationId, this);
				node.send(msg);
				break;
			}
		}
	}

	/**
	 * Sends the given document back to the client.
	 * @param doc
	 * @throws IOException 
	 */
	private void returnDocumentToClient(Document doc) throws IOException {
		if(complete) return;
		complete = true;
		System.out.println("ReadRecordTask: Returning doc to client, version " + doc.getVersion() + " using correlationId " + clientCorrelationId);
		ReadMessageResponse clientResponse = new ReadMessageResponse(clientCorrelationId, MessageStatus.OK, bucketName, key, 
				doc.getVersion(), doc.getPayload());
		returnChannel.write(clientResponse.getBuffer());
	}

	

	/**
	 * Used to track the version responses from the different nodes.
	 * @author rbp28668
	 *
	 */
	private static class ResponseInfo {
		ClusterNode node;
		Version version;
		MessageStatus status;
		
		ResponseInfo(ClusterNode node){
			this.node = node;
			this.version = null;
		}
	}
}
