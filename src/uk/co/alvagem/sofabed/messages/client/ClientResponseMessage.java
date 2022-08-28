package uk.co.alvagem.sofabed.messages.client;

import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.MessageStatus;

/**
 * Client response message that includes the message status.
 * @author rbp28668
 *
 */
public abstract class ClientResponseMessage extends ClientMessage {

	private MessageStatus status;
	
	public ClientResponseMessage() {
		super();
	}

	public ClientResponseMessage(ByteBuffer buffer) {
		super(buffer);
		buffer.position(super.baseLength());
		status =  MessageStatus.get(buffer);
	}

	protected void setBuffer(short messageType, int len, long correlationId, MessageStatus status) {
		super.setBuffer(messageType, len, correlationId);
		int offset = super.baseLength();
		offset = status.write(buffer, offset);
	}

	public MessageStatus getStatus() {
		return status;		
	}

	protected int baseLength() {
		return super.baseLength() + MessageStatus.BYTES;
	}

}
