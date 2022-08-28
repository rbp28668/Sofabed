package uk.co.alvagem.sofabed.messages.client;

import java.io.IOException;
import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.Key;
import uk.co.alvagem.sofabed.MessageType;
import uk.co.alvagem.sofabed.Version;

public final class CreateMessage extends ClientMessage {

	private DataPayload payload;

	public CreateMessage(ByteBuffer buffer) throws IOException {
		super(buffer);
		if(getType() != MessageType.CREATE_MSG.getCode()) {
			throw new IllegalArgumentException("Message does not contain an update message");
		}
	
		int offset = super.baseLength();
		payload = new DataPayload(buffer, offset);
	}

	public CreateMessage(long correlationId, String bucket, Key key, Version version, byte[] payload) throws IOException {
		int len = super.baseLength() + DataPayload.bufferSize(bucket, key, version, payload);
		setBuffer(MessageType.CREATE_MSG.getCode(), len, correlationId);
		DataPayload.writeToBuffer(buffer, super.baseLength(), bucket, key, version, payload);
	}

	public Version getVersion() {
		return payload.getVersion();
	}

	public String getBucket() {
		return payload.getBucket();
	}

	public Key getKey() {
		return payload.getKey();
	}

	public byte[] getPayload() {
		return payload.getPayload();
	}

}