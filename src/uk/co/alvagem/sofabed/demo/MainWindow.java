/**
 * 
 */
package uk.co.alvagem.sofabed.demo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

import uk.co.alvagem.sofabed.Key;
import uk.co.alvagem.sofabed.Metrics;
import uk.co.alvagem.sofabed.Server;
import uk.co.alvagem.sofabed.Settings;
import uk.co.alvagem.sofabed.Version;
import uk.co.alvagem.sofabed.client.Bucket;
import uk.co.alvagem.sofabed.client.Cluster;
import uk.co.alvagem.sofabed.client.Record;
/**
 * @author bruce.porteous
 *
 */
public class MainWindow extends JFrame {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Cluster cluster;
	private List<Server> servers;
	private ServerGroupPanel serverGroupPanel;
	private ClientPanel clientPanel;
	
	
	MainWindow(Cluster cluster, List<Server> servers, Settings settings) {
		this.cluster = cluster;
		this.servers = servers;

		setTitle("Cluster");
		setLayout(new BorderLayout());
		
		clientPanel = new ClientPanel(cluster);
		add(clientPanel, BorderLayout.CENTER);
		
		serverGroupPanel = new ServerGroupPanel(servers, settings);
		add(serverGroupPanel, BorderLayout.SOUTH);

		setDefaultCloseOperation(EXIT_ON_CLOSE);

		setLocation(100, 100);
		setSize(800, 600);

		pack();
		setVisible(true);
	}

	public void updateValues() {
		serverGroupPanel.updateValues();
	}

	private static class ClientPanel extends JPanel {
	
		private Cluster cluster;
		
		ClientPanel(Cluster cluster){
			this.cluster = cluster;
			
			setBorder(new LineBorder(Color.black));
			BoxLayout box = new BoxLayout(this, BoxLayout.Y_AXIS);
			setLayout(box);
			
			CreatePanel createPanel = new CreatePanel();
			add(createPanel);
			
			ReadWritePanel readWritePanel= new ReadWritePanel();
			add(readWritePanel);
			
			DeletePanel deletePanel = new DeletePanel();
			add(deletePanel);
			
			BucketPanel bucketPanel = new BucketPanel();
			add(bucketPanel);
			
		}
		
		
		private class CreatePanel extends JPanel {

			private static final long serialVersionUID = 1L;
			JTextField bucketField;
			JTextField keyField;
			JTextArea contentsField;
			JLabel versionField;
			
			CreatePanel(){
				setBorder(new TitledBorder( "Create"));
	
				GridBagLayout grid = new GridBagLayout();
				GridBagConstraints c = new GridBagConstraints();
				setLayout(grid);
				
				JLabel label;
				c.anchor = GridBagConstraints.LINE_START;
				c.weighty = 1.0;
				c.insets = new Insets(5, 10, 5, 10);
				c.gridwidth = 1;
				
				label = new JLabel("Bucket");
				bucketField = new JTextField(40);
				grid.setConstraints(label, c);
				add(label);
				grid.setConstraints(bucketField, c);
				add(bucketField);

				label = new JLabel("Key");
				keyField = new JTextField(40);
				grid.setConstraints(label, c);
				add(label);
				c.gridwidth = GridBagConstraints.REMAINDER;
				grid.setConstraints(keyField, c);
				add(keyField);
				
				c.gridwidth = 1;
				label = new JLabel("Contents");
				contentsField = new JTextArea(5,40);
				grid.setConstraints(label, c);
				add(label);
				grid.setConstraints(contentsField, c);
				add(contentsField);
				
				label = new JLabel("Version");
				versionField = new JLabel("");
				grid.setConstraints(label, c);
				add(label);
				c.gridwidth = GridBagConstraints.REMAINDER;
				grid.setConstraints(versionField, c);
				add(versionField);
				
				JButton create = new JButton("Create");
				create.addActionListener(new ActionListener() {
					
					@Override
					public void actionPerformed(ActionEvent e) {
						try {
							String b = bucketField.getText();
							String k = keyField.getText();
							String c = contentsField.getText();
							
							Bucket bucket = cluster.getBucket(b);
							Record record = new Record(new Key(k), Version.NONE, c.getBytes("UTF-8") );
							Future<Version> fv = bucket.create(record);
							Version v = fv.get();
							versionField.setText(v.toString());
							
						} catch (Exception x) {
							JOptionPane.showMessageDialog(CreatePanel.this,x.getMessage(), "Sofabed Exception", JOptionPane.ERROR_MESSAGE);
							x.printStackTrace();
						} 
						
					}
				});
				c.gridwidth = GridBagConstraints.REMAINDER;
				grid.setConstraints(create, c);
				add(create);

				JButton createMany = new JButton("1000");
				createMany.addActionListener(new ActionListener() {
					
					@Override
					public void actionPerformed(ActionEvent e) {
						try {
							createRecords(1000);
						} catch (Exception x) {
							JOptionPane.showMessageDialog(CreatePanel.this,x.getMessage(), "Sofabed Exception", JOptionPane.ERROR_MESSAGE);
							x.printStackTrace();
						} 
						
					}
				});
				c.gridwidth = GridBagConstraints.REMAINDER;
				grid.setConstraints(createMany, c);
				add(createMany);

				
			}
			
