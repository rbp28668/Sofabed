package uk.co.alvagem.sofabed.messages.server;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.Key;
import uk.co.alvagem.sofabed.MessageType;

public class ServerDeleteMessage extends ServerMessage {

	private String bucketName;
	private Key key;

	public ServerDeleteMessage(ByteBuffer buffer) throws UnsupportedEncodingException {
		super(buffer);
		buffer.position(super.baseLength());// skip over message type and length;
		bucketName = getString(buffer);
		key = new Key(buffer);
	}


	public ServerDeleteMessage(long correlationId, String bucketName, Key key) throws UnsupportedEncodingException {
		byte[] bucketBytes = bucketName.getBytes("UTF-8");
		byte[] keyBytes = key.asBytes();

		int len = super.baseLength();
		len += stringBytesLength(bucketBytes);
		len += Key.bytesLength(keyBytes);

		setBuffer(MessageType.SVR_DELETE_MSG.getCode(), len, correlationId);
		
		int offset = super.baseLength() ;
		offset = writeStringBytes(buffer,offset,bucketBytes);
		offset = key.write(buffer, offset);
	}

	public String getBucketName() {
		return bucketName;
	}

	public Key getKey() {
		return key;
	}

}
