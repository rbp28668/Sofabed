package uk.co.alvagem.sofabed.client;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;

import uk.co.alvagem.sofabed.MessageType;
import uk.co.alvagem.sofabed.messages.client.ClientMessage;
import uk.co.alvagem.sofabed.messages.client.ClusterInfoMessageResponse;
import uk.co.alvagem.sofabed.messages.client.CreateMessageResponse;
import uk.co.alvagem.sofabed.messages.client.DeleteMessageResponse;
import uk.co.alvagem.sofabed.messages.client.LockMessageResponse;
import uk.co.alvagem.sofabed.messages.client.NopMessageResponse;
import uk.co.alvagem.sofabed.messages.client.ReadMessageResponse;
import uk.co.alvagem.sofabed.messages.client.UpdateMessageResponse;
import uk.co.alvagem.sofabed.messages.client.VersionMessageResponse;

public class Connection {
	private final static int DEFAULT_BUFFER_SIZE = 1024;
	private SocketChannel socketChannel;
	private ClientImpl client;
	private ClientSocketProcessingThread processingThread;
	private ByteBuffer inputBuffer;
	private LinkedList<ByteBuffer> outputQueue = new LinkedList<>();
	
	private static ClientMessageHandler[] handlers = new ClientMessageHandler[MessageType.CLIENT_MESSAGE_LIMIT.getCode()];
	static {
		put(new NopMessageResponseHandler());
		put(new ReadMessageResponseHandler());
		put(new CreateMessageResponseHandler());
		put(new UpdateMessageResponseHandler());
		put(new DeleteMessageResponseHandler());
		put(new LockMessageResponseHandler());
		put(new VersionMessageResponseHandler());
		put(new ClusterInfoMessageResponseHandler());
		
	}

	private static void put(ClientMessageHandler handler) {
		handlers[handler.getType()] = handler;
	}

	
	Connection(ClientImpl client, ClientSocketProcessingThread processingThread) throws IOException{
		this.client = client;
		this.processingThread = processingThread;
		inputBuffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
	}
	
