package uk.co.alvagem.sofabed;

/**
 * Implement a keepalive Ping between servers.
 * 
 * @author rbp28668
 *
 */
public class PingThread implements Runnable {

	private final Server server;
	private boolean shutdown = false;
	private Thread thread;
	
	PingThread(Server server){
		this.server = server;
	}
	
	
	@Override
	public void run() {
		thread = Thread.currentThread(); // save so we can shutdown.
		while(!shutdown) {
			try {
				Cluster cluster = server.getCluster();
				cluster.pingNodes();
				// Put sleep in here to give nodes time to respond before checking
				Thread.sleep(server.getSettings().getPingIntervalMs());
				cluster.checkNodesActive();
			} catch(InterruptedException ix) {
				// TODO log as info - normal part of shutdown process.
			} catch (Exception e) {
			
				// TODO Log error
				e.printStackTrace();
			}
		}

	}


	public void shutdown() {
		shutdown = true;
		thread.interrupt();
	}

}
