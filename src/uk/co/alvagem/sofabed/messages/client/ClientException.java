package uk.co.alvagem.sofabed.messages.client;

import java.util.concurrent.ExecutionException;

public class ClientException extends ExecutionException {

	private static final long serialVersionUID = 1L;

	public ClientException() {
		super();
	}

	public ClientException(String message, Throwable cause) {
		super(message, cause);
	}

	public ClientException(String message) {
		super(message);
	}

	public ClientException(Throwable cause) {
		super(cause);
	}

}
