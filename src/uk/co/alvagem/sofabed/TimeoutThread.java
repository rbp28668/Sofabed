package uk.co.alvagem.sofabed;

public class TimeoutThread implements Runnable {

	private CorrelationMap tasks;
	private boolean stop = false;
	
	TimeoutThread(CorrelationMap tasks){
		this.tasks = tasks;
	}
	
	@Override
	public void run() {
		while(!stop) {
			try {
				tasks.removeOldTasks(2000); // 2 second timeout
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
}
