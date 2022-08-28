package uk.co.alvagem.sofabed;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import uk.co.alvagem.sofabed.messages.server.ServerMessage;
import uk.co.alvagem.sofabed.messages.server.ServerReadMessage;
import uk.co.alvagem.sofabed.messages.server.ServerReadResponse;
import uk.co.alvagem.sofabed.messages.server.ServerRecoveryMessage;
import uk.co.alvagem.sofabed.messages.server.ServerRecoveryResponse;

/**
 * Runnable thread initiated in response to another server asking to recover and to
 * handle the responses on the initiating server.
 * Call run() to send all the response messages back to the initiating server.  The
 * initating server needs to pass the responses into receiveRecoveryResponse - subsequent
 * messages to get the payload for each record are handled in the usual request/response
 * fashion each with their own correlation ID.
 * Note - there are really 2 classes waiting to get out but it's a significant refactoring
 * so left alone for the time being at least.
 * 
 * @author rbp28668
 *
 */
public class RecoveryThread extends TaskBase implements Task, Runnable {

	private int thisNodeId;
	private int quorumCount;
	private int targetNodeId;
	private long correlationId;
	
	private ConcurrentHashMap<String, BucketVersionInfo> buckets = new ConcurrentHashMap<>();
	private ConcurrentHashMap<Long, VersionInfo> requested = new ConcurrentHashMap<>();
	private Set<Long> currentRequests = new HashSet<>();
	private final Random rand = new Random();
	/**
	 * Constructor to set up the recovery thread in response to a ServerRecoveryMessage.
	 * Use in conjunction with run().
	 * @param server
	 * @param request
	 * @param processor
	 */
	public RecoveryThread(Server server, ServerRecoveryMessage request, ServerMessageProcessor processor) {
		super(server, processor);
		targetNodeId = request.getNodeId();
		correlationId = request.getCorrelationId();
		
		thisNodeId = server.getCluster().thisNodeId();
		quorumCount = server.getSettings().getQuorumCount();
	}

	
	/**
	 * Constructor to set up the task flavour that receives the messages.  Responses should arrive
	 * via receiveRecoveryResponse.
	 * @param server
	 */
	public RecoveryThread(Server server) {
		super(server, null);
	}
	
	
	//  Send out response messages for all records this node has that the requesting node (identified
	// by targetNodeId) should have. 
	@Override
	public void run() {
	
		int replicaCount = server.getSettings().getReplicaCount();
		Cluster cluster = server.getCluster();
		
		try {
			for(Bucket bucket : server.getBuckets()) {
				
				String bucketName = bucket.getName();
				
				for(Document doc : bucket.getDocuments()) {
					Key key = doc.getKey();
					Version v = doc.getVersion();
					
					ArrayList<NodeScore> nodeList = nodeListFor(bucketName,  key, cluster);
					
					boolean toInclude = false;
					int idx = 0;
					for(NodeScore ns : nodeList) {
						++idx;
						if(idx > replicaCount) {
							break;
						}
						
						if(ns.getNode().nodeId == targetNodeId) {
							toInclude = true;
							break;
						}
					}
					
					if(toInclude) {
						ServerRecoveryResponse response = new ServerRecoveryResponse(bucketName, key, v, thisNodeId, correlationId);
						returnChannel.write(response.getBuffer());
					}
				}
			}
			
			ServerRecoveryResponse response = new ServerRecoveryResponse("", new Key(""), Version.NONE, thisNodeId, correlationId);
			returnChannel.write(response.getBuffer());

		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
	/**
	 * Called when initiating a recovery to log the correlationId used to send the initial 
	 * recovery message to another node.  Use this to identify when each node has finished
	 * sending the recovery response messages.
	 * @param correlationId
	 */
	public void addInitialCorrelationId(long correlationId) {
		synchronized(currentRequests) {
			currentRequests.add(correlationId);
		}
		
	}

	/**
	 * Receive a response message from another node.  Message will provide a bucket/key/version
	 * tuple.  This check the version to see if its quorate and if so asks the first node
	 * to respond for the document payload.
	 * @param response
	 * @throws IOException
	 */
	void receiveRecoveryResponse(ServerRecoveryResponse response) {
		if(response.getVersion().equals(Version.NONE)) {
			synchronized(currentRequests) {
				currentRequests.remove(response.getCorrelationId());
				if(currentRequests.isEmpty()) {
					// This means that all the recovery responses have been received.  There may
					// be some pending payload requests to receive.  If there are any records that
					// haven't requested the payload (not quorate) there's a problem with recovery.
					boolean outstandingRecords = false;
					for(BucketVersionInfo bvi : buckets.values()) {
						for(RecordInfo ri : bvi.recordInfo.values()) {
							outstandingRecords = outstandingRecords || !ri.requested;
						}
					}
					
					if(outstandingRecords) {
						// TODO  - recovery failed - log ERROR
						System.out.println("Recovery failed");
					} else {
						// TODO - set change of state.
						System.out.println("Recovery phase 1 complete");
					}
				}
			}
		} else {
			
			String bucketName = response.getBucketName();
			Key key = response.getKey();
			
			BucketVersionInfo bucket = buckets.get(bucketName);
			if(bucket == null) {
				bucket = new BucketVersionInfo(bucketName);
				buckets.put(bucketName, bucket);
			}
			
			RecordInfo record = bucket.add(response.getKey(), response.getVersion(), response.getNodeId());
			
			// If we haven't got a quorum of responses we can't go further
			if(record.versionInfo.size() < quorumCount) {
				return;
			}
			
			// If already processed then return.
			if(record.requested) {
				return;
			}
			
			// Must be at least quorumCount versions recorded
			// Usual case is that the versions match so this will run through
			// the list just once.  Double loop for higher replica counts 
			// (unusual but not impossible).
			
			int count = 0;
			int size = record.versionInfo.size();
			VersionInfo targetVersion = null;
			boolean found = false;
			
			for(int i=0; i<size; ++i) {
				count = 1; // include the i-th record.
				targetVersion = record.versionInfo.get(i);
				for(int j=i+1; j<size; ++j) {
					if(record.versionInfo.get(j).version.equals(targetVersion.version)) {
						++count;
					}
				}
				if(count >= quorumCount) {
					found = true; // and target version has the quorate version
					break;
				}
			}
			
			if(!found) {
				return;
			}
			
			// randomly pick a node to fetch from but make sure it has the correct
			// version and change if need be. Spreads the fetch load more evenly 
			// over the other nodes.
			int idx = rand.nextInt(size); 
			VersionInfo fetchVersionInfo = record.versionInfo.get(idx);
			while(!fetchVersionInfo.version.equals(targetVersion.version)) {
				idx = (idx + 1) % size;
				fetchVersionInfo = record.versionInfo.get(idx);
			}

			record.requested = true; // mark as processed.
			fetchRecordData(bucketName, key, fetchVersionInfo);
		}
	}

	private void fetchRecordData(String bucketName, Key key, VersionInfo targetVersion)  {
		try {
			long correlationId = server.nextCorrelationId();
			server.addTask(correlationId, this);
			requested.put(correlationId, targetVersion);
			
			ServerReadMessage request = new ServerReadMessage(correlationId, bucketName, key);
			
			int nodeId = targetVersion.nodeId; // Could select a random node that has the correct version but just pick the first now.
			System.out.println("Fetching " + key + " from node " + nodeId + " with cid " + correlationId);
			ClusterNode node = server.getCluster().getNodeById(nodeId);
			node.send(request);
			targetVersion.requested = targetVersion.record.requested = true;
		} catch (IOException e) {
			// TODO log error - should never happen.
		}
	}
	
	
	// This will be a timeout on requesting a payload. If possible try
	// another node.
	@Override
	public void abort(long correlationId) {
		// TODO log ERROR
		VersionInfo info = requested.remove(correlationId);
		RecordInfo record = info.record;
		System.out.println("Failed to retrieve " + record.key.toString() + " with cid " + correlationId);
		// If this request has timed out it may be possible to get the data from another
		// node.  So look for one that hasn't requested and has the right version.
		for(VersionInfo vi : record.versionInfo) {
			if(vi.requested) {
				continue;
			}
			
			if(info.version.equals(vi.version)) { // it's the version we want
				String bucketName = record.bucket.name;
				fetchRecordData(bucketName, record.key, vi);
			}
		}
		
	}

	@Override
	public void process(ServerMessage message) throws IOException {
		// TODO Auto-generated method stub
		
		// Expecting a ServerReadResponse
		if(message.getType() == MessageType.SVR_READ_RESPONSE.getCode()) {
			try {
				ServerReadResponse response = (ServerReadResponse)message;
				
				long cid = response.getCorrelationId();
				
				System.out.println("Received ServerReadResponse with cid " + cid);
				VersionInfo info = requested.remove(cid);
				if(info == null) {
					// TODO log info
					System.out.println("Response with cid " + cid + " not matched - already aborted?");
					return;
				}
				info.record.received = true;
				
				String bucketName = info.record.bucket.name;
				Key key = info.record.key;
				Version version = info.version;
				
				Document doc = new Document(key, version, response.getPayload());
				server.getBucket(bucketName).writeDocument(doc);
			} catch (BucketException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			// TODO log warning that this is unexpected.
		}
	}

	/**
	 * Analogue of a Bucket but just keep version information including the node
	 * that has the record.
	 * @author rbp28668
	 *
	 */
	private static class BucketVersionInfo{
		
		String name;
		ConcurrentHashMap<Key, RecordInfo> recordInfo = new ConcurrentHashMap<>();
		
		BucketVersionInfo(String name){
			this.name = name;
		}
		
		RecordInfo add(Key key, Version version, int nodeId) {
			RecordInfo record = recordInfo.get(key);
			if(record == null) {
				record = new RecordInfo(this,key);
				recordInfo.put(key, record);
			}
			record.add(new VersionInfo(record, version, nodeId));
			return record;
		}
		
	}
	
	/**
	 * Records the state for managing a single record during recovery.
	 * @author rbp28668
	 *
	 */
	private static class RecordInfo{
		BucketVersionInfo bucket;
		Key key;
		ArrayList<VersionInfo> versionInfo = new ArrayList<VersionInfo>();
		boolean requested = false;
		boolean received = false;
		
		
		RecordInfo(BucketVersionInfo bucket, Key key){
			this.bucket = bucket;
			this.key = key;
		}
		
		void add(VersionInfo info) {
			versionInfo.add(info);
		}
		
		
	}
	
	/**
	 * Version of a record and the node it came from
	 * @author rbp28668
	 *
	 */
	private static class VersionInfo{
		Version version;
		int nodeId;
		boolean requested = false;
		RecordInfo record;
		
		public VersionInfo(RecordInfo record, Version version, int nodeId) {
			super();
			this.record = record;
			this.version = version;
			this.nodeId = nodeId;
		}
		
	}

}
