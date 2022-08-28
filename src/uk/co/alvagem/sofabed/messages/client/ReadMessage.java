package uk.co.alvagem.sofabed.messages.client;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.Key;
import uk.co.alvagem.sofabed.MessageType;

/**
 * Request to read a document.
 * @author rbp28668
 *
 */
public class ReadMessage extends ClientMessage {

	private Key key;
	private String bucket;
	
	public ReadMessage(ByteBuffer buffer) throws UnsupportedEncodingException{
		super(buffer);
		parseBuffer(buffer);
	}
	
	public ReadMessage(long correlationId, String bucket, Key key) throws UnsupportedEncodingException {
		createBuffer(correlationId, bucket, key);
	}
	
	protected void parseBuffer(ByteBuffer buffer) throws UnsupportedEncodingException {
		
		buffer.position(super.baseLength()) ; // skip over message type and length;
		bucket = getString(buffer);
		key = new Key(buffer);
	}


	protected void createBuffer(long correlationId, String bucket, Key key)
			throws UnsupportedEncodingException {
		
		byte[] bucketBytes = bucket.getBytes("UTF-8");
		byte[] keyBytes = key.asBytes();

		int len = super.baseLength();
		len += stringBytesLength(bucketBytes);
		len += Key.bytesLength(keyBytes);

		setBuffer(MessageType.READ_MSG.getCode(), len, correlationId);

		int offset = super.baseLength();
		offset = writeStringBytes(buffer, offset, bucketBytes);
		offset = key.write(buffer, offset);
	}

	public String getBucket() {
		return bucket;
	}

	public Key getKey() {
		return key;
	}

}
