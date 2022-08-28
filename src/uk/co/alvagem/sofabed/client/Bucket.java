package uk.co.alvagem.sofabed.client;

import java.io.IOException;
import java.util.concurrent.Future;

import uk.co.alvagem.sofabed.Key;
import uk.co.alvagem.sofabed.Version;

public interface Bucket {
	Future<Version>  create(Record record) throws IOException;
	Future<Record> read(Key key) throws IOException;
	Future<Version>  write(Record record) throws IOException;
	Future<Void> delete(Key key, Version version) throws IOException;
	Future<Version>  lock(Key key) throws IOException;
	
	
	void setEncryptionKey(byte[] key);
	boolean isCompressing();
	void setCompression(boolean on);
}
