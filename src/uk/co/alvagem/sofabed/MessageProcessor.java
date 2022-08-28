package uk.co.alvagem.sofabed;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;

public class MessageProcessor implements ChannelHandler {

	private final static int DEFAULT_BUFFER_SIZE = 1024;
	private final Server server;
	private SocketChannel socketChannel;
	private final SocketProcessingThread processingThread; // owns the selector
	private final MessageHandler[] handlers;

	private ByteBuffer inputBuffer;
	private LinkedList<ByteBuffer> outputQueue = new LinkedList<>();

	MessageProcessor(Server server, SocketChannel socketChannel, SocketProcessingThread processingThread, MessageHandler[] handlers) {
		//System.out.println("MessageProcessor.Cons " + socketChannel);
		this.server = server;
		this.socketChannel = socketChannel;
		this.processingThread = processingThread;
		this.handlers = handlers;

		inputBuffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
	}

	@Override
	public void write(ByteBuffer message)  {
		
		if (message.position() != 0)
			message.flip(); // prepare for reading if not already done.

		if(!message.hasRemaining()) {
			// TODO log properly
			System.out.println("Nothing to send");
			return;
		}
		
		int len = 0;
		synchronized (outputQueue) {
			outputQueue.add(message);
			len = outputQueue.size();
		}
		processingThread.setPendingWrite(this);
		updateQueueMetrics(server, len);
	}

	
	/**
	 * Log out the write queue length.  Over-ride in derived classes to update metrics appropriately
	 * @param server
	 * @param len
	 */
	protected void updateQueueMetrics(Server server, int len) {
		System.out.println("Write queue length " + len);
	}

	/**
	 * Callback to enable writes on this processor.  Called in the context of
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
			System.out.println("MessageProcessor.write:  not connected" + socketChannel);
		}
	}

	/**
	 * Gets the core server. Intended primarily to allow derived classes to pick up
	 * the appropriate selector to register a channel with.
	 * 
	 * @return a reference to the server.
	 */
	protected Server getServer() {
		return server;
	}

	/**
	 * See if the underlying socket is connected or at least has a connection pending so
	 * will become connected in the immediate future. Basically, if not "connected" then
	 * need to reconnect.
	 * @return
	 */
	boolean isConnected() {
		return socketChannel.isConnected() || socketChannel.isConnectionPending();
	}
	
	/**
	 * Connects this processor to a new channel. The channel should be non-blocking.
	 * Note that this is appropriate when this message processor is being used on the
	 * client side of a SocketChannel.  
	 * 
	 * @param channel is the new channel to use
	 */
	protected void reconnectTo(SocketChannel channel) throws IOException {
		try {
			if (socketChannel.isOpen()) {
				socketChannel.close();
			}
		} catch (IOException e) {
			// TODO Log error
		}
		socketChannel = channel;
		inputBuffer.clear();

	
		// Don't know state of output queue if we're having to reconnect so clear it.
		// May have been a partial send and therefore state undefined.
		outputQueue.clear();
		
		processingThread.addChannel(channel, this);
	}

	@Override
	public void process(SelectionKey selectionKey) throws IOException {
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
					MessageHandler handler = handlers[msgType];
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

							handler.process(server, inputBuffer, this);

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
						System.err.println("MessageProcessor: No client message handler for type " + msgType);
						break;
					}

				} else {
					System.err.println("MessageProcessor: Message type out of range " + msgType);
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
		//System.out.println("MessageProcessor: On write");
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

				try {
					while (socketChannel.write(buffers) > 0) {
						// Remove any buffers that have been written from the output queue.
						while (!outputQueue.isEmpty() && !outputQueue.getFirst().hasRemaining()) {
							outputQueue.removeFirst();
						}
						if (outputQueue.isEmpty())
							break;
						buffers = outputQueue.toArray(new ByteBuffer[outputQueue.size()]);
					}
				} catch (IOException e) {
					// if the other end of this channel has closed or gone away (or any other 
					// network disconnection has happened then the write above will throw:
					// java.io.IOException: Broken pipe. As the other end has disconnected there's
					// little that can be done apart from log and tidy up.
					// TODO - log warning
					processingThread.removeChannel(socketChannel);
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
		//System.out.println("MessageProcessor: onConnect");
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

	

}
