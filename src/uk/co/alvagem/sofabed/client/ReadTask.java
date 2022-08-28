package uk.co.alvagem.sofabed.client;

import java.io.IOException;
import java.util.concurrent.Future;

import uk.co.alvagem.sofabed.Key;
import uk.co.alvagem.sofabed.MessageType;
import uk.co.alvagem.sofabed.messages.client.ClientMessage;
import uk.co.alvagem.sofabed.messages.client.ClientResponseMessage;
import uk.co.alvagem.sofabed.messages.client.ReadMessage;
import uk.co.alvagem.sofabed.messages.client.ReadMessageResponse;

/**
 * Task to read a record from the server.
 * @author rbp28668
 *
 */
class ReadTask extends ClientTaskBase<Record> implements ClientTask, Future<Record> {
	private String bucketName;
	private Key key;
	
	ReadTask(ClusterImpl cluster, String bucketName, Key key ) throws IOException{
		super(cluster, bucketName, key);
		this.bucketName = bucketName;
		this.key = key;
		start();
	}
	
	@Override
	protected void sendTo(ClusterNode node) throws IOException {
		ClusterImpl cluster = getCluster();
		long cid = cluster.nextCorrelationId();
		cluster.registerTask(cid, this);
		System.out.println("Client ReadTask: Client read request correlationID " + cid + " being sent to " + node.getNodeId());
		ReadMessage message = new ReadMessage(cid, bucketName, key);
		node.write(message.getBuffer());
	}
	
	@Override
	protected Record getResultFrom(ClientResponseMessage message) {
		ReadMessageResponse response = (ReadMessageResponse)message;
		return new Record(response.getKey(), response.getVersion(), response.getPayload());
	}

	@Override
	protected void validateMessage(ClientMessage message) throws IllegalArgumentException {
		if(message.getType() != MessageType.READ_MSG_RESPONSE.getCode()) {
			throw new IllegalArgumentException("Invalid message type - expected a read message response");
		}
	}
	
}
