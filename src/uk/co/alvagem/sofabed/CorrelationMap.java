package uk.co.alvagem.sofabed;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Use this to track tasks which are waiting for reply messages with
 * given correlation IDs. Also tracks time when tasks are created so
 * orphans caused by comms failures etc can be cleared up eventually.
 * 
 * @author rbp28668
 *
 */
public class CorrelationMap {

	private final Map<Long, TaskRecord> tasks = new ConcurrentHashMap<>();
	
	void addTask(long id, Task task) {
		TaskRecord record = new TaskRecord(id, task);
		tasks.put(id, record);
	}
	
	boolean containsTask(long id) {
		return tasks.containsKey(id);
	}
	
	Task getAndRemoveTask(long id) {
		Long l = Long.valueOf(id);
		Task t = tasks.remove(l).task;
		if(t == null) {
			throw new IllegalStateException("Task Id not known");
		}
		return t;
	}
	
	
	boolean hasOldTasks(int mSAgo) {
		long threshold = System.currentTimeMillis() - mSAgo;
		for(TaskRecord entry : tasks.values()) {
			if(entry.timestamp < threshold) {
				return true;
			}
		}
		return false;
	}
	
	int removeOldTasks(int mSAgo) {
		int count = 0;
		long threshold = System.currentTimeMillis() - mSAgo;
		List<Long> toRemove = new LinkedList<>();
		for(TaskRecord entry : tasks.values()) {
			if(entry.timestamp < threshold) {
				++count;
				Task task = entry.task;
				task.abort(entry.id);
				toRemove.add(entry.id);
				// TODO log task abortion
				String msg = "Server timeout #" + entry.id + " to " + entry.task.getClass().getSimpleName();
				System.out.println(msg);
			}
		}
		for(Long id : toRemove) {
			tasks.remove(id);
		}
		return count;
	}

	/**
	 * Hold all the information about a class.
	 * @author rbp28668
	 *
	 */
	private static class TaskRecord {
		final long id;
		final Task task;
		final long timestamp;
		
		public TaskRecord(long id, Task task) {
			this.id = id;
			this.task = task;
			this.timestamp = System.currentTimeMillis();
		}
		
	}
}
