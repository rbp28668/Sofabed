package uk.co.alvagem.sofabed.client;

import java.io.IOException;
import java.util.concurrent.Future;

import uk.co.alvagem.sofabed.Key;
import uk.co.alvagem.sofabed.MessageType;
import uk.co.alvagem.sofabed.Version;
import uk.co.alvagem.sofabed.messages.client.ClientMessage;
import uk.co.alvagem.sofabed.messages.client.ClientResponseMessage;
import uk.co.alvagem.sofabed.messages.client.DeleteMessage;

public class DeleteTask extends ClientTaskBase<Void> implements ClientTask, Future<Void> {

	private String bucketName;
	private Key key;
	private Version version;

	DeleteTask(ClusterImpl cluster, String bucketName, Key key, Version version) throws IOException{
		super(cluster, bucketName, key);
		this.bucketName = bucketName;
		this.key = key;
		this.version = version;
		start();
	}
	
	@Override
	protected void sendTo(ClusterNode node) throws IOException {
		ClusterImpl cluster = getCluster();
		long cid = cluster.nextCorrelationId();
		cluster.registerTask(cid, this);
		DeleteMessage message = new DeleteMessage(cid, bucketName, key, version);
		node.write(message.getBuffer());
	}
	
	@Override
	protected Void getResultFrom(ClientResponseMessage message) {
		return null;
	}

	@Override
	protected void validateMessage(ClientMessage message) throws IllegalArgumentException {
		if(message.getType() != MessageType.DELETE_MSG_RESPONSE.getCode()) {
			throw new IllegalArgumentException("Invalid message type - expected a delete message response");
		}
	}

}
