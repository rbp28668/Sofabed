package uk.co.alvagem.sofabed.messages.server;

import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.MessageStatus;
import uk.co.alvagem.sofabed.MessageType;
import uk.co.alvagem.sofabed.Version;

/**
 * Message to return the version of a given record.
 * @author rbp28668
 *
 */
public class ServerReadVersionResponse extends ServerMessage {

	private Version version;
	private MessageStatus status;
	
	public ServerReadVersionResponse(long correlationId, MessageStatus status, Version version) {
		int len = super.baseLength();
		len += MessageStatus.BYTES;
		len += Version.BYTES; // version

		setBuffer(MessageType.SVR_VERSION_RESPONSE.getCode(), len, correlationId);
		
		int offset = super.baseLength();
		offset = status.write(buffer, offset);
		offset = version.write(buffer, offset);
	}

	public ServerReadVersionResponse(ByteBuffer buffer)  {
		super(buffer);

		if (getType() != MessageType.SVR_VERSION_RESPONSE.getCode()) {
			throw new IllegalArgumentException("Message is not a server version response message");
		}

		buffer.position(super.baseLength()); // skip over message type and length;
		status = MessageStatus.get(buffer);
		version = new Version(buffer);
	}

	public Version getVersion() {
		return version;
	}

	public MessageStatus getStatus() {
		return status;
	}

	
}
