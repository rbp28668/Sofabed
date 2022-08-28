package uk.co.alvagem.sofabed.client;

import java.io.IOException;
import java.util.concurrent.Future;

import uk.co.alvagem.sofabed.Key;
import uk.co.alvagem.sofabed.Version;

class BucketImpl implements Bucket {

	private String name;
	private ClusterImpl cluster;
	
	BucketImpl(ClusterImpl clusterImpl, String name){
		this.cluster = clusterImpl;
		this.name = name;
	}
	
	@Override
	public Future<Version>  create(Record record) throws IOException {
		return cluster.create(name, record);
		
	}

	@Override
	public Future<Record> read(Key key) throws IOException {
		return cluster.read(name, key);
	}

	@Override
	public Future<Version>  write(Record record) throws IOException {
		return cluster.write(name, record);

	}

	@Override
	public Future<Void> delete(Key key, Version version) throws IOException {
		return cluster.delete(name, key, version);
	}

	@Override
	public Future<Version>  lock(Key key)  throws IOException {
		return cluster.lock(name, key);

	}

	@Override
	public void setEncryptionKey(byte[] key) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isCompressing() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setCompression(boolean on) {
		// TODO Auto-generated method stub

	}

}
