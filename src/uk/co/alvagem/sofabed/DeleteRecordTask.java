package uk.co.alvagem.sofabed;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import uk.co.alvagem.sofabed.messages.client.DeleteMessage;
import uk.co.alvagem.sofabed.messages.client.DeleteMessageResponse;
import uk.co.alvagem.sofabed.messages.server.ServerDeleteMessage;
import uk.co.alvagem.sofabed.messages.server.ServerDeleteResponse;
import uk.co.alvagem.sofabed.messages.server.ServerMessage;
import uk.co.alvagem.sofabed.messages.server.ServerReadVersionMessage;
import uk.co.alvagem.sofabed.messages.server.ServerReadVersionResponse;

/**
 * Server side task to read a record. To ensure quorum it's necessary to send
 * version messages to the other nodes. In normal operation a. there'll be a
 * quorum and b. this node will have the right version. If not.... have to sort
 * out the resulting mess.
 * 
 * @author rbp28668
 *
 */
public class DeleteRecordTask extends TaskBase implements Task {

	// Data received from client's inbound message.
	private String bucketName;
	private Key key;
	private Version versionFromClient;
	private long clientCorrelationId;

	private ArrayList<ClusterNode> targetNodes; // Nodes to talk to.
	private Map<Long, ResponseInfo> sentVersion = new HashMap<>(); // track received version info
	private Map<Long, ResponseInfo> sentWrite = new HashMap<>(); // track writes
	private boolean complete = false; // set true if complete (sent some form of response to client).
	private boolean versionConfirmed = false;
	private Version nextVersion; // version to return to client
	private int savedRecordCount = 0;
	
	public DeleteRecordTask(Server server, DeleteMessage msg, ClientMessageProcessor processor) throws IOException {
		super(server, processor);
		this.bucketName = msg.getBucket().toLowerCase();
		this.key = msg.getKey();
		this.versionFromClient = msg.getVersion();
		this.clientCorrelationId = msg.getCorrelationId();

		System.out.println("Starting delete record task deleting version " + versionFromClient + " of record " + this.key);
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
				System.out.println("Send version message to " + entry.getValue().node.getNodeName() + " correlationId " + correlationId);
			}

