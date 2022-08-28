package uk.co.alvagem.sofabed.messages.client;

import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.MessageStatus;
import uk.co.alvagem.sofabed.MessageType;

public class VersionMessageResponse extends ClientResponseMessage {

	private short[] version = new short[3];
	
	/**
	 * Constructor for when a messages is received.
	 * @param buffer
	 */
	public VersionMessageResponse(ByteBuffer buffer) {
		super(buffer);
		if(getType() != MessageType.VERSION_MSG_RESPONSE.getCode()) {
			throw new IllegalArgumentException("Message is not a Version Response Message");
		}
		
		int offset = baseLength();
		for(int i=0; i<version.length; ++i) {
			version[i] = buffer.getShort(offset);
			offset += Short.BYTES;
		}
	}

	/**
	 * Constructor to create the message to send.
	 */
	public VersionMessageResponse(long correlationId, short major, short minor, short patch) {
		int len = super.baseLength();
		len += Short.BYTES * version.length;
		
		super.setBuffer(MessageType.VERSION_MSG_RESPONSE.getCode(), len, correlationId, MessageStatus.OK);
		
		int offset = super.baseLength();
		buffer.putShort(offset, major);
		offset += Short.BYTES;
		buffer.putShort(offset, minor);
		offset += Short.BYTES;
		buffer.putShort(offset, patch);
		offset += Short.BYTES;
	}

	public short getMajor() {
		return version[0];
	}
	
	public short getMinor() {
		return version[1];
	}
	
	public short getPatch() {
		return version[2];
	}
	
	
}
