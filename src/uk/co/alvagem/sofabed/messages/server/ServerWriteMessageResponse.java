package uk.co.alvagem.sofabed.messages.server;

import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.MessageStatus;
import uk.co.alvagem.sofabed.MessageType;

/**
 * Server to server response to creating a record.
 * @author rbp28668
 *
 */
public class ServerWriteMessageResponse extends ServerMessage {

	private MessageStatus status;
	
	public ServerWriteMessageResponse(ByteBuffer buffer){
		super(buffer);
		int offset = super.baseLength();
		status = MessageStatus.valueOf(buffer.getInt(offset));
		offset += MessageStatus.BYTES;
		
	}
	
	public ServerWriteMessageResponse(long correlationId, MessageStatus status) {
		int len = super.baseLength();
		len += MessageStatus.BYTES;
		super.setBuffer(MessageType.SVR_WRITE_RESPONSE.getCode(), len, correlationId);
		
		int offset = super.baseLength();
		buffer.putInt(offset, status.getCode());
		offset += MessageStatus.BYTES;
	}

	public MessageStatus getStatus() {
		return status;
	}
	
	
}
