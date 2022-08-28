package uk.co.alvagem.sofabed;

public enum MessageType {
	
	// Client side messages
	NOP_MSG(0),
	NOP_MSG_RESPONSE(1),
	READ_MSG (2),
	READ_MSG_RESPONSE(3),
	CREATE_MSG (4),
	CREATE_MSG_RESPONSE(5),
	UPDATE_MSG (6),
	UPDATE_MSG_RESPONSE(7),
	DELETE_MSG (8),
	DELETE_MSG_RESPONSE(9),
	LOCK_MSG (10),
	LOCK_MSG_RESPONSE(11),
	VERSION_MSG (12),
	VERSION_MSG_RESPONSE(13),
	CLUSTER_INFO_MSG(14),
	CLUSTER_INFO_MSG_RESPONSE(15),
	
	CLIENT_MESSAGE_LIMIT(16),
	// Server side messages
	SVR_READ_MSG (16), // Read a record
	SVR_READ_RESPONSE (17),
	SVR_WRITE_MSG(18), // write a record to a node
	SVR_WRITE_RESPONSE(19),
	SVR_VERSION_MSG(20),  // Get version of a record
	SVR_VERSION_RESPONSE(21),
	SVR_CLUSTER_INFO(22), // replicate full cluster info
	SVR_CLUSTER_INFO_RESPONSE(23),
	SVR_HEARTBEAT(24),
	SVR_HEARTBEAT_RESPONSE(25),
	SVR_DELETE_MSG(26),
	SVR_DELETE_RESPONSE(27),
	SVR_RECOVERY_MSG(28),
	SVR_RECOVERY_RESPONSE(29),
	SERVER_MESSAGE_LIMIT(32), 
	
	;
	
	private final short code;
	
	
	MessageType(int code) {
		this.code = (short)code;
	}
	
	public short getCode() {
		return code;
	}
}
