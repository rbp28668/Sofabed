package uk.co.alvagem.sofabed.messages.client;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.Key;
import uk.co.alvagem.sofabed.Version;

/**
 * Defines standard message format for message with a payload i.e.
 * create, update, read response.
	  * Long: Version
	  * Short: BucketLen;
	  * Variable: Bucket;
	  * Short: KeyLen;
	  * Variable : Key
	  * Int: payloadLen
	  * Variable: Payload
 * @author rbp28668
 *
 */
class DataPayload {

		private String bucket;
		private Key key;
		private Version version;
		private byte[] payload;
	
		DataPayload(ByteBuffer buffer, int offset) throws UnsupportedEncodingException {
			buffer.position(offset);
			version = new Version(buffer.getLong());

			int len;
			byte[] bytes;

			// Get the bucket name;
			len = buffer.getShort();
			bytes = new byte[len];
			buffer.get(bytes, 0, len);
			bucket = new String(bytes, "UTF-8");

			// Get the key
			len = buffer.getShort();
			bytes = new byte[len];
			buffer.get(bytes, 0, len);
			key = new Key(bytes);

			int payloadLength = buffer.getInt();

			payload = new byte[payloadLength];
			buffer.get(payload, 0, payloadLength);
			
			
		}

		static int bufferSize(String bucket, Key key, Version version, byte[] payload) throws UnsupportedEncodingException {
			byte[] bucketBytes = bucket.getBytes("UTF-8");
			byte[] keyBytes = key.asBytes();

			int len = 0;
			len += Version.BYTES; // version
			len += Short.BYTES; // bucket length;
			len += bucketBytes.length;
			len += Short.BYTES; // key length;
			len += keyBytes.length;
			len += Integer.BYTES; // payload length;
			len += payload.length;
			
			return len;
		}

		static void writeToBuffer(ByteBuffer buffer, int offset, String bucket, Key key, Version version, byte[] payload)
				throws UnsupportedEncodingException {
			
			byte[] bucketBytes = bucket.getBytes("UTF-8");
			byte[] keyBytes = key.asBytes();

			buffer.putLong(offset, version.asLong());
			offset += Long.BYTES;

			buffer.putShort(offset, (short) bucketBytes.length);
			offset += Short.BYTES;
			buffer.put(offset, bucketBytes);
			offset += bucketBytes.length;

			buffer.putShort(offset, (short) keyBytes.length);
			offset += Short.BYTES;
			buffer.put(offset, keyBytes);
			offset += keyBytes.length;

			buffer.putInt(offset, payload.length);
			offset += Integer.BYTES;

			buffer.put(offset, payload);
		}

		Version getVersion() {
			return version;
		}

		String getBucket() {
			return bucket;
		}

		Key getKey() {
			return key;
		}

		byte[] getPayload() {
			return payload;
		}

	

}
