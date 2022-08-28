package uk.co.alvagem.sofabed;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

public interface ChannelHandler {
	
	void process(SelectionKey selectionKey) throws IOException;

	void write(ByteBuffer message) throws  IOException;
}
