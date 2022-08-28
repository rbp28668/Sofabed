package uk.co.alvagem.sofabed.messages.client;

import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.MessageType;

public class NopMessage extends ClientMessage {

	/**
	 * Constructor for when a messages is received.
	 * @param buffer
	 */
	public NopMessage(ByteBuffer buffer) {
		super(buffer);
		if(getType() != MessageType.NOP_MSG.getCode()) {
			throw new IllegalArgumentException("Message is not a No Operation Message");
		}
	}

	/**
	 * Constructor to create the message to send.
	 */
	public NopMessage(long correlationId) {
		int len = super.baseLength();
		super.setBuffer(MessageType.NOP_MSG.getCode(), len, correlationId);
	}
	
}
