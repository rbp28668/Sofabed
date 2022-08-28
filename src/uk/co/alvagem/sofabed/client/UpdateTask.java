package uk.co.alvagem.sofabed.client;

import java.io.IOException;
import java.util.concurrent.Future;

import uk.co.alvagem.sofabed.MessageType;
import uk.co.alvagem.sofabed.Version;
import uk.co.alvagem.sofabed.messages.client.ClientMessage;
import uk.co.alvagem.sofabed.messages.client.ClientResponseMessage;
import uk.co.alvagem.sofabed.messages.client.UpdateMessage;
import uk.co.alvagem.sofabed.messages.client.UpdateMessageResponse;

class UpdateTask extends ClientTaskBase<Version> implements ClientTask, Future<Version> {
	private String bucketName;
	private Record record;
	
	UpdateTask(ClusterImpl cluster, String bucketName, Record record ) throws IOException{
		super(cluster, bucketName, record.getKey());
		this.bucketName = bucketName;
		this.record = record;
		start();
	}
	
	@Override
	protected void sendTo(ClusterNode node) throws IOException {
		ClusterImpl cluster = getCluster();
		long cid = cluster.nextCorrelationId();
		cluster.registerTask(cid, this);
		UpdateMessage message = new UpdateMessage(cid, bucketName, record.getKey(), record.getVersion(), record.getPayload());
		node.write(message.getBuffer());
	}
	
	@Override
	protected Version getResultFrom(ClientResponseMessage message) {
		UpdateMessageResponse response = (UpdateMessageResponse)message;
		return response.getVersion();
	}

	@Override
	protected void validateMessage(ClientMessage message) throws IllegalArgumentException {
		if(message.getType() != MessageType.UPDATE_MSG_RESPONSE.getCode()) {
			throw new IllegalArgumentException("Invalid message type - expected an update message response");
		}
	}

}
