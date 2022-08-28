package uk.co.alvagem.sofabed.messages.client;

import java.io.IOException;
import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.Key;
import uk.co.alvagem.sofabed.MessageStatus;
import uk.co.alvagem.sofabed.MessageType;
import uk.co.alvagem.sofabed.Version;

public class ReadMessageResponse extends ClientResponseMessage {
	
	private DataPayload payload;
	
	public ReadMessageResponse(ByteBuffer buffer) throws IOException {
		super(buffer);
		if(getType() != MessageType.READ_MSG_RESPONSE.getCode()) {
			throw new IllegalArgumentException("Message does not contain read message response message");
		}
	
		int offset = super.baseLength();
		payload = new DataPayload(buffer, offset);
	}

	public ReadMessageResponse(long correlationId, MessageStatus status, String bucket, Key key, Version version, byte[] payload) throws IOException {
		int len = super.baseLength() + DataPayload.bufferSize(bucket, key, version, payload);
		setBuffer(MessageType.READ_MSG_RESPONSE.getCode(), len, correlationId, status);
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
