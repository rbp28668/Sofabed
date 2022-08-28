package uk.co.alvagem.sofabed.messages.server;

import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.MessageType;

/**
 * Ask a node to send the details of all the record the sending node should have (that it knows about).
 * @author rbp28668
 *
 */
public class ServerRecoveryMessage extends ServerMessage {

	private int nodeId;
	
	public ServerRecoveryMessage(ByteBuffer buffer) {
		super(buffer);
		if (getType() != MessageType.SVR_RECOVERY_MSG.getCode()) {
			throw new IllegalArgumentException("Message is not a recovery message");
		}
		
		int offset = super.baseLength();
		nodeId = buffer.getInt(offset);
		offset += Integer.BYTES;
	}

	public ServerRecoveryMessage(int nodeId, long correlationId){
		int len = super.baseLength();
		len += Integer.BYTES; // nodeId;

		setBuffer(MessageType.SVR_RECOVERY_MSG.getCode(), len, correlationId);
		
		int offset = super.baseLength();
		buffer.putInt(offset, nodeId);
		offset += Integer.BYTES;
		
		assert(offset == len);
	}

	public int getNodeId() {
		return nodeId;
	}

	
	
}
