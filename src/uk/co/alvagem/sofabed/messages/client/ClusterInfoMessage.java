package uk.co.alvagem.sofabed.messages.client;

import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.MessageType;

public class ClusterInfoMessage extends ClientMessage {

	/**
	 * No parameters needed
	 */
	public ClusterInfoMessage(long correlationId){
		int len = baseLength();
		setBuffer(MessageType.CLUSTER_INFO_MSG.getCode(), len, correlationId);
	}
	
	public ClusterInfoMessage(ByteBuffer buffer){
		super(buffer);
		if(getType() != MessageType.CLUSTER_INFO_MSG.getCode()) {
			throw new IllegalArgumentException("Message is not a Cluster Info Message");
		}
	}
	
}
