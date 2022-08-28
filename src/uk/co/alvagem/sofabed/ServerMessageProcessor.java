package uk.co.alvagem.sofabed;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import uk.co.alvagem.sofabed.messages.server.HeartbeatMessage;
import uk.co.alvagem.sofabed.messages.server.HeartbeatResponseMessage;
import uk.co.alvagem.sofabed.messages.server.ServerDeleteMessage;
import uk.co.alvagem.sofabed.messages.server.ServerDeleteResponse;
import uk.co.alvagem.sofabed.messages.server.ServerReadMessage;
import uk.co.alvagem.sofabed.messages.server.ServerReadResponse;
import uk.co.alvagem.sofabed.messages.server.ServerReadVersionMessage;
import uk.co.alvagem.sofabed.messages.server.ServerReadVersionResponse;
import uk.co.alvagem.sofabed.messages.server.ServerRecoveryMessage;
import uk.co.alvagem.sofabed.messages.server.ServerRecoveryResponse;
import uk.co.alvagem.sofabed.messages.server.ServerWriteMessage;
import uk.co.alvagem.sofabed.messages.server.ServerWriteMessageResponse;

/**
 * Manages a single SocketChannel and its buffer(s) to process Server messages
 * and return responses. A single channel may manage multiple request/response
 * messages.
 * 
 * @author rbp28668
 *
 */
public class ServerMessageProcessor extends MessageProcessor {

	private static MessageHandler[] handlers = new MessageHandler[MessageType.SERVER_MESSAGE_LIMIT.getCode()];
	static {
		put(new ServerReadHandler());
		put(new ServerReadResponseHandler());
		put(new ServerWriteHandler());
		put(new ServerWriteResponseHandler());
		put(new ServerVersionHandler());
		put(new ServerVersionResponseHandler());
		put(new ServerDeleteHandler());
		put(new ServerDeleteResponseHandler());
		put(new ServerClusterInfoHandler());
		put(new ServerClusterInfoResponseHandler());
		put(new ServerHeartbeatHandler());
		put(new ServerHeartbeatResponseHandler());
		put(new ServerRecoveryHandler());
		put(new ServerRecoveryResponseHandler());
	}

	private static void put(MessageHandler handler) {
		handlers[handler.getType()] = handler;
	}

	/**
	 * Creates a new server message processor to manage the given socket channel.
	 * @param server
	 * @param socketChannel
	 */
	ServerMessageProcessor(Server server, SocketChannel socketChannel, SocketProcessingThread processingThread) {
		super(server, socketChannel, processingThread, handlers);
	}

	@Override
	protected void updateQueueMetrics(Server server, int len) {
		server.getMetrics().updateClusterWriteQueueLength(len);
	}
	
	
	///////////////////////////////////////////////////////////////////
	// Message handlers for all the server messages
	///////////////////////////////////////////////////////////////////

	
	private static final class ServerReadHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.SVR_READ_MSG.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			try {
				ServerReadMessage msg = new ServerReadMessage(buffer);
				Bucket bucket = server.getBucket(msg.getBucketName());
				Document doc = bucket.getDocument(msg.getKey());
				
