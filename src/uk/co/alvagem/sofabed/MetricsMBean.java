package uk.co.alvagem.sofabed;

/**
 * Interface for JMX Metrics MBean
 * @author rbp28668
 *
 */
public interface MetricsMBean {

	
	/**
	 * Gets the number of nodes configured for this cluster.
	 * @return
	 */
	public int getConfiguredNodes();
	
	/**
	 * Gets number of nodes that are actually reachable (includes this one so never zero)
	 * @return
	 */
	public int getReachableNodes();
	
	/**
	 * Gets the time in mS for the main loop of the server
	 * @return
	 */
	public long getLoopTime();
	
	/**
	 * Get a smoothed average of the CLIENT write queue length
	 * @return
	 */
	public float getClientWriteQueueSmoothedLength();

	/**
	 * Get a smoothed average of the CLUSTER write queue length
	 * @return
	 */
	public float getClusterWriteQueueSmoothedLength();

}
