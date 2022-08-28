package uk.co.alvagem.sofabed;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Base class for specific message handlers that know when a message is complete and know how to process the message.
 * Typically instances of this class will act as factories for specific message types based on the contents of 
 * the supplied buffer.
 * @author rbp28668
 *
 */
public abstract class MessageHandler {

	// Gets the type for this message.
	abstract short getType();

	// True IFF the buffer holds a complete message.
	public boolean isComplete(ByteBuffer buffer) {
		if (buffer.position() < 6) {
			return false;
		}

		int len = buffer.getInt(2);

		return buffer.position() >= len;
	}

	// Gets the length of this message.
	int messageLength(ByteBuffer buffer) {
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
	 abstract void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException;

}