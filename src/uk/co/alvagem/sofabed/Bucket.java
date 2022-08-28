package uk.co.alvagem.sofabed;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A bucket manages a set of keys.  Keys only have to be unique within a bucket.
 * A cluster may contain many buckets.
 * @author rbp28668
 *
 */
public class Bucket {
	
	private String name;
	private Map<Key,Document> contents = new ConcurrentHashMap<>(); // Cheap and cheerful lookup

	public Bucket(String name) {
		this.name = name.toLowerCase();
	}

	public String getName() {
		return name;
	}

	Document getDocument(Key key) throws BucketException {
		Document doc = contents.get(key);
		if(doc == null) {
			throw new BucketException("Document " + key.toString() + " not found in bucket " + name, MessageStatus.KEY_NOT_FOUND);
		}
		return doc;
	}
	
	void writeDocument(Document doc) {
		Key key = doc.getKey();
		contents.put(key, doc); // replace existing
	}

	public boolean containsKey(Key key) {
		return contents.containsKey(key);
	}

	public void markRecordForReplication(Key key)  throws BucketException {
		Document existing = contents.get(key);
		if(existing == null) {
			throw new BucketException("Document " + key.toString() + " not found in bucket " + name, MessageStatus.KEY_NOT_FOUND);
		}
		existing.markForReplication();
	}

	public void deleteDocument(Key key) throws BucketException{
		Document removed = contents.remove(key); {
			if(removed == null) {
				throw new BucketException("Document " + key.toString() + " not found and could not be removed", MessageStatus.KEY_NOT_FOUND);
			}
		}
	}

	public Collection<Document> getDocuments() {
		return contents.values();
	}

	
}
