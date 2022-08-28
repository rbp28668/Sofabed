package uk.co.alvagem.sofabed.messages.client;

import java.io.IOException;
import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.Key;
import uk.co.alvagem.sofabed.MessageStatus;
import uk.co.alvagem.sofabed.MessageType;
import uk.co.alvagem.sofabed.Version;

public class CreateMessageResponse extends ClientResponseMessage {

	private Version version;
	private String bucket;
	private Key key;
	
	
	public CreateMessageResponse(ByteBuffer buffer) throws IOException {
		super(buffer);

		if (getType() != MessageType.CREATE_MSG_RESPONSE.getCode()) {
			throw new IllegalArgumentException("Message is not a create message response");
		}

		buffer.position(super.baseLength()); // skip over message type and length;
	
		version = new Version(buffer);
		bucket = getString(buffer);
		key = new Key(buffer);
	}

	
	public CreateMessageResponse(long correlationId, MessageStatus status, Version version, String bucket, Key key) throws IOException {
		byte[] bucketBytes = bucket.getBytes("UTF-8");
		byte[] keyBytes = key.asBytes();

		int len = super.baseLength();
		len += Version.BYTES; // version
		len += stringBytesLength(bucketBytes);
		len += Key.bytesLength(keyBytes);

		setBuffer(MessageType.CREATE_MSG_RESPONSE.getCode(), len, correlationId, status);

		int offset = super.baseLength();
		offset = version.write(buffer, offset);
		offset = writeStringBytes(buffer, offset, bucketBytes);
		offset = key.write(buffer, offset);
	}


	public Version getVersion() {
		return version;
	}


	public String getBucket() {
		return bucket;
	}


	public Key getKey() {
		return key;
	}
	
	

}
