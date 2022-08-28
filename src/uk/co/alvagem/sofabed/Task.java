package uk.co.alvagem.sofabed;

import java.io.IOException;

import uk.co.alvagem.sofabed.messages.server.ServerMessage;

public interface Task {

	void abort(long correlationId);

	void process(ServerMessage message) throws IOException;
}
