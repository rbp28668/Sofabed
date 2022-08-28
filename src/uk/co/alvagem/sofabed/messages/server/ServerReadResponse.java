package uk.co.alvagem.sofabed.messages.server;

import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.MessageType;
import uk.co.alvagem.sofabed.Version;

public class ServerReadResponse extends ServerMessage {

	private Version version;
	private byte[] payload;
	
	public ServerReadResponse(long correlationId, Version version, byte[] payload) {
		int len = super.baseLength() ;
		len += Version.BYTES; // version
		len += payloadBytesLength(payload);
		
		setBuffer(MessageType.SVR_READ_RESPONSE.getCode(), len, correlationId);
		
		int offset = super.baseLength();
		offset = version.write(buffer, offset);
		offset = writePayloadBytes(buffer, offset, payload);
	}
	
	public ServerReadResponse(ByteBuffer buffer) {
		super(buffer);

		if (getType() != MessageType.SVR_READ_RESPONSE.getCode()) {
			throw new IllegalArgumentException("Message is not a read response message");
		}

		buffer.position(super.baseLength()); // skip over message type and length;
		version = new Version(buffer);
		payload = getPayloadBytes(buffer);
	}

	public Version getVersion() {
		return version;
	}

	public byte[] getPayload() {
		return payload;
	}
	
	
}