			checkVersionsComplete(); // If quorum is 1 (debug/dev mode) may be able to send now.

		} catch (BucketException e) {
			sendFailureMessage(e.getStatus(), bucketName, key);
		} catch (IOException e) {
			throw e;
		}

	}

	/**
	 * Tries to send a failure message back to the client. Last chance here so if
	 * this fails an error is logged.
	 * 
	 * @param status
	 * @param bucketName
	 * @param key
	 */
	protected void sendFailureMessage(MessageStatus status, String bucketName, Key key) {

		if (complete)
			return; // already sent

		complete = failed = true; // so abort processing after this
		DeleteMessageResponse response = new DeleteMessageResponse(clientCorrelationId, status);
		returnChannel.write(response.getBuffer());
	}

	@Override
	public void abort(long correlationId) {

		// Aborts can arrive well after normal processing is complete
		// So try both maps to see if response is there.
		ResponseInfo info = sentVersion.get(correlationId);
		if (info == null)
			info = sentWrite.get(correlationId);
		info.status = MessageStatus.NO_RESPONSE;
		info.version = Version.NONE;

		if (versionConfirmed) {
			checkComplete();
		} else {
			checkVersionsComplete();
		}
	}

	@Override
	public void process(ServerMessage message) throws IOException {

		// May have already managed to send a response if quorum reached earlier
		if (complete) {
			return;
		}

		if (message.getType() == MessageType.SVR_VERSION_RESPONSE.getCode()) {
			ServerReadVersionResponse response = (ServerReadVersionResponse) message;

			// Record the version response.
			ResponseInfo info = sentVersion.get(message.getCorrelationId());
			info.version = response.getVersion();
			info.status = response.getStatus();

			System.out.println("Received version response " + info.version + " status " + info.status.toString() + " correlationId " + message.getCorrelationId());
			// And see if we've got a quorum
			checkVersionsComplete();

		} else if (message.getType() == MessageType.SVR_DELETE_RESPONSE.getCode()) {
			// just received the write response from the other node
			ServerDeleteResponse response = (ServerDeleteResponse) message;
			System.out.println("Received delete response " );

			// Record the version response.
			ResponseInfo info = sentWrite.get(message.getCorrelationId());
			MessageStatus status = response.getStatus();
			info.status = status;
			if(status == MessageStatus.OK) {
				++savedRecordCount; 
			}
			
			checkComplete();
		}
	}

	/**
	 * Check to see if we've got enough version information to identify the current version of the
	 * record.  This must match the version of the incoming record from the client, otherwise there's 
	 * a version mismatch.
	 */
	private void checkVersionsComplete() {
		if(versionConfirmed || complete) {
			return;
		}
		
		
		// So have we got a quorum of versions?
		int quorumCount = server.getSettings().getQuorumCount();

		MessageStatus status = MessageStatus.OK;
		int matches = 0;
		Version currentVersion = Version.NONE;
		int toReceive = 0;

		// In try/catch as this node might not actually have the record.  In which case
		// trying to access it will throw a BucketException.
		try {
			Bucket bucket = server.getBucket(bucketName);
			Document doc = bucket.getDocument(key);

			currentVersion = doc.getVersion();
			
			// Normal case - This node is the current version so just confirm.
			matches = 1; // Count this node - matches itself.
			toReceive = 0;
			for (ResponseInfo info : sentVersion.values()) {
				if (info.status != null) { // received a message
					if (info.version.equals(currentVersion) && info.status == MessageStatus.OK) {
						++matches;
					}
				} else { // not received a response (status in null)
					++toReceive;
				}
			}
		} catch (BucketException e) {
			status = e.getStatus();
		}
		
		
		// If we've found enough equal versions to validate this node's document
		// then we can just return the response tot he client and mark the
		// task as complete (so that we can ignore any subsequent messages).
		if (matches >= quorumCount) {
			System.out.println("Current version matches " + currentVersion.toString());
			checkVersionAndWrite(currentVersion);
		} else if (toReceive == 0) {

			// This isn't good as we've received all the responses and this node doesn't
			// agree with enough of the others to reach quorum. It's possible then that 
			// there's been a failure in the past and the other nodes have the quorum 
			// with this one being out of step.
			// So look through the version info received from the other nodes. Don't fold in
			// this node's version as it's already determined that this node's version
			// doesn't
			// have a quorum.
			Map<Version, Integer> counts = new HashMap<>();
			for (ResponseInfo info : sentVersion.values()) {
				if (MessageStatus.OK == info.status) {
					Version v = info.version;
					if (counts.containsKey(v)) {
						Integer count = counts.get(v);
						counts.put(v, Integer.valueOf(count.intValue() + 1));
					} else {
						counts.put(v, Integer.valueOf(1));
					}
				}
			}

			// So, did any of the versions reach quorum?
			currentVersion = null;
			for (Map.Entry<Version, Integer> entry : counts.entrySet()) {
				if (entry.getValue().intValue() >= quorumCount) {
					currentVersion = entry.getKey();
					break;
				}
			}

			if (currentVersion == null) {
				// Buggered....so try and return something meaningful.
				if(status == MessageStatus.OK) {
					status = MessageStatus.READ_QUORUM_NOT_REACHED;
				}
				sendFailureMessage(status, bucketName, key);
			} else {
				System.out.println("Current version not local " + currentVersion.toString());
				checkVersionAndWrite(currentVersion);
			}
		}
		

	}

	private void checkVersionAndWrite(Version currentVersion) {
		if(versionFromClient.equals(currentVersion)) {
			nextVersion = currentVersion.next();
			writeRecordToNodes(nextVersion);
		} else {
			// Version mismatch
			sendFailureMessage(MessageStatus.VERSION_MISMATCH, bucketName, key);
		}
	}

	/**
	 * Saves the record to all the nodes (including this one if appropriate) that 
	 * should receive it.
	 * @param newVersion
	 */
	private void writeRecordToNodes(Version newVersion) {
		versionConfirmed = true;

		try {
			// Set up the sentWrite map ahead of time. Once set up the map
			// won't be mutated so then won't need to be synchronised.
			for (ClusterNode node : targetNodes) {
				if (node.isLocalNode()) {
					Bucket bucket = server.getBucket(bucketName);
					bucket.deleteDocument(key);
					++savedRecordCount;
				} else { // set up remote node.
					long cid = server.nextCorrelationId();
					ResponseInfo info = new ResponseInfo(node);
					sentWrite.put(cid, info);
				}
			}

			// Send messages to other servers to get version information.
			for (Map.Entry<Long, ResponseInfo> entry : sentWrite.entrySet()) {
				long correlationId = entry.getKey().longValue();
				ServerDeleteMessage msg = new ServerDeleteMessage(correlationId, bucketName, key);
				server.addTask(correlationId, this); // **before ** we actually send the message (race condition).
				entry.getValue().node.send(msg);
			}
			
		} catch (UnsupportedEncodingException e) {
			// TODO Should never happen so log error
		} catch (BucketException e) {
			sendFailureMessage(e.getStatus(), bucketName, key);
		}

		// If quorum is 1 (debug/dev mode) may be able to send response now 
		// and won't get a response to trigger.
		checkComplete(); 

	}

	private void checkComplete() {
		
		if(complete) {
			return;
		}

		int toReceive = 0;
		for(ResponseInfo info : sentWrite.values()) {
			if(info.status == null) {
				++toReceive;
			}
		}

		if(savedRecordCount >= server.getSettings().getQuorumCount()) {
			System.out.println("Returning version " + nextVersion + " to client");
			returnResponseToClient(nextVersion);
		} else {
			if(toReceive == 0) {  				// Not quorate and no more responses.
				sendFailureMessage(MessageStatus.INSUFFICIENT_NODES, bucketName, key);
			}
		}
	}

	/**
	 * Sends the given document back to the client.
	 * 
	 * @param doc
	 * @throws IOException
	 */
	private void returnResponseToClient(Version newVersion) {
		if(complete) return;
		complete = true;
		DeleteMessageResponse clientResponse = new DeleteMessageResponse(clientCorrelationId, MessageStatus.OK);
		returnChannel.write(clientResponse.getBuffer());
	}

	/**
	 * Used to track the version responses from the different nodes.
	 * 
	 * @author rbp28668
	 *
	 */
	private static class ResponseInfo {
		ClusterNode node;
		Version version;
		MessageStatus status;

		ResponseInfo(ClusterNode node) {
			this.node = node;
			this.version = null;
			this.status = null;
		}
	}
}
