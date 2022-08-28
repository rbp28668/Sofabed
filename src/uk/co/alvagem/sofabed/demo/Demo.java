package uk.co.alvagem.sofabed.demo;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;

import uk.co.alvagem.sofabed.Server;
import uk.co.alvagem.sofabed.Settings;
import uk.co.alvagem.sofabed.client.Client;
import uk.co.alvagem.sofabed.client.Cluster;

public class Demo {

	
	///////////////////////////////////////////////////////////////////////////////
	// Main - server startup
	///////////////////////////////////////////////////////////////////////////////

	public static void main(String[] args) {
		try {
			int localNodes = 3;
			int clusterPort = Settings.Node.CLUSTER_PORT;
			int clientPort = Settings.Node.CLIENT_PORT;
			
			for(String arg : args) {
				
			}

			Settings settings = new Settings();  // empty
			
			
			for(int i=0; i<localNodes; ++i) {
				Settings.Node node = new Settings.Node();
				node.setClientPort(clientPort);
				node.setClusterPort(clusterPort);
				node.setNodeName("Node" + Integer.toString(i));
				settings.addNode(node);
				++clientPort;
				++clusterPort;
			}
			
			Settings.Bucket defaultBucket = new Settings.Bucket("default");
			settings.addBucket(defaultBucket);
			
			// Fire up the main processing threads for all the servers that
			// should be running on this hardware node.
			List<Thread> threads = new LinkedList<Thread>();	
			List<Server> servers = new LinkedList<Server>();
			for(int i=0; i<localNodes; ++i) {
				Server server = new Server(settings, i);
				servers.add(server);
				Thread thread = new Thread(server, "SofabedServer" + Integer.toString(i));
				threads.add(thread);
				System.out.println("starting " + thread.getName());
				thread.start();
			}

			Thread.sleep(1000);
			
			InetSocketAddress[] addresses = new InetSocketAddress[1];
			addresses[0] = new InetSocketAddress(InetAddress.getLocalHost(), Settings.Node.CLIENT_PORT);
			Client client = Client.getClient();
			client.connect(addresses);

			Cluster cluster = client.getClusterBlocking();
			
			MainWindow mainWindow = new MainWindow(cluster, servers, settings);

			UpdateThread updater = new UpdateThread(mainWindow);
			new Thread(updater,"Window Updater").start();

			
			// Wait for all the threads to die.
//			for(Thread thread : threads) {
//				thread.join();
//			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


}
