package uk.co.alvagem.sofabed;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import uk.co.alvagem.sofabed.messages.client.CreateMessage;
import uk.co.alvagem.sofabed.messages.client.CreateMessageResponse;
import uk.co.alvagem.sofabed.messages.server.ServerWriteMessage;
import uk.co.alvagem.sofabed.messages.server.ServerWriteMessageResponse;
import uk.co.alvagem.sofabed.messages.server.ServerMessage;

/**
 * Manage the create record operation. This has to use the task framework as it
 * involves sending messages to other nodes and waiting for their responses.
 * 
 * @author rbp28668
 *
 */
public class CreateRecordTask extends TaskBase implements Task {

	private String bucketName;
	private Key key;
	private byte[] payload;
	private Version newVersion;
	private Map<Long, MessageStatus> sent = new HashMap<>(); // Track all messages sent by correlation ID.
	private long clientCorrelationId;
	private AtomicInteger successCount = new AtomicInteger(0); // Count of number of records successfully written.
	boolean responseSent = false;

	CreateRecordTask(Server server, CreateMessage create, ClientMessageProcessor returnChannel) throws IOException {
		super(server, returnChannel);
		this.bucketName = create.getBucket().toLowerCase();
		this.key = create.getKey();
		this.payload = create.getPayload();
		this.clientCorrelationId = create.getCorrelationId();
		createDocument();
	}

	private void createDocument() throws IOException {

		try {
			Settings settings = server.getSettings();

			Bucket bucket = server.getBucket(bucketName);

			// Initial version for the document.
			newVersion = new Version();

			ArrayList<ClusterNode> targetNodes = getAliveTargetNodes(bucketName, key);

			// If we haven't reached a quorum at this point then forget it. Note that the
			// writes aren't
			// guaranteed at this point but it's likely they will succeed.
			if (targetNodes.size() < settings.getQuorumCount()) {
				sendFailureMessage(MessageStatus.INSUFFICIENT_NODES, bucketName, key);
				return;
			}

			// Get all the correlation IDs ahead of time to avoid race conditions.
			for (ClusterNode node : targetNodes) {
				if (!node.isLocalNode()) {
					long id = server.nextCorrelationId();
					sent.put(id, null); // before we send so no race condition
				}
			}
			Long[] ids = sent.keySet().toArray(new Long[sent.keySet().size()]);

			// Send messages to other servers and store locally if the correct node.
			int idx = 0;
			for (ClusterNode node : targetNodes) {
				if (node.isLocalNode()) {
					if (bucket.containsKey(key)) {
						sendFailureMessage(MessageStatus.DUPLICATE_KEY, bucketName, key);
					}
					System.out.println("Creating doc " + key + " on " + node.getNodeName());
					Document doc = new Document(key, newVersion, payload);
					bucket.writeDocument(doc);
					successCount.incrementAndGet();
				} else {
					sendCreateToNode(node, newVersion, ids[idx++]);
				}
			}
			
			// If configured as single node then may be time to send response.
			sendResponseWhenQuorate();
			
		} catch (UnsupportedEncodingException e) {
			// Should never happen
			// TODO log ERROR
			e.printStackTrace();
		} catch (BucketException e) {
			// TODO Log missing bucket
			sendFailureMessage(MessageStatus.UNKNOWN_BUCKET, bucketName, key);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * Sends a failure message to client. Sets failed status so client doesn't get any more messages.
	 * @param status
	 * @param bucketName
	 * @param key
	 * @throws IOException
	 */
	protected void sendFailureMessage(MessageStatus status, String bucketName, Key key) throws IOException {
		failed = true; // so abort processing after this
		CreateMessageResponse response = new CreateMessageResponse(clientCorrelationId, status, Version.NONE, bucketName,
				key);
		returnChannel.write(response.getBuffer());
	}

	/**
	 * Sends a server to server create message to the given node.
	 * 
	 * @param node          is the destination node.
	 * @param version       to use for this record.
	 * @param correlationId is the correlation ID for the message to identify the
	 *                      response.
	 * @throws UnsupportedEncodingException
	 */
	private void sendCreateToNode(ClusterNode node, Version version, long correlationId)
			throws UnsupportedEncodingException {
		// Need to get a correlation ID, put it in the outbound message.
		// Register this task against the correlation ID.
		// Then (eventually) process should be called with the response message.
		ServerWriteMessage msg = new ServerWriteMessage(correlationId, bucketName, key, version, payload);
		server.addTask(correlationId, this); // **before ** we actually send the message (race condition).
		node.send(msg);
	}

	@Override
	public void abort(long correlationId) {
		synchronized (sent) {
			sent.put(correlationId, MessageStatus.NO_RESPONSE);
		}
		try {
			checkComplete();
		} catch (IOException e) {
			// Not much we can do here. Just log failure to response to client.
			// TODO - log this
		}
	}

	@Override
	public void process(ServerMessage message) throws IOException {

		if (message.getType() != MessageType.SVR_WRITE_RESPONSE.getCode()) {
			throw new IllegalArgumentException("Unexpected message type in create record task: " + message.getType());
		}

		ServerWriteMessageResponse response = (ServerWriteMessageResponse) message;

		long id = response.getCorrelationId();
		MessageStatus status = response.getStatus();

		if (status == MessageStatus.OK) {
			successCount.incrementAndGet();
		}

		synchronized (sent) {
			if (!sent.containsKey(id)) {
				throw new IllegalArgumentException("Response received for unknown outbound message");
			}

			// If there's already a message status for this ID then we've got a duplicate...
			MessageStatus existing = sent.get(id);
			if (existing != null) {
				throw new IllegalArgumentException("Duplicate response received");
			}

			// Record this status for future reference against the original correlation ID.
			sent.put(id, status);
		}

		sendResponseWhenQuorate();
		checkComplete();
	}

	private void sendResponseWhenQuorate() throws IOException {
		if(failed) {
			return; // failure response already sent
		}
		
		// If we get here then must have all responses and the count of successful
		// updates is in successCount;
		Settings settings = server.getSettings();

		// Enough to be a successful write? Send response message back to client as
		// appropriate
		synchronized (this) {
			if (successCount.get() >= settings.getQuorumCount() && !responseSent) {
				CreateMessageResponse clientResponse = new CreateMessageResponse(clientCorrelationId, MessageStatus.OK,
						newVersion, bucketName, key);
				returnChannel.write(clientResponse.getBuffer());
				responseSent = true;
			}
		}
	}

	private void checkComplete() throws IOException {

		// check all the messages have replies or have been aborted. Any null value
		// indicates that a response wasn't received for that message.
		synchronized (sent) {
			for (MessageStatus status : sent.values()) {
				if (status == null) {
					return;
				}
			}
		}

		// So if we're complete and haven't sent a response then failed.  If we'd reached
		// enough nodes we'd have responded.
		if (!responseSent && !failed) {
			sendFailureMessage(MessageStatus.INSUFFICIENT_NODES, bucketName, key);
		}

		// Now check to see if we've managed all replicas. If not mark the record.
		Settings settings = server.getSettings();
		try {
			// If we didn't have a complete set of docs written will need to recover the
			// cluster so mark for re-replication.
			if (successCount.get() < settings.getReplicaCount()) {
				Bucket bucket = server.getBucket(bucketName);
				bucket.markRecordForReplication(key);
			}
		} catch (BucketException e) {
			// Should only happen if the record shouldn't be stored on this node.
			// Log warning and otherwise ignore.
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
