package uk.co.alvagem.sofabed.messages.server;

import java.io.IOException;
import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.Key;
import uk.co.alvagem.sofabed.MessageType;

/**
 * Ask another server node what the version of a record is.
 * @author rbp28668
 *
 */
public class ServerReadVersionMessage extends ServerMessage {

	private String bucketName;
	private Key key;
	
	public ServerReadVersionMessage(long correlationId, String bucketName, Key key) throws IOException {
		byte[] bucketBytes = bucketName.getBytes("UTF-8");
		byte[] keyBytes = key.asBytes();

		int len = super.baseLength();
		len += stringBytesLength(bucketBytes);
		len += Key.bytesLength(keyBytes);

		setBuffer(MessageType.SVR_VERSION_MSG.getCode(), len, correlationId);
		
		int offset = super.baseLength();
		offset = writeStringBytes(buffer, offset, bucketBytes);
		offset = key.write(buffer, offset);
	}

	public ServerReadVersionMessage(ByteBuffer buffer) throws IOException {
		super(buffer);

		if (getType() != MessageType.SVR_VERSION_MSG.getCode()) {
			throw new IllegalArgumentException("Message is not a read version message");
		}

		buffer.position(super.baseLength()); // skip over message type and length;

		bucketName = getString(buffer);
		key = new Key(buffer);
	}

	public String getBucket() {
		return bucketName;
	}

	public Key getKey() {
		return key;
	}

}
