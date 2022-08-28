package uk.co.alvagem.sofabed.messages.client;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.Key;
import uk.co.alvagem.sofabed.MessageType;
import uk.co.alvagem.sofabed.Version;

public class DeleteMessage extends ClientMessage {

	private Version version;
	private String bucket;
	private Key key;

	public DeleteMessage(ByteBuffer buffer) throws UnsupportedEncodingException {
		super(buffer);

		if (getType() != MessageType.DELETE_MSG.getCode()) {
			throw new IllegalArgumentException("Message is not a delete message");
		}

		buffer.position(super.baseLength()); // skip over message type and length;
	
		bucket = getString(buffer);
		key = new Key(buffer);
		version = new Version(buffer);
		
	}
	
	public DeleteMessage(long correlationId, String bucketName, Key key, Version version) throws UnsupportedEncodingException {
		byte[] bucketBytes = bucketName.getBytes("UTF-8");
		byte[] keyBytes = key.asBytes();

		int len = super.baseLength();
		len += stringBytesLength(bucketBytes);
		len += Key.bytesLength(keyBytes);
		len += Version.BYTES; // version

		setBuffer(MessageType.DELETE_MSG.getCode(), len, correlationId);

		int offset = super.baseLength();
		offset = writeStringBytes(buffer, offset, bucketBytes);
		offset = key.write(buffer, offset);
		offset = version.write(buffer, offset);
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
