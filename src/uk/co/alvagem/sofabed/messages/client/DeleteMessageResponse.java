package uk.co.alvagem.sofabed.messages.client;

import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.MessageStatus;
import uk.co.alvagem.sofabed.MessageType;

public class DeleteMessageResponse extends ClientResponseMessage {

	public DeleteMessageResponse(ByteBuffer buffer) {
		super(buffer);
	}

	
	public DeleteMessageResponse(long correlationId, MessageStatus status) {
		super();
		int len = super.baseLength();
		setBuffer(MessageType.DELETE_MSG_RESPONSE.getCode(), len, correlationId, status);
	}

}
