package uk.co.alvagem.sofabed.messages.server;

import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.MessageType;

public class HeartbeatMessage extends ServerMessage {

	private int nodeId;
	
	/**
	 * Create a message from a received buffer
	 * @param buffer
	 */
	public HeartbeatMessage(ByteBuffer buffer) {
		super(buffer);
		
		if (getType() != MessageType.SVR_HEARTBEAT.getCode()) {
			throw new IllegalArgumentException("Message is not a Heartbeat message");
		}
		
		int offset = super.baseLength();
		nodeId = buffer.getInt(offset);
		offset += Integer.BYTES;
	}

	/**
	 * Build a message to transmit
	 * @param nodeId
	 */
	public HeartbeatMessage(int nodeId){
		int len = super.baseLength();
		len += Integer.BYTES; // nodeId;

		setBuffer(MessageType.SVR_HEARTBEAT.getCode(), len, 0);  // Don't correlate heartbeat messages, 0
		
		int offset = super.baseLength();
		buffer.putInt(offset, nodeId);
		offset += Integer.BYTES;
		
		assert(offset == len);
	}
	
	public int getNodeId() {
		return nodeId;
	}
}
