package uk.co.alvagem.sofabed.client;

import uk.co.alvagem.sofabed.Key;
import uk.co.alvagem.sofabed.Version;

public class Record {

	private Key key;
	private Version version;
	private byte[] payload;
	
	public Record(Key key, Version version, byte[] payload) {
		super();
		this.key = key;
		this.version = version;
		this.payload = payload;
	}
	public Key getKey() {
		return key;
	}
	public void setKey(Key key) {
		this.key = key;
	}
	public Version getVersion() {
		return version;
	}
	public void setVersion(Version version) {
		this.version = version;
	}
	public byte[] getPayload() {
		return payload;
	}
	public void setPayload(byte[] payload) {
		this.payload = payload;
	}
	
	
}
