package uk.co.alvagem.sofabed;

import java.nio.ByteBuffer;

/**
 * Message status codes.  These can be mapped to an integer for sending in a message and
 * also contain a flag to say whether to retry the operation and a text description of the status.
 * @author rbp28668
 *
 */
public enum MessageStatus {

	OK (false, "OK"),
	INSUFFICIENT_NODES (true, "Insufficient nodes for write quorum"), // There aren't enough nodes visible to write a quorum of records.
	UNKNOWN_BUCKET(false, "Unknown bucket"), // The bucket name isn't known.
	DUPLICATE_KEY(false, "Duplicate key on create"),     // Key already exists in bucket on create
	KEY_NOT_FOUND(false, "Key not found"), // key not found in bucket when trying to access it
	VERSION_MISMATCH(false, "Version mismatch"),  // Version doesn't match on an update.
	READ_QUORUM_NOT_REACHED(true, "Read quorum not reached"), // Couldn't reach quorum on read.
	NO_RESPONSE(true, "No response received"), 
	DUPLICATE_BUCKET(false, "Duplicate bucket"), 
	SERVER_EXCEPTION(true, "Server exception")
	;
	
	public final static int BYTES = Integer.BYTES;
	
	private int code;
	private boolean recoverable;
	private String text;
	
	static {
		int idx = 0;
		for(MessageStatus status : MessageStatus.values()) {
			status.code = idx;
			++idx;
		}
	}
	
	MessageStatus(boolean recoverable, String message){
		this.recoverable = recoverable;
		this.text = message;
	}
	
	/**
	 * Gets a unique integer code for this.
	 * @return
	 */
	public int getCode() {
		return code;
	}
	
	/**
	 * Determine whether this status is recoverable.  Responses with insufficient nodes
	 * may be if the cluster is partitioned and another node may be able to reach quorum.
	 * Generally logic errors such as not finding keys are not.
	 * @return
	 */
	public boolean isRecoverable() {
		return recoverable;
	}
	
	/**
	 * Gets appropriate message text for this status.
	 * @return
	 */
	public String getMessage() {
		return text;
	}
	
	/**
	 * Converts an integer index into a message status.
	 * @param i
	 * @return
	 */
	public static MessageStatus valueOf(int i) {
		MessageStatus[] lookup = MessageStatus.values();
		if(i<0 || i>= lookup.length) {
			throw new IllegalArgumentException("Invalid message status: " + i);
		}
		return lookup[i];
	}
	
	/**
	 * Writes the status into a byte buffer.
	 * @param buffer
	 * @param offset
	 * @return
	 */
	public int write(ByteBuffer buffer, int offset) {
		buffer.putInt(offset, code);
		return offset + BYTES;
	}
	
	/**
	 * Gets a message status from the current position in the supplied ByteBuffer.
	 * @param buffer
	 * @return
	 */
	public static MessageStatus get(ByteBuffer buffer) {
		return valueOf(buffer.getInt());
	}
}
