package uk.co.alvagem.sofabed;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import uk.co.alvagem.sofabed.messages.client.ClusterInfoMessage;
import uk.co.alvagem.sofabed.messages.client.ClusterInfoMessageResponse;
import uk.co.alvagem.sofabed.messages.client.CreateMessage;
import uk.co.alvagem.sofabed.messages.client.DeleteMessage;
import uk.co.alvagem.sofabed.messages.client.NopMessage;
import uk.co.alvagem.sofabed.messages.client.NopMessageResponse;
import uk.co.alvagem.sofabed.messages.client.ReadMessage;
import uk.co.alvagem.sofabed.messages.client.UpdateMessage;
import uk.co.alvagem.sofabed.messages.client.VersionMessage;
import uk.co.alvagem.sofabed.messages.client.VersionMessageResponse;

/**
 * Manages a single SocketChannel and its buffer(s) to process client messages
 * and return responses. A single channel may manage multiple request/response
 * messages.
 * 
 * @author rbp28668
 *
 */
public class ClientMessageProcessor extends MessageProcessor {

	private static MessageHandler[] handlers = new MessageHandler[MessageType.CLIENT_MESSAGE_LIMIT.getCode()];
	static {
		put(new NopMessageHandler());
		put(new ReadMessageHandler());
		put(new CreateMessageHandler());
		put(new UpdateMessageHandler());
		put(new DeleteMessageHandler());
		put(new LockMessageHandler());
		put(new VersionMessageHandler());
		put(new ClusterInfoMessageHandler());
	}

	private static void put(MessageHandler handler) {
		handlers[handler.getType()] = handler;
	}

	ClientMessageProcessor(Server server, SocketChannel socketChannel, SocketProcessingThread processingThread) {
		super(server, socketChannel, processingThread,  handlers);
	}

	@Override
	protected void updateQueueMetrics(Server server, int len) {
		server.getMetrics().updateClientWriteQueueLength(len);
	}

	//NOP_MSG(0),
	//NOP_MSG_RESPONSE(1),

	private static final class NopMessageHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.NOP_MSG.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			NopMessage msg = new NopMessage(buffer);
			long correlationId = msg.getCorrelationId();
			
			NopMessageResponse response = new NopMessageResponse(correlationId);
			processor.write(response.getBuffer());
		}

	}


	//READ_MSG (2),
	//READ_MSG_RESPONSE(3),

	private static final class ReadMessageHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.READ_MSG.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			ReadMessage msg = new ReadMessage(buffer);
			System.out.println("Read " + msg.getKey() + " received from client on " + server.getCluster().thisNodeName());
			new ReadRecordTask(server, msg, (ClientMessageProcessor) processor);
		}

	}

	//CREATE_MSG (4),
	//CREATE_MSG_RESPONSE(5),
	
	private static final class CreateMessageHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.CREATE_MSG.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			CreateMessage msg = new CreateMessage(buffer);
			System.out.println("Create " + msg.getKey() + " received from client on " + server.getCluster().thisNodeName());
			new CreateRecordTask(server, msg, (ClientMessageProcessor) processor);
		}

	}
	
	//UPDATE_MSG (6),
	//UPDATE_MSG_RESPONSE(7),
	private static final class UpdateMessageHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.UPDATE_MSG.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			UpdateMessage msg = new UpdateMessage(buffer);
			System.out.println("Update " + msg.getKey() + " received from client on " + server.getCluster().thisNodeName());
			new UpdateRecordTask(server, msg, (ClientMessageProcessor)processor);
		}

	}
	//DELETE_MSG (8),
	//DELETE_MSG_RESPONSE(9),
	private static final class DeleteMessageHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.DELETE_MSG.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			DeleteMessage msg = new DeleteMessage(buffer);
			System.out.println("Delete " + msg.getKey() + " received from client on " + server.getCluster().thisNodeName());
			new DeleteRecordTask(server,msg, (ClientMessageProcessor)processor);
		}

	}

	
	//LOCK_MSG (10),
	//LOCK_MSG_RESPONSE(11),
	private static final class LockMessageHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.LOCK_MSG.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			// TODO
		}

	}
	
	
	//VERSION_MSG (12),
	//VERSION_MSG_RESPONSE(13),
	private static final class VersionMessageHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.VERSION_MSG.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			VersionMessage request = new VersionMessage(buffer);
			long correlationId = request.getCorrelationId();
			
			short major = 0;
			short minor = 1;
			short patch = 0;
			VersionMessageResponse response = new VersionMessageResponse(correlationId, major, minor, patch);
			processor.write(response.getBuffer());
		}

	}
	
	//CLUSTER_INFO_MSG(14),
	//CLUSTER_INFO_MSG_RESPONSE(15),
	private static final class ClusterInfoMessageHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.CLUSTER_INFO_MSG.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			
			ClusterInfoMessage request = new ClusterInfoMessage(buffer);
			long correlationId = request.getCorrelationId();
			
			Cluster cluster = server.getCluster();
			ClusterInfoMessageResponse.Node nodes[] = new ClusterInfoMessageResponse.Node[cluster.nodeCount()];
			int idx = 0;
			for(ClusterNode cn : cluster.getNodeInfo()) {
				InetAddress address = cn.getAddress();
				int port = cn.getClientPort();
				int id = cn.getNodeId();
				ClusterInfoMessageResponse.Node node = new ClusterInfoMessageResponse.Node(address, port, id);
				nodes[idx++] = node;
			}
			
			ClusterInfoMessageResponse response = new ClusterInfoMessageResponse(correlationId,nodes);
			processor.write(response.getBuffer());
		}

	}

}
