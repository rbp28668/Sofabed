package uk.co.alvagem.sofabed;

import java.nio.ByteBuffer;

/**
 * Class to encapsulate the version of a record.
 * @author rbp28668
 *
 */
public class Version {

	public static final int BYTES = Long.BYTES;
	public static final Version NONE = new Version(-1);
	
	private long value;
	
	/**
	 * Creates an zero version.
	 */
	public Version() {
		this.value = 0;
	}
	
	/**
	 * Initialises a version from a long.
	 * @param version
	 */
	public Version(long version) {
		this.value = version;
	}

	/**
	 * Initialises a version from the current position in a ByteBuffer.
	 * @param buffer
	 */
	public Version(ByteBuffer buffer) {
		this.value = buffer.getLong();
	}
	
	/**
	 * Initialises a version from the given position in a ByteBuffer.
	 * @param buffer
	 * @param offset
	 */
	public Version(ByteBuffer buffer, int offset) {
		this.value = buffer.getLong(offset);
	}
	
	/**
	 * Writes the version into the current position of a ByteBuffer.
	 * @param buffer
	 */
	public void write(ByteBuffer buffer) {
		buffer.putLong(value);
	}
	
	/**
	 * Writes the version into the given position of a ByteBuffer.
	 * @param buffer
	 * @param offset
	 * @return the position immediately after the version in the buffer.
	 */
	public int write(ByteBuffer buffer, int offset) {
		buffer.putLong(offset, value);
		return offset + BYTES;
	}
	
	/**
	 * Gets the version as a long value.
	 * @return
	 */
	public long asLong() {
		return value;
	}

	/**
	 * Creates a new Version which is the next in sequence.
	 * @return
	 */
	public Version next() {
		return new Version(value + 1);
	}
	
	@Override
	public boolean equals(Object other) {
		if(this == other) return true; // same object
		if(other == null) return false; 
		if(! (other instanceof Version)) return false;
		return value == ((Version)other).value;
	}
	
	@Override
	public String toString() {
		return Long.toString(value);
	}
}