			private void createRecords(int number) throws Exception {
				String b = bucketField.getText();
				for(int i=0; i<number; ++i) {
					String k = UUID.randomUUID().toString();
					String c = k + " " + Integer.toString(i) + " of " + Integer.toString(number);
					
					Bucket bucket = cluster.getBucket(b);
					Record record = new Record(new Key(k), Version.NONE, c.getBytes("UTF-8") );
					Future<Version> fv = bucket.create(record);
					Version v = fv.get();
					versionField.setText(v.toString());
					
				}
			}
			
		}
		
		private class ReadWritePanel extends JPanel {
			
			JTextField bucketField;
			JTextField keyField;
			JTextArea contentsField;
			JTextField versionField;
			
			ReadWritePanel(){
				setBorder(new TitledBorder("Read/Write"));
				GridBagLayout grid = new GridBagLayout();
				GridBagConstraints c = new GridBagConstraints();
				setLayout(grid);
				
				JLabel label;
				c.anchor = GridBagConstraints.LINE_START;
				c.weighty = 1.0;
				c.insets = new Insets(5, 10, 5, 10);
				c.gridwidth = 1;
				
				label = new JLabel("Bucket");
				grid.setConstraints(label, c);
				add(label);
				bucketField = new JTextField(40);
				grid.setConstraints(bucketField, c);
				add(bucketField);

				label = new JLabel("Key");
				keyField = new JTextField(40);
				grid.setConstraints(label, c);
				add(label);
				c.gridwidth = GridBagConstraints.REMAINDER;
				grid.setConstraints(keyField, c);
				add(keyField);
				
				label = new JLabel("Contents");
				c.gridwidth = 1;
				grid.setConstraints(label, c);
				add(label);
				contentsField = new JTextArea(5,40);
				grid.setConstraints(contentsField, c);
				add(contentsField);
				
				label = new JLabel("Version");
				grid.setConstraints(label, c);
				add(label);
				versionField = new JTextField(40);
				c.gridwidth = GridBagConstraints.REMAINDER;
				grid.setConstraints(versionField, c);
				add(versionField);
				
				JButton read = new JButton("Read");
				read.addActionListener(new ActionListener() {
					
					@Override
					public void actionPerformed(ActionEvent e) {
						try {
							String b = bucketField.getText();
							String k = keyField.getText();
								
							Bucket bucket = cluster.getBucket(b);
							Future<Record> fr = bucket.read(new Key(k));
							Record r = fr.get();
							contentsField.setText( new String(r.getPayload(), "UTF-8"));
							versionField.setText( r.getVersion().toString());
						} catch (Exception x) {
							JOptionPane.showMessageDialog(ReadWritePanel.this,x.getMessage(), "Sofabed Exception", JOptionPane.ERROR_MESSAGE);
							x.printStackTrace();
						} 
					}
				});
				
				c.gridwidth = 1;
				grid.setConstraints(read, c);
				add(read);
				
				
				JButton update = new JButton("Update");
				update.addActionListener(new ActionListener() {
					
					@Override
					public void actionPerformed(ActionEvent e) {
						try {
							String b = bucketField.getText();
							String k = keyField.getText();
							String c = contentsField.getText();
							long ver = Long.parseLong(versionField.getText());
							
							Bucket bucket = cluster.getBucket(b);
							Record record = new Record(new Key(k), new Version(ver), c.getBytes("UTF-8") );
							Future<Version> fv = bucket.write(record);
							Version v = fv.get();
							versionField.setText(v.toString());
						} catch (Exception x) {
							JOptionPane.showMessageDialog(ReadWritePanel.this,x.getMessage(), "Sofabed Exception", JOptionPane.ERROR_MESSAGE);
							x.printStackTrace();
						} 
					}
				});
				
				c.gridwidth = GridBagConstraints.REMAINDER;
				grid.setConstraints(update, c);
				add(update);
			}
		}
		
