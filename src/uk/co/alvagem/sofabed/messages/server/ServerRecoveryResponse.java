package uk.co.alvagem.sofabed.messages.server;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.Key;
import uk.co.alvagem.sofabed.MessageType;
import uk.co.alvagem.sofabed.Version;

/**
 * Message for sending recovery information back to the requesting server.  In response
 * to the corresponding ServerRecoveryMessage a server should send back metadata for
 * all the records that the requesting server should have (that this node knows about).
 * @see RecoveryThread
 * @author rbp28668
 *
 */
public class ServerRecoveryResponse extends ServerMessage {

	private String bucketName;
	private Key key;
	private Version version;
	private int nodeId;
	
	public ServerRecoveryResponse(ByteBuffer buffer) throws UnsupportedEncodingException {
		super(buffer);
		buffer.position(super.baseLength()) ;
		bucketName = getString(buffer);
		key = new Key(buffer);
		version = new Version(buffer);
		nodeId = buffer.getInt();
	}

	public ServerRecoveryResponse(String bucketName, Key key, Version v, int nodeId, long correlationId) throws UnsupportedEncodingException {
		byte[] bucketBytes = bucketName.getBytes("UTF-8");
		byte[] keyBytes = key.asBytes();

		int len = super.baseLength();
		len += stringBytesLength(bucketBytes);
		len += Key.bytesLength(keyBytes);
		len += Version.BYTES;
		len += Integer.BYTES; // node ID
		
		setBuffer(MessageType.SVR_RECOVERY_RESPONSE.getCode(), len, correlationId);
		
		int offset = super.baseLength() ;
		offset = writeStringBytes(buffer,offset,bucketBytes);
		offset = key.write(buffer, offset);
		offset = v.write(buffer, offset);
		buffer.putInt(offset, nodeId);
		offset += Integer.BYTES;
	}

	public String getBucketName() {
		return bucketName;
	}

	public Key getKey() {
		return key;
	}

	public Version getVersion() {
		return version;
	}

	public int getNodeId() {
		return nodeId;
	}
	

	
	
}
