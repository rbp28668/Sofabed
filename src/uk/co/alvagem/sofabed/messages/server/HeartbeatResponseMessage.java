package uk.co.alvagem.sofabed.messages.server;

import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.MessageType;

public class HeartbeatResponseMessage extends ServerMessage {

	private int nodeId;
	
	/**
	 * Create a message from a received buffer
	 * @param buffer
	 */
	public HeartbeatResponseMessage(ByteBuffer buffer) {
		super(buffer);
		
		if (getType() != MessageType.SVR_HEARTBEAT_RESPONSE.getCode()) {
			throw new IllegalArgumentException("Message is not a Heartbeat response message");
		}
		
		int offset = super.baseLength();
		nodeId = buffer.getInt(offset);
		offset += Integer.BYTES;
	}

	/**
	 * Build a message to transmit
	 * @param nodeId
	 */
	public HeartbeatResponseMessage(int nodeId,long correlationId){
		int len = super.baseLength();
		len += Integer.BYTES; // node ID

		setBuffer(MessageType.SVR_HEARTBEAT_RESPONSE.getCode(), len, correlationId);
		
		int offset = super.baseLength();
		buffer.putInt(offset, nodeId);
		offset += Integer.BYTES;
		
		assert(offset == len);
	}

	/**
	 * Gets the node Id of this response message. It should be the node ID of the
	 * replying node.
	 * @return integer node ID.
	 */
	public int getNodeId() {
		return nodeId;
	}
}