		private class DeletePanel extends JPanel {

			
			JTextField bucketField;
			JTextField keyField;
			JTextField versionField;

			DeletePanel(){
				setBorder(new TitledBorder("Delete"));
				GridBagLayout grid = new GridBagLayout();
				GridBagConstraints c = new GridBagConstraints();
				setLayout(grid);
				
				JLabel label;
				c.anchor = GridBagConstraints.LINE_START;
				c.weighty = 1.0;
				c.insets = new Insets(5, 10, 5, 10);
				c.gridwidth = 1;

				label = new JLabel("Bucket");
				grid.setConstraints(label, c);
				add(label);
				bucketField = new JTextField(40);
				grid.setConstraints(bucketField, c);
				add(bucketField);

				label = new JLabel("Key");
				keyField = new JTextField(40);
				grid.setConstraints(label, c);
				add(label);
				c.gridwidth = GridBagConstraints.REMAINDER;
				grid.setConstraints(keyField, c);
				add(keyField);
				
				
				label = new JLabel("Version");
				c.gridwidth = 1;
				grid.setConstraints(label, c);
				add(label);
				versionField = new JTextField(40);
				c.gridwidth = GridBagConstraints.REMAINDER;
				grid.setConstraints(versionField, c);
				add(versionField);
				
				JButton delete = new JButton("Delete");
				delete.addActionListener(new ActionListener() {
					
					@Override
					public void actionPerformed(ActionEvent e) {
						try {
							String b = bucketField.getText();
							String k = keyField.getText();
							long ver = Long.parseLong(versionField.getText());
							
							Bucket bucket = cluster.getBucket(b);
							Future<Void> fv = bucket.delete(new Key(k), new Version(ver));
							fv.get();
						} catch (Exception x) {
							JOptionPane.showMessageDialog(DeletePanel.this,x.getMessage(), "Sofabed Exception", JOptionPane.ERROR_MESSAGE);
						} 
						
					}
				});
				add(delete);
			}
		}
		
		private class BucketPanel extends JPanel {
			
			private JTextField bucketName;
			
			BucketPanel(){
				setBorder(new TitledBorder("Buckets"));
				GridBagLayout grid = new GridBagLayout();
				GridBagConstraints c = new GridBagConstraints();
				setLayout(grid);
				
				JLabel label;
				c.anchor = GridBagConstraints.LINE_START;
				c.weighty = 1.0;
				c.insets = new Insets(5, 10, 5, 10);
				c.gridwidth = 1;

				label = new JLabel("Bucket");
				grid.setConstraints(label, c);
				add(label);
				bucketName = new JTextField(40);
				c.gridwidth = GridBagConstraints.REMAINDER;
				grid.setConstraints(bucketName, c);
				add(bucketName);

				
				JButton create = new JButton("Create");
				create.addActionListener(new ActionListener() {
					
					@Override
					public void actionPerformed(ActionEvent e) {
						// TODO Auto-generated method stub
						
					}
				});
				c.gridwidth = GridBagConstraints.REMAINDER;
				grid.setConstraints(create, c);
				add(create);
			}
		}
	}
	
	
	private static class ServerGroupPanel extends JPanel {

		private List<ServerPanel> panels = new LinkedList<>();
		
		ServerGroupPanel(List<Server> servers, Settings settings) {
			setBorder(new LineBorder(Color.black));
			BoxLayout box = new BoxLayout(this, BoxLayout.X_AXIS);
			setLayout(box);

			int idx = 0;
			for (Server server : servers) {
				ServerPanel sp = new ServerPanel(server, settings, idx++);
				add(sp);
				panels.add(sp);
			}
		}
		
		void updateValues() {
			for(ServerPanel panel : panels) {
				panel.updateValues();
			}
		}
	}

	private static class ServerPanel extends JPanel {

		Server server;
		Settings settings;
		int index;
		
