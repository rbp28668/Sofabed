package uk.co.alvagem.sofabed.client;

import java.io.IOException;

import uk.co.alvagem.sofabed.messages.client.ClientMessage;

public interface ClientTask {

	void abort(long correlationId);

	void process(ClientMessage message) throws IOException;
}
