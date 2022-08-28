package uk.co.alvagem.sofabed;

/**
 * Exception to record errors thrown by an individual bucket. Includes message 
 * status to return to the caller.
 * @author rbp28668
 *
 */
public class BucketException extends Exception {

	private MessageStatus status;
	
	public BucketException(String message, MessageStatus status) {
		super(message);
		this.status = status;
	}

	public MessageStatus getStatus() {
		return status;
	}

	
}
