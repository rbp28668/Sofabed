package uk.co.alvagem.sofabed.messages.client;

import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.Message;

/**
 * Base class for all messages between client and server.
 * Really just a marker class.
 * @author rbp28668
 *
 */
public abstract class ClientMessage extends Message {

	private long correlationId;

	public ClientMessage() {
		
	}
	
	public ClientMessage(ByteBuffer buffer) {
		super(buffer);
	}
}
