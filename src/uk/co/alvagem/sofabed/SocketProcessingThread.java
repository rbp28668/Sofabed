package uk.co.alvagem.sofabed;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

public class SocketProcessingThread implements Runnable {

	private final Selector selector;
	private boolean terminate = false;
	private final LinkedList<RegistrationQueueNode> registrations = new LinkedList<>();
	private final LinkedList<MessageProcessor> pendingWrites = new LinkedList<>();
	private final LinkedList<SocketChannel> pendingRemoves = new LinkedList<>();
	
	SocketProcessingThread() throws IOException{
		this.selector = Selector.open();
	}
	
	/**
	 * Adds a channel to this processing thread - really just ties it to the selector.
	 * Minor snag is that you can't register a channel with a selector when the selector
	 * is blocked in select.  Hence we put the params on a queue and unblock the
	 * selector with wakeup().  Not at all obvious!
	 * @param channel
	 * @param processor
	 * @throws IOException
	 */
	void addChannel(SocketChannel channel, MessageProcessor processor) throws IOException{
		channel.configureBlocking(false);
		synchronized(registrations) {
			registrations.add(new RegistrationQueueNode(channel, processor));
		}
		selector.wakeup();
	}
	
	/**
	 * Removes the channel associated with a message processor. Used when the remote end of a connection
	 * is closed to tidy up the message processor etc.
	 * @param messageProcessor
	 */
	public void removeChannel(SocketChannel socketChannel) {
		synchronized(pendingRemoves) {
			pendingRemoves.add(socketChannel);
		}
		selector.wakeup();
	}

	Selector getSelector() {
		return selector;
	}
	
	/**
	 * Call when the supplied message processor has something to write. It's channel
	 * will of OP_WRITE enabled so that the selector enables the write.
	 * @param mp
	 */
	void setPendingWrite(MessageProcessor mp) {
		synchronized(pendingWrites) {
			pendingWrites.add(mp);
		}
		selector.wakeup();
	}
	
	@Override
	public void run() {
		while(!terminate) {
			try {
				if(selector.select() > 0) {
					//System.out.println("SocketProcessingThread: selected");
					Set<SelectionKey> selectedKeys = selector.selectedKeys();
					Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
					
					while(keyIterator.hasNext()) {
					    SelectionKey selectionKey = keyIterator.next();		
						ChannelHandler handler = (ChannelHandler)selectionKey.attachment();
						handler.process(selectionKey);
						keyIterator.remove();
					}
				}
				
				// While the selector isn't blocked we can register any new channels. Read
				// these off the registrations list.
				synchronized(registrations) {
					for(RegistrationQueueNode rqn : registrations) {
						if(rqn.channel.isConnected()) {
							//System.out.println("SocketProcessingThread.addChannel: Channel already connected " + rqn.channel);
							rqn.channel.register(selector, SelectionKey.OP_READ, rqn.processor);
						} else {
							//System.out.println("SocketProcessingThread.addChannel: Registering channel for OP_CONNECT " + rqn.channel);
							rqn.channel.register(selector, SelectionKey.OP_CONNECT, rqn.processor);
						}
					}
					registrations.clear();
				}
				
				synchronized(pendingWrites) {
					for(MessageProcessor mp : pendingWrites) {
						// Register a write interest now the processor has something to write.
						// If not connected this should be picked up when it's connectable.
						mp.enableWrite(selector);
					}
					pendingWrites.clear();
				}

				synchronized(pendingRemoves) {
					for(SocketChannel sc : pendingRemoves) {
						SelectionKey sk = sc.keyFor(selector);
						sk.cancel();
						sc.close();
					}
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		try {
			selector.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	void shutdown() {
		terminate = true;
		selector.wakeup();
	}

	/**
	 * Track new channels/processor for registration on this threads selector.
	 * @author rbp28668
	 *
	 */
	private static class RegistrationQueueNode {
		final SocketChannel channel;
		final MessageProcessor processor;
		
		RegistrationQueueNode(SocketChannel channel, MessageProcessor processor){
			this.channel = channel;
			this.processor = processor;
		}
	}

}