				ServerReadResponse response = new ServerReadResponse(msg.getCorrelationId(), doc.getVersion(), doc.getPayload());
				processor.write(response.getBuffer());
			}  catch (BucketException e) {
				throw new IOException(e.getMessage());
			}
		}
	}
	

	private static final class ServerReadResponseHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.SVR_READ_RESPONSE.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			ServerReadResponse msg = new ServerReadResponse(buffer);
			server.sendMessageToTask(msg);
		}

	}

	
	/**
	 * Handler for message asking to write a local copy of a message.
	 * Note that this doesn't distinguish between creates / updates.
	 * Any version checking should have been done on the coordinating
	 * node.
	 * Responds with status.
	 * @author rbp28668
	 *
	 */
	private static final class ServerWriteHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.SVR_WRITE_MSG.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			ServerWriteMessage msg = new ServerWriteMessage(buffer);
			long id = msg.getCorrelationId();
			try {
				String bucketName = msg.getBucket();
				Key key = msg.getKey();
				byte[] payload = msg.getPayload();
				Version version = msg.getVersion();
				
				System.out.println("Server Write " + msg.getKey() + " on " + server.getCluster().thisNodeName());
				Document doc = new Document(key,  version, payload);
				Bucket bucket = server.getBucket(bucketName);
				bucket.writeDocument(doc);
				
				ServerWriteMessageResponse response = new ServerWriteMessageResponse(id, MessageStatus.OK);
				processor.write(response.getBuffer());
			} catch (BucketException e) {
				ServerWriteMessageResponse response = new ServerWriteMessageResponse(id, e.getStatus());
				processor.write(response.getBuffer());
			}
		}

	}

	private static final class ServerWriteResponseHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.SVR_WRITE_RESPONSE.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			ServerWriteMessageResponse response = new ServerWriteMessageResponse(buffer);
			server.sendMessageToTask(response);
		}

	}

	//////  Version of a record
	
	
	private static final class ServerVersionHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.SVR_VERSION_MSG.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			ServerReadVersionMessage msg = new ServerReadVersionMessage(buffer);
			long correlationId = msg.getCorrelationId();
			ServerReadVersionResponse response;
			
			try {
				Bucket bucket = server.getBucket(msg.getBucket());
				Document doc = bucket.getDocument(msg.getKey());
				Version version = doc.getVersion();
				System.out.println("Server Version " + version.toString() + " for " + msg.getKey().toString() + " on " + server.getCluster().thisNodeName());
				response = new ServerReadVersionResponse(correlationId, MessageStatus.OK, version);
			} catch (BucketException e) {
				response = new ServerReadVersionResponse(correlationId, e.getStatus(), Version.NONE);
			}
			processor.write(response.getBuffer());
		}

	}

	private static final class ServerVersionResponseHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.SVR_VERSION_RESPONSE.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			ServerReadVersionResponse response = new ServerReadVersionResponse(buffer);
			System.out.println("Server Version Response received by " + server.getCluster().thisNodeName());
			server.sendMessageToTask(response);
		}

	}
	
	
	//// DELETE
	
	private static final class ServerDeleteHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.SVR_DELETE_MSG.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			try {
				ServerDeleteMessage msg = new ServerDeleteMessage(buffer);
				Bucket bucket = server.getBucket(msg.getBucketName());
				bucket.deleteDocument(msg.getKey());
				
				long correlationId = msg.getCorrelationId();
				ServerDeleteResponse response = new ServerDeleteResponse(correlationId, MessageStatus.OK);
				processor.write(response.getBuffer());
			} catch (IOException e) {
				throw e;
			} catch (BucketException e) {
				throw new IOException(e.getMessage());
			}
		}

	}

	private static final class ServerDeleteResponseHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.SVR_DELETE_RESPONSE.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			ServerDeleteResponse response = new ServerDeleteResponse(buffer);
			System.out.println("Server Delete Response received by " + server.getCluster().thisNodeName());
			server.sendMessageToTask(response);
		}

	}

	
	
	///// Cluster information - used to replicate full cluster information.

	private static final class ServerClusterInfoHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.SVR_CLUSTER_INFO.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			// TODO
		}

	}

	private static final class ServerClusterInfoResponseHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.SVR_CLUSTER_INFO_RESPONSE.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			// TODO
		}

	}

	private static final class ServerHeartbeatHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.SVR_HEARTBEAT.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			HeartbeatMessage msg = new HeartbeatMessage(buffer);
			
			Cluster cluster = server.getCluster();
			cluster.processHeartbeat(msg, (ServerMessageProcessor)processor);
			//System.out.println("Received Heartbeat");
		}

	}

	private static final class ServerHeartbeatResponseHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.SVR_HEARTBEAT_RESPONSE.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			HeartbeatResponseMessage msg = new HeartbeatResponseMessage(buffer);
			Cluster cluster = server.getCluster();
			cluster.processHeartbeatResponse(msg);
			//System.out.println("Received Heartbeat response from " + msg.getNodeId());
		}
	}

	private static final class ServerRecoveryHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.SVR_RECOVERY_MSG.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			ServerRecoveryMessage msg = new ServerRecoveryMessage(buffer);
			server.processRecoveryMessage(msg, (ServerMessageProcessor)processor);
		}
	}
	

	private static final class ServerRecoveryResponseHandler extends MessageHandler {

		@Override
		short getType() {
			return MessageType.SVR_RECOVERY_RESPONSE.getCode();
		}

		@Override
		void process(Server server, ByteBuffer buffer, MessageProcessor processor) throws IOException {
			ServerRecoveryResponse msg = new ServerRecoveryResponse(buffer);
			server.processRecoveryResponse(msg);
		}

	}

}
