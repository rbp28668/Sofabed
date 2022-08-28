package uk.co.alvagem.sofabed.messages.server;

import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.Message;

/**
 * Server to server message.  Really just a marker class as doesn't add functionality.
 * @author rbp28668
 *
 */
public abstract class ServerMessage extends Message {

	protected ServerMessage(ByteBuffer buffer){
		super(buffer);
		
	}
	
	protected ServerMessage() {
	}
	

}