	void connect(SocketAddress addr ) throws IOException {
		socketChannel = SocketChannel.open();
	    socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, false);
		// TODO look at increasing buffer sizes
		socketChannel.configureBlocking(false);
	    socketChannel.connect(addr);
		processingThread.addChannel(socketChannel, this);
	}
	
	public void write(ByteBuffer message) throws IOException {
		
		if (message.position() != 0)
			message.flip(); // prepare for reading if not already done.

		if(!message.hasRemaining()) {
			// TODO log properly
			System.out.println("Nothing to send");
			return;
		}
		
		synchronized (outputQueue) {
			outputQueue.add(message);
		}
		processingThread.setPendingWrite(this);
	}
	
	
	public void close() throws IOException {
		if(socketChannel.isOpen()) {
			socketChannel.close();
		}
	}

	/**
	 * General response message handling
	 * @param response
	 */
	private void receive(ClientMessage response) throws IOException {
		client.getCluster().handleResponse(response);
	}

	/**
	 * Special purpose handling of cluster info message.
	 * @param response
	 * @throws IOException
	 */
	private void receiveClusterInfo(ClusterInfoMessageResponse response) throws IOException {
		client.receiveClusterInfo(response);
		
	}

	
	/**
	 * Callback to enable writes on this connection.  Called in the context of
	 * the socket processing thread (the selector isn't blocking at this point)
	 * to ensure that the new interestOps are applied.
	 * @param selector
	 */
	public void enableWrite(Selector selector) {
		if(socketChannel.isConnected()) {
			SelectionKey key = socketChannel.keyFor(selector);
			int ops = key.interestOps();
			ops |= SelectionKey.OP_WRITE;
			key.interestOps(ops);
		} else {
			System.out.println("Connection.write:  not connected" + socketChannel);
		}
	}

	
	void process(SelectionKey selectionKey) throws IOException {
		//System.out.println("MessageProcessor:process " + selectionKey.readyOps());
		if (selectionKey.isReadable()) {
			//System.out.println("MessageProcessor:Readable");
			onRead(selectionKey);
		}

		if (selectionKey.isWritable()) {
			//System.out.println("MessageProcessor:Writeable");
			onWrite(selectionKey);
		}

		if (selectionKey.isConnectable()) {
			//System.out.println("MessageProcessor:Connectable");
			onConnect(selectionKey);
		}
	}

	/**
	 * Called when there are bytes available to be read from the channel.
	 * 
	 * @param selectionKey
	 * @throws IOException
	 */
	protected void onRead(SelectionKey selectionKey) throws IOException {
		SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
		while (socketChannel.isConnected() && socketChannel.read(inputBuffer) > 0) {

			// All messages start with a message type and length (6 bytes in all)
			// Don't process until we've got at least this.
			if (inputBuffer.position() >= 6) {
				short msgType = inputBuffer.getShort(0);
				if (msgType < handlers.length) {
					ClientMessageHandler handler = handlers[msgType];
					if (handler != null) {

						if (handler.isComplete(inputBuffer)) {

							// May have read in data beyond the message. If so grab it before
							// we relinquish control of the buffer to the message.
							byte[] remainder = null;
							int len = handler.messageLength(inputBuffer);
							int pos = inputBuffer.position();
							if (pos > len) {
								remainder = new byte[pos - len];
								inputBuffer.get(len, remainder);
							}

							handler.process(inputBuffer, this);

							inputBuffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
							if (remainder != null) {
								inputBuffer.put(remainder);
							}

							break;
						} else { // not complete message - may be due to buffer being too small.
							int len = handler.messageLength(inputBuffer);
							if (len > inputBuffer.capacity()) {
								// Then buffer is too small for the complete message.
								// so allocate a new one.
								ByteBuffer longBuffer = ByteBuffer.allocate(len);
								inputBuffer.flip();
								longBuffer.put(inputBuffer);
								inputBuffer = longBuffer;
								// TODO log use of long buffer
							}
						}

					} else {
						System.err.println("Connection: No client message handler for type " + msgType);
						break;
					}

				} else {
					System.err.println("Connection: Message type out of range " + msgType);
					break;
				}
			}
		}

	}

	/**
	 * Called when there is space in the output buffer for the channel so that bytes
	 * can be written to it.
	 * 
	 * @param selectionKey
	 * @throws IOException
	 */
	protected void onWrite(SelectionKey selectionKey) throws IOException {
		SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
		if (!socketChannel.isConnected()) {
			return;
		}
		// Note that this uses the gathered write to write as much of the output queue
		// as possible. This makes it possible to pass a message in in multiple parts
		// i.e. a header and a separate buffer direct from storage.
		synchronized (outputQueue) {
			if (!outputQueue.isEmpty()) {
				ByteBuffer[] buffers = outputQueue.toArray(new ByteBuffer[outputQueue.size()]);

				while (socketChannel.write(buffers) > 0) {
					// Remove any buffers that have been written from the output queue.
					while (!outputQueue.isEmpty() && !outputQueue.getFirst().hasRemaining()) {
						outputQueue.removeFirst();
					}
					if (outputQueue.isEmpty())
						break;
					buffers = outputQueue.toArray(new ByteBuffer[outputQueue.size()]);
				}
			}
		}

		// If there's nothing more to write then remove the interest in being
		// able to write to the channel.
		// Don't cancel the key as it will no longer be usable.
		synchronized (outputQueue) {
			if (outputQueue.isEmpty()) {
				if(socketChannel.finishConnect()){
					int ops = selectionKey.interestOps();
					ops &= ~SelectionKey.OP_WRITE;
					selectionKey.interestOps(ops);
					//selectionKey.selector().wakeup();
				}
			}
		}

	}

	/**
	 * Called whether this key's channel has either finished, or failed to finish,
	 * its socket-connection operation.
	 * 
	 * @param selectionKey
	 * @throws IOException
	 */
	protected void onConnect(SelectionKey selectionKey) throws IOException {
		SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
		if (socketChannel.isConnectionPending()) {
			if(socketChannel.finishConnect()) {
				int ops = selectionKey.interestOps();
				ops &= ~SelectionKey.OP_CONNECT;
				ops |= SelectionKey.OP_READ;
				if (!outputQueue.isEmpty()) {
					ops |= SelectionKey.OP_WRITE;
				}
				selectionKey.interestOps(ops);
				//selectionKey.selector().wakeup();
			}
		}
	}


	// Handle all the client side responses.