		JLabel thisNodeName;
		JLabel thisNodeId;
		JLabel activeNodeCount;
		JLabel thisNodeClientPort;
		JLabel thisNodeClusterPort;
		JLabel configuredNodes;
		JLabel clusterWriteQueueLength;
		JLabel clientWriteQueueLength;
		JButton startStopButton;
		
		public ServerPanel(Server server, Settings settings, int idx) {
			this.server = server;
			this.settings = settings;
			this.index = idx;
			
			setBorder(new LineBorder(Color.black));

			GridBagLayout grid = new GridBagLayout();
			GridBagConstraints c = new GridBagConstraints();

			setLayout(grid);

			c.anchor = GridBagConstraints.LINE_START;
			c.weighty = 1.0;
			c.insets = new Insets(5, 10, 5, 10);

			uk.co.alvagem.sofabed.Cluster cluster = server.getCluster();

			thisNodeName = new JLabel();
			add("Node name", thisNodeName, grid, c);

			thisNodeId = new JLabel();
			add("Node id", thisNodeId, grid, c);

			activeNodeCount = new JLabel();
			add("Active node count", activeNodeCount, grid, c);

			thisNodeClientPort = new JLabel();
			add("Client port", thisNodeClientPort, grid, c);

			thisNodeClusterPort = new JLabel();
			add("Cluster port", thisNodeClusterPort, grid, c);

			configuredNodes = new JLabel();
			add("Configured Nodes", configuredNodes, grid, c);

			clusterWriteQueueLength = new JLabel();
			add("Cluster write queue length", clusterWriteQueueLength, grid, c);

			clientWriteQueueLength = new JLabel();
			add("Client write queue length", clientWriteQueueLength, grid, c);

			JButton startStopButton = new JButton("Stop");
			c.gridwidth = GridBagConstraints.REMAINDER;
			grid.setConstraints(startStopButton, c);
			add(startStopButton);
			server = null;
			startStopButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					if(isRunning()) {
						shutdown();
						startStopButton.setText("Start");
					} else {
						startup();
						startStopButton.setText("Stop");
					}
				}

			});
			updateValues();
		}

		private boolean isRunning() {
			return server != null;
		}
		
		private void shutdown() {
			server.shutdown();
			server = null;
		}
		
		private void startup() {
			try {
				server = new Server(settings, index);
				Thread thread = new Thread(server, "SofabedServer" + Integer.toString(index));
				System.out.println("restarting " + thread.getName());
				thread.start();
			} catch (IOException e) {
				JOptionPane.showMessageDialog(ServerPanel.this,e.getMessage(), "Sofabed Exception", JOptionPane.ERROR_MESSAGE);
				e.printStackTrace();
			}
		}

		private void add(String text, JLabel value, GridBagLayout grid, GridBagConstraints c) {
			JLabel label = new JLabel(text);
			c.gridwidth = GridBagConstraints.RELATIVE;
			grid.setConstraints(label, c);
			add(label);

			c.gridwidth = GridBagConstraints.REMAINDER;
			grid.setConstraints(value, c);
			add(value);
		}

		void updateValues() {
			if(server != null) {
				uk.co.alvagem.sofabed.Cluster cluster = server.getCluster();
				activeNodeCount.setText(Integer.toString(cluster.activeNodeCount()));
				thisNodeName.setText(cluster.thisNodeName());
				
				thisNodeId.setText(Integer.toString(cluster.thisNodeId()));
				thisNodeClientPort.setText(Integer.toString(cluster.thisNodeClientPort()));
				thisNodeClusterPort.setText(Integer.toString(cluster.thisNodeClusterPort()));
	
				Metrics metrics = server.getMetrics();
	
				configuredNodes.setText(Integer.toString(metrics.getConfiguredNodes()));
				clusterWriteQueueLength.setText(Float.toString(metrics.getClusterWriteQueueSmoothedLength()));
				clientWriteQueueLength.setText(Float.toString(metrics.getClientWriteQueueSmoothedLength()));
			} else {
				activeNodeCount.setText("---");
				//thisNodeName.setText(cluster.thisNodeName());
				thisNodeId.setText("---");
				thisNodeClientPort.setText("---");
				thisNodeClusterPort.setText("---");
				configuredNodes.setText("---");
				clusterWriteQueueLength.setText("---");
				clientWriteQueueLength.setText("---");
				
			}
		}
	}

}
