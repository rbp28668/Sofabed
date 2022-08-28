package uk.co.alvagem.sofabed.client;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Use this to track ClientTasks which are waiting for reply messages with
 * given correlation IDs. Also tracks time when ClientTasks are created so
 * orphans caused by comms failures etc can be cleared up eventually.
 * Implements Runnable so can be its own tidy-up thread.
 * 
 * @author rbp28668
 *
 */
public class ClientCorrelationMap implements Runnable{

	private final Map<Long, ClientTaskRecord> clientTasks = new ConcurrentHashMap<>();
	private boolean stop = false;
	
	void addClientTask(long id, ClientTask ClientTask) {
		ClientTaskRecord record = new ClientTaskRecord(id, ClientTask);
		clientTasks.put(id, record);
	}
	
	boolean containsClientTask(long id) {
		return clientTasks.containsKey(id);
	}
	
	ClientTask getAndRemoveClientTask(long id) {
		Long l = Long.valueOf(id);
		if(!clientTasks.containsKey(l)) {
			throw new IllegalStateException("ClientTask Id " + id + " not known");
		}
		ClientTask ct = clientTasks.get(l).ClientTask;
		clientTasks.remove(l);
		return ct;
	}
	
	boolean hasOldClientTasks(int mSAgo) {
		long threshold = System.currentTimeMillis() - mSAgo;
		for(ClientTaskRecord entry : clientTasks.values()) {
			if(entry.timestamp < threshold) {
				return true;
			}
		}
		return false;
	}
	
	int removeOldClientTasks(int mSAgo) {
		int count = 0;
		long threshold = System.currentTimeMillis() - mSAgo;
		List<Long> toRemove = new LinkedList<>();
		for(ClientTaskRecord entry : clientTasks.values()) {
			if(entry.timestamp < threshold) {
				++count;
				ClientTask clientTask = entry.ClientTask;
				clientTask.abort(entry.id);
				toRemove.add(entry.id);
				String msg = "Client timeout #" + entry.id + " to " + entry.ClientTask.getClass().getSimpleName();
				System.out.println(msg);
				// TODO log ClientTask abortion
			}
		}
		for(Long id : toRemove) {
			clientTasks.remove(id);
		}
		return count;
	}

	
	
	@Override
	public void run() {
		while(!stop) {
			try {
				removeOldClientTasks(2000);
				Thread.sleep(250);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	void shutdown() {
		stop = true;
	}

	/**
	 * Hold all the information about a class.
	 * @author rbp28668
	 *
	 */
	private static class ClientTaskRecord {
		final long id;
		final ClientTask ClientTask;
		long timestamp;
		
		public ClientTaskRecord(long id, ClientTask ClientTask) {
			this.id = id;
			this.ClientTask = ClientTask;
			this.timestamp = System.currentTimeMillis();
		}
		
	}
}
