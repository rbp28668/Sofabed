package uk.co.alvagem.sofabed.messages.server;

import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.MessageStatus;
import uk.co.alvagem.sofabed.MessageType;

public class ServerDeleteResponse extends ServerMessage {

	private MessageStatus status;

	public ServerDeleteResponse(ByteBuffer buffer) {
		super(buffer);
		int offset = super.baseLength();
		status = MessageStatus.valueOf(buffer.getInt(offset));
		offset += MessageStatus.BYTES;
	}

	public ServerDeleteResponse(long correlationId, MessageStatus status) {
		int len = super.baseLength();
		len += MessageStatus.BYTES;
		super.setBuffer(MessageType.SVR_DELETE_RESPONSE.getCode(), len, correlationId);
		
		int offset = super.baseLength();
		buffer.putInt(offset, status.getCode());
		offset += MessageStatus.BYTES;
	}

	public MessageStatus getStatus() {
		return status;
	}
	

}
