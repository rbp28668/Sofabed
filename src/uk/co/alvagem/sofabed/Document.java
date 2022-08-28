package uk.co.alvagem.sofabed;

public class Document {
	private Key key;
	private Version version;
	private byte[] payload;
	private long lockTime;
	private boolean requiresReplication;
	
	Document(Key key, Version version, byte[] payload){
		this.key = key;
		this.version = version;
		this.payload = payload;
		this.requiresReplication = false;
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

	public void markForReplication() {
		requiresReplication = true;
		
	}
	
	
}
