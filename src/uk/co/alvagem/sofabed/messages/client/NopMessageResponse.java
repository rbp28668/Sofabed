package uk.co.alvagem.sofabed.messages.client;

import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.MessageStatus;
import uk.co.alvagem.sofabed.MessageType;

public class NopMessageResponse extends ClientResponseMessage {

	/**
	 * Constructor for when a messages is received.
	 * @param buffer
	 */
	public NopMessageResponse(ByteBuffer buffer) {
		super(buffer);
	}

	/**
	 * Constructor to create the message to send.
	 */
	public NopMessageResponse(long correlationId) {
		int len = super.baseLength();
		super.setBuffer(MessageType.NOP_MSG_RESPONSE.getCode(), len, correlationId, MessageStatus.OK);
	}

}