//	NOP_MSG_RESPONSE(1),
	private static class NopMessageResponseHandler extends ClientMessageHandler {

		@Override
		public short getType() {
			return MessageType.NOP_MSG_RESPONSE.getCode();
		}

		@Override
		public void process(ByteBuffer buffer, Connection connection) throws IOException {
			NopMessageResponse response = new NopMessageResponse(buffer);
			connection.receive(response);
		}
		
	}
	
	
//	READ_MSG_RESPONSE(3),
	private static class ReadMessageResponseHandler extends ClientMessageHandler {

		@Override
		public short getType() {
			return MessageType.READ_MSG_RESPONSE.getCode();
		}

		@Override
		public void process(ByteBuffer buffer, Connection connection) throws IOException {
			ReadMessageResponse response = new ReadMessageResponse(buffer);
			connection.receive(response);
		}
		
	}

//	CREATE_MSG_RESPONSE(5),
	private static class CreateMessageResponseHandler extends ClientMessageHandler {

		@Override
		public short getType() {
			return MessageType.CREATE_MSG_RESPONSE.getCode();
		}

		@Override
		public void process(ByteBuffer buffer, Connection connection) throws IOException {
			CreateMessageResponse response = new CreateMessageResponse(buffer);
			connection.receive(response);
		}
		
	}

//	UPDATE_MSG_RESPONSE(7),
	private static class UpdateMessageResponseHandler extends ClientMessageHandler {

		@Override
		public short getType() {
			return MessageType.UPDATE_MSG_RESPONSE.getCode();
		}

		@Override
		public void process(ByteBuffer buffer, Connection connection) throws IOException {
			UpdateMessageResponse response = new UpdateMessageResponse(buffer);
			connection.receive(response);
		}
		
	}

//	DELETE_MSG_RESPONSE(9),
	private static class DeleteMessageResponseHandler extends ClientMessageHandler {

		@Override
		public short getType() {
			return MessageType.DELETE_MSG_RESPONSE.getCode();
		}

		@Override
		public void process(ByteBuffer buffer, Connection connection) throws IOException {
			DeleteMessageResponse response = new DeleteMessageResponse(buffer);
			connection.receive(response);
		}
		
	}

//	LOCK_MSG_RESPONSE(11),
	private static class LockMessageResponseHandler extends ClientMessageHandler {

		@Override
		public short getType() {
			return MessageType.LOCK_MSG_RESPONSE.getCode();
		}

		@Override
		public void process(ByteBuffer buffer, Connection connection) throws IOException {
			LockMessageResponse response = new LockMessageResponse(buffer);
			connection.receive(response);
		}
		
	}

//	VERSION_MSG_RESPONSE(13),
	private static class VersionMessageResponseHandler extends ClientMessageHandler {

		@Override
		public short getType() {
			return MessageType.VERSION_MSG_RESPONSE.getCode();
		}

		@Override
		public void process(ByteBuffer buffer, Connection connection) throws IOException {
			VersionMessageResponse response = new VersionMessageResponse(buffer);
			connection.receive(response);
		}
		
	}

//	CLUSTER_INFO_MSG_RESPONSE(15),
	private static class ClusterInfoMessageResponseHandler extends ClientMessageHandler {

		@Override
		public short getType() {
			return MessageType.CLUSTER_INFO_MSG_RESPONSE.getCode();
		}

		@Override
		public void process(ByteBuffer buffer, Connection connection) throws IOException {
			ClusterInfoMessageResponse response = new ClusterInfoMessageResponse(buffer);
			connection.receiveClusterInfo(response);
		}
		
	}




	
	
}
