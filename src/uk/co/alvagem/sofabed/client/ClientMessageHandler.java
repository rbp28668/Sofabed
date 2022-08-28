package uk.co.alvagem.sofabed.client;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Client side analogue of the server side  MessageHandler but one that doesn't reference the server.   
 * TODO - refactor into a common base class
 * @author rbp28668
 *
 */
public abstract class ClientMessageHandler {

	// Gets the type for this message.
	public abstract short getType();

	// True IFF the buffer holds a complete message.
	public boolean isComplete(ByteBuffer buffer) {
		if (buffer.position() < 6) {
			return false;
		}

		int len = buffer.getInt(2);

		return buffer.position() >= len;
	}

	// Gets the length of this message.
	public int messageLength(ByteBuffer buffer) {
		return buffer.getInt(2);
	}

	/**
	 * Process the complete message.
	 * @param server is the server - usually will end up as the destination of the message.
	 * @param buffer is the buffer holding the complete message.
	 * @param processor is the MessageProcessor that originated this message and should therefore
	 * have the connection to be used to send any response.
	 * @throws IOException
	 */
	public abstract void process( ByteBuffer buffer, Connection connection) throws IOException;

}