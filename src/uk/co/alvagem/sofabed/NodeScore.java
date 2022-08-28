package uk.co.alvagem.sofabed;

import java.io.UnsupportedEncodingException;

import org.apache.commons.codec.digest.MurmurHash3;
import org.apache.commons.codec.digest.MurmurHash3.IncrementalHash32x86;

public class NodeScore implements Comparable<NodeScore> {
	private final int hash;
	private final Node node;

	public NodeScore(Node node, String bucketName, Key key) {
		this.node = node;
		this.hash = getDocHash(node.getNodeId(), bucketName, key);
	}

	/**
	 * @param nodeId
	 * @param bucketName
	 * @param key
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	private int getDocHash(int nodeId, String bucketName, Key key)  {
		IncrementalHash32x86 digest = new MurmurHash3.IncrementalHash32x86();
		digest.start(nodeId);
		try {
			byte[] bucketAsBytes = bucketName.getBytes("UTF-8");
			digest.add(bucketAsBytes, 0, bucketAsBytes.length);
			byte[] keyBytes = key.asBytes();
			digest.add(keyBytes, 0, keyBytes.length);
		} catch (UnsupportedEncodingException e) {
			// TODO Log this - should never happen
			e.printStackTrace();
		}
		return digest.end();
	}

	@Override
	public int compareTo(NodeScore o) {
		return Integer.compare(o.hash, hash); // should be descending order
	}

	public Node getNode() {
		return node;
	}
}