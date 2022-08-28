package uk.co.alvagem.sofabed;

import java.lang.management.ManagementFactory;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * Expose metrics via JMX
 * 
 * @author rbp28668
 *
 */
public class Metrics implements MetricsMBean {

	private long loopTime;
	private int reachableNodes;
	private int configuredNodes;
	private float clientWriteQueueLength = 0;
	private float clusterWriteQueueLength = 0;

	private final static float SMOOTH = 8;
	
	Metrics() {
	}

	void register(String name) throws JMException {
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		// register the MBean
		String objectName = getClass().getPackage().getName() + ":type=" + getClass().getSimpleName() + ",name=" + name;
		System.out.println(objectName);
		ObjectName oname = new ObjectName(objectName);
		mbs.registerMBean(this, oname);
	}

	public void setLoopTime(long loopTime) {
		this.loopTime = loopTime;
	}

	public void setReachableNodes(int reachableNodes) {
		this.reachableNodes = reachableNodes;
	}

	public void setConfiguredNodes(int configuredNodes) {
		this.configuredNodes = configuredNodes;
	}

	@Override
	public int getConfiguredNodes() {
		return configuredNodes;
	}

	@Override
	public int getReachableNodes() {
		return reachableNodes;
	}

	@Override
	public long getLoopTime() {
		return loopTime;
	}

	@Override
	public float getClientWriteQueueSmoothedLength() {
		return clientWriteQueueLength;
	}

	@Override
	public float getClusterWriteQueueSmoothedLength() {
		return clusterWriteQueueLength;
	}

	public void updateClientWriteQueueLength(int len) {
		clientWriteQueueLength *= SMOOTH;
		clientWriteQueueLength += len;
		clientWriteQueueLength /= (SMOOTH + 1);
		
	}

	public void updateClusterWriteQueueLength(int len) {
		clusterWriteQueueLength *= SMOOTH;
		clusterWriteQueueLength += len;
		clusterWriteQueueLength /= (SMOOTH + 1);
	}

}
