package uk.co.alvagem.sofabed;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

/**
 * Wrapper round string class.  Use this for record keys.
 * @author rbp28668
 *
 */
public class Key {

	private String value;
	
	/**
	 * Creates a key from a string.
	 * @param key
	 */
	public Key(String key) {
		value = key;
	}
	
	/**
	 * Creates a key from an array of bytes containing the UTF-8 representation
	 * of a key.
	 * @param bytes
	 * @throws UnsupportedEncodingException
	 */
	public Key(byte[] bytes) throws UnsupportedEncodingException {
		value = new String(bytes, "UTF-8");
	}

	/**
	 * Reads a key from the current position of a buffer.  The position is
	 * updated.
	 * @param buffer
	 * @throws UnsupportedEncodingException
	 */
	public Key(ByteBuffer buffer) throws UnsupportedEncodingException {
		int len = buffer.getShort();
		byte[] bytes = new byte[len];
		buffer.get(bytes, 0, len);
		value = new String(bytes, "UTF-8");
	}

	public static int bytesLength(byte[] bytes) throws UnsupportedEncodingException {
		return Short.BYTES + bytes.length;
	}
	
	/**
	 * Writes a key to the current position of the buffer. The position is updated.
	 * @param buffer
	 * @throws UnsupportedEncodingException
	 */
	public void write(ByteBuffer buffer) throws UnsupportedEncodingException {
		byte[] asBytes = value.getBytes("UTF-8");
		buffer.putShort((short)asBytes.length);
		buffer.put(asBytes);
	}
	
	/**
	 * Writes a key to the given position in a buffer. The buffer position is updated.
	 * @param buffer
	 * @param offset
	 * @return the offset into the byte buffer immediately after the key. Usually this
	 * is where the next element will be written.
	 * @throws UnsupportedEncodingException
	 */
	public int write(ByteBuffer buffer, int offset) throws UnsupportedEncodingException {
		byte[] asBytes = value.getBytes("UTF-8");
		buffer.putShort(offset, (short)asBytes.length);
		offset += Short.BYTES;
		buffer.put(offset, asBytes);
		offset += asBytes.length;
		return offset;
	}
	
	
	/**
	 * Gets the key as a byte array.
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public byte[] asBytes() throws UnsupportedEncodingException {
		return value.getBytes("UTF-8");
	}
	
	public String toString() {
		return value;
	}

	@Override
	public int hashCode() {
		return value.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Key other = (Key) obj;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}
	
	
}
