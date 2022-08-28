package uk.co.alvagem.sofabed.messages.client;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;

import uk.co.alvagem.sofabed.MessageStatus;
import uk.co.alvagem.sofabed.MessageType;

public class ClusterInfoMessageResponse extends ClientResponseMessage {

	private Node nodes[];
	
	public ClusterInfoMessageResponse(long correlationId, Node[] nodes){
		int len = baseLength();
		len += Short.BYTES; // Node count;
		for(Node node : nodes) {
			len += Short.BYTES; // length of the inet address
			byte[] addr = node.address.getAddress();
			len += addr.length; // bytes for the address proper
			len += Short.BYTES; // port
			len += Integer.BYTES; // id
		}
		setBuffer(MessageType.CLUSTER_INFO_MSG_RESPONSE.getCode(), len, correlationId, MessageStatus.OK);
		
		int offset = baseLength();
		buffer.putShort(offset, (short)nodes.length);
		offset += Short.BYTES; // Node count;
		
		for(Node node : nodes) {
			byte[] addr = node.address.getAddress();
			buffer.putShort(offset, (short)addr.length);
			offset += Short.BYTES; // length of the inet address
			
			buffer.put(offset, addr);
			offset += addr.length; // bytes for the address proper
			
			buffer.putShort(offset, (short)node.port);
			offset += Short.BYTES; // port
			
			buffer.putInt(offset, node.id);
			offset += Integer.BYTES;
		}

	}
	
	public ClusterInfoMessageResponse(ByteBuffer buffer) throws IOException{
		super(buffer);
		if(getType() != MessageType.CLUSTER_INFO_MSG_RESPONSE.getCode()) {
			throw new IllegalArgumentException("Message is not a Cluster Info Response Message");
		}
		
		int offset = baseLength();
		int nodeCount = buffer.getShort(offset);
		offset += Short.BYTES; // Node count;
	
		nodes = new Node[nodeCount];
		
		for(int i=0; i<nodeCount; ++i) {
			int len = buffer.getShort(offset);
			offset += Short.BYTES; // length of the inet address
			
			byte[] addr = new byte[len];
			buffer.get(offset, addr);
			offset += len; // bytes for the address proper
			
			int port = buffer.getShort(offset);
			if(port < 0) port += 65536;  // make unsigned
			offset += Short.BYTES; // port
			
			int id = buffer.getInt(offset);
			offset += Integer.BYTES;
			
			nodes[i] = new Node(InetAddress.getByAddress(addr), port, id);
		}

	}

	public Node[] getNodes() {
		return nodes;
	}
	
	public static class Node {
		public InetAddress address;
		public int port;
		public int id;
		
		public Node(InetAddress address, int port, int id){
			this.address = address;
			this.port = port;
			this.id = id;
		}
	}
}
