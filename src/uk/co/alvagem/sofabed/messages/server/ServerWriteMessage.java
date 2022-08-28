package uk.co.alvagem.sofabed.messages.server;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.Key;
import uk.co.alvagem.sofabed.MessageType;
import uk.co.alvagem.sofabed.Version;

public class ServerWriteMessage extends ServerMessage {

	private String bucketName;
	private Key key;
	private Version version;
	private byte[] payload;
	
	public ServerWriteMessage(long correlationId, String bucket, Key key, Version version,  byte[] payload) throws UnsupportedEncodingException{
		byte[] bucketBytes = bucket.getBytes("UTF-8");
		byte[] keyBytes = key.asBytes();

		int len = super.baseLength();
		len += Version.BYTES; // version
		len += stringBytesLength(bucketBytes);
		len += Key.bytesLength(keyBytes);
		len += payloadBytesLength(payload);

		setBuffer(MessageType.SVR_WRITE_MSG.getCode(), len, correlationId);
		
		int offset = super.baseLength();

		offset = version.write(buffer, offset);
		offset = writeStringBytes(buffer, offset, bucketBytes);
		offset = writeStringBytes(buffer, offset, keyBytes);
		offset = writePayloadBytes(buffer, offset, payload);
		
	}

	public ServerWriteMessage(ByteBuffer buffer) throws UnsupportedEncodingException {
		super(buffer);

		if (getType() != MessageType.SVR_WRITE_MSG.getCode()) {
			throw new IllegalArgumentException("Message is not a server write message");
		}

		buffer.position(super.baseLength());
		version = new Version(buffer);
		bucketName = getString(buffer);
		key = new Key(buffer);

		int payloadLength = buffer.getInt();
		payload = new byte[payloadLength];
		buffer.get(payload, 0, payloadLength);
	}

	public String getBucket() {
		return bucketName;
	}

	public Key getKey() {
		return key;
	}

	public byte[] getPayload() {
		return payload;
	}

	public Version getVersion() {
		return version;
	}
	
}
