package uk.co.alvagem.sofabed.client;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import uk.co.alvagem.sofabed.CorrelationMap;
import uk.co.alvagem.sofabed.Key;
import uk.co.alvagem.sofabed.MessageStatus;
import uk.co.alvagem.sofabed.MessageType;
import uk.co.alvagem.sofabed.messages.client.ClientException;
import uk.co.alvagem.sofabed.messages.client.ClientMessage;
import uk.co.alvagem.sofabed.messages.client.ClientResponseMessage;

/**
 * Template for managing a client task. The client tasks are all pretty much the same
 * in that the client finds the list of nodes for a record, tries to get a response
 * from the server. If that fails (abort is called) then move onto the next node and try
 * again.
 * @author rbp28668
 *
 * @param <T>
 */
abstract  class ClientTaskBase <T> implements ClientTask, Future<T>  {
		private ClusterImpl cluster;
		
		private Iterator<ClusterNode> nodeIterator;
		private T result = null;
		private Semaphore lock = new Semaphore(0,true); 
		private boolean cancelled = false;
		private String errorState = null;
		
		protected ClientTaskBase(ClusterImpl cluster, String bucketName, Key key) throws IOException{
			this.cluster = cluster;
			nodeIterator = cluster.getTargetNodes(bucketName, key).iterator();
		}
		
		/**
		 * Call this at the end of the derived class constructor to start things off.  Split
		 * off from this class's constructor to give the derived class a chance to get their
		 * properties sorted out before sendTo is called.
		 * @throws IOException
		 */
		protected void start() throws IOException {
			if(nodeIterator.hasNext()) {
				sendTo(nodeIterator.next());
			} else {
				errorState = "No nodes to send to";
				lock.release();
			}
		}
		
		
		/**
		 * Send the appropriate message to the given node.
		 * @param node
		 * @throws IOException
		 */
		protected abstract void sendTo(ClusterNode node) throws IOException;
		
		
		/**
		 * Pick out the result from the client message.  
		 * @param message
		 * @return the result from the message.  This will be returned to the client
		 */
		protected abstract T getResultFrom(ClientResponseMessage message);
		
		
		/**
		 * Chance to validate an incoming message.
		 * @param message
		 * @throws IllegalArgumentException if invalid
		 */
		protected abstract void validateMessage(ClientMessage message) throws IllegalArgumentException;
		
		/**
		 * Gets the cluster implementation.
		 * @return
		 */
		protected ClusterImpl getCluster() {
			return cluster;
		}
		
		@Override
		public void abort(long correlationId) {
			// Timeout on message response so try other node(s) if possible.
			String err = null;
			boolean sent = false;
			while(nodeIterator.hasNext()) {
				try {
					sendTo(nodeIterator.next());
					sent = true;
					break;
				} catch (IOException e) {
					err = e.getMessage();
				}
			}
			
			// If not successfully sent to any then set error
			if(!sent) {
				errorState = (err == null) ? "No nodes to send to" : err;
				lock.release();
			}
			
		}

		@Override
		public void process(ClientMessage message) throws IOException {

			validateMessage(message);
			
			if(cancelled) {
				lock.release(); // just in case
				return;  // no further processing
			}
			
			ClientResponseMessage response = (ClientResponseMessage)message;
			MessageStatus status = response.getStatus();
			
			if(status == MessageStatus.OK) {
				result = getResultFrom(response);
				lock.release();
			} else {
				// something bad happened.  Log it and Try next node if possible
				System.out.println("Error: " + response.getStatus());
				// TODO log properly
				
				if(status.isRecoverable()) {
					if(nodeIterator.hasNext()) {
						sendTo(nodeIterator.next());
					} else { // no next node
						errorState = status.getMessage() + " and no more nodes";
					}
				} else { // not recoverable so die painfully
					errorState = status.getMessage();
					lock.release();
				}

			}
		}
		
		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			cancelled = true;
			return result == null;
		}
		
		@Override
		public boolean isCancelled() {
			return cancelled;
		}
		
		@Override
		public boolean isDone() {
			return cancelled || result != null;
		}
		
		@Override
		public T get() throws InterruptedException, ExecutionException {
			checkState();
			lock.acquire();  // will block if lock.release hasn't been called.
			checkState();
			return result;
		}

		@Override
		public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
			checkState();
			if(lock.tryAcquire(timeout, unit)) {  // will block if lock.release hasn't been called.
				checkState();
				return result;
			} else { // couldn't get a permit in the stated time period - i.e. timed out.
				throw new TimeoutException("Timed out waiting for response");
			}
		}

		private void checkState() throws ClientException {
			if(cancelled) {
				throw new CancellationException("task was cancelled");
			}

			if(errorState != null) {
				throw new ClientException(errorState);
			}
		}
		
		
		
	}


