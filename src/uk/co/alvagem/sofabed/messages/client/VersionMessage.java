package uk.co.alvagem.sofabed.messages.client;

import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.MessageType;

public class VersionMessage extends ClientMessage {

	/**
	 * Constructor for when a messages is received.
	 * @param buffer
	 */
	public VersionMessage(ByteBuffer buffer) {
		super(buffer);
		if(getType() != MessageType.VERSION_MSG.getCode()) {
			throw new IllegalArgumentException("Message is not a Version Message");
		}
	}

	/**
	 * Constructor to create the message to send.
	 */
	public VersionMessage(long correlationId) {
		int len = super.baseLength();
		super.setBuffer(MessageType.VERSION_MSG.getCode(), len, correlationId);
	}
	

}
