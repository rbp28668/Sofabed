package uk.co.alvagem.sofabed;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

/**
 * Base class of all messages. Defines the common structure of a message.
 * 2 bytes of type.
 * 4 bytes of overall message length
 * 8 bytes of correlation ID.
 * @author rbp28668
 *
 */
public class Message {
	protected ByteBuffer buffer;

	/**
	 * Creates an empty message prior to calling setBuffer().
	 */
	protected Message() {
	}

	/**
	 * Creates a message from a received buffer.  Ensures the buffer is set for reading.
	 * @param buffer
	 */
	protected Message(ByteBuffer buffer) {
		if(buffer.position() != 0) {
			buffer.flip();
		}
		this.buffer = buffer;
	}

	
	/**
	 * Initialises a message for transmission
	 * @param messageType
	 * @param len
	 * @param correlationId
	 */
	protected void setBuffer(short messageType, int len, long correlationId) {
		this.buffer = ByteBuffer.allocate(len);;
		buffer.putShort(0, messageType);
		buffer.putInt(Short.BYTES, len);
		buffer.putLong(Short.BYTES + Integer.BYTES, correlationId);
	}

	public short getType() {
		return buffer.getShort(0);
	}

	public int length() {
		return buffer.getInt(Short.BYTES);
	}

	public long getCorrelationId() {
		return buffer.getLong(Short.BYTES + Integer.BYTES);
	}
	
//	protected void setType(short type) {
//		buffer.putShort(0,type);
//	}
//	
//	protected void setLength(int length) {
//		buffer.putInt(2,length);
//	}

//	protected void setCorrelationId(long correlationId) {
//		buffer.setLong(Short.BYTES + Integer.BYTES, correlationId);
//	}

	protected int baseLength() {
		return Short.BYTES +    // Message type
				Integer.BYTES + // total length of message (required buffer size)
				Long.BYTES;     // Correlation ID
	}
	
	public ByteBuffer getBuffer() {
		return buffer;
	}
	
	protected String getString(ByteBuffer buffer) throws UnsupportedEncodingException {
		int len = buffer.getShort();
		byte[] bytes = new byte[len];
		buffer.get(bytes, 0, len);
		return new String(bytes, "UTF-8");
	}
	
	protected int stringBytesLength(byte[] bytes) {
		return Short.BYTES + bytes.length;
	}
	
	protected int writeStringBytes(ByteBuffer buffer, int offset, byte[] bytes) {
		buffer.putShort(offset, (short) bytes.length);
		offset += Short.BYTES;
		buffer.put(offset, bytes);
		offset += bytes.length;
		return offset;
	}

	protected int payloadBytesLength(byte[] payload) {
		return Integer.BYTES + payload.length;
	}
	
	protected byte[] getPayloadBytes(ByteBuffer buffer) {
		int payloadLength = buffer.getInt();
		byte[] payload = new byte[payloadLength];
		buffer.get(payload, 0, payloadLength);
		return payload;
	}

	protected int writePayloadBytes(ByteBuffer buffer, int offset, byte[] bytes) {
		buffer.putInt(offset, bytes.length);
		offset += Integer.BYTES;
		buffer.put(offset, bytes);
		offset += bytes.length;
		return offset;
	}

}