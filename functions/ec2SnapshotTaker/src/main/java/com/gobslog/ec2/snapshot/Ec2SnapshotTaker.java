package com.gobslog.ec2.snapshot;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeVolumesRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceBlockDeviceMapping;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Volume;
import com.amazonaws.services.lambda.runtime.Context;

import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

public class Ec2SnapshotTaker {
	
	/** EC2 Tag to place on the Host or the Volume */
	private static String  SNAPSHOT_CONFIG_TAG = "SNAPSHOT_CONFIG";
	
	/** log4j logger */
    static final Logger logger = Logger.getLogger(Ec2SnapshotTaker.class);
    
    private AmazonEC2 ec2Client = null;
    
    private Map<String, String> volumeSnapshotConfigMap = null;
    
    public void lambdaHandler(Map<String,Object> input, Context context) {
        
    	// Building EC2 CLient
    	AmazonEC2ClientBuilder builder = AmazonEC2Client.builder();
    	ec2Client = builder.build();
    	
    	// Loading EC2 Volumes
    	initialiseVolumeSnapshotConfigMap();
    	
    	// Listing all the instances (for the region)
    	DescribeInstancesResult result = ec2Client.describeInstances();
    	// For each reservation
    	for (Reservation reservation : result.getReservations())
    	{
    		// Looping through all the instances
    		for (Instance instance : reservation.getInstances())
        	{
    			if (logger.isInfoEnabled())
    				logger.info("Processing instance " + instance.getInstanceId());
    			
    			SnapshotConfig instanceSnapshotConfig = null;
    			String snapshotConfigString = null;
    			
    			// Reading the instance tags
    			for (Tag tag : instance.getTags())
    			{
    				// Looking for the Snapshot Config Tag
    				if (SNAPSHOT_CONFIG_TAG.equals(tag.getKey()))
    					snapshotConfigString = tag.getValue();
    			}
    			
    			// If the Snapshot Config Tag was found
    			if (snapshotConfigString != null)
    			{
    				if (logger.isDebugEnabled())
        				logger.debug("Read config tag: " + snapshotConfigString);
    				
    				try {
    					// Parsing the EC2 Tag received
	    				instanceSnapshotConfig = new SnapshotConfig();
						instanceSnapshotConfig.initialise(snapshotConfigString);
					} catch (ParseException e) 
    				{
						logger.error("Error with instance " + instance.getInstanceId()+" : "+e.getMessage());
						continue;
					}
    			}
    			else
				// If the Snapshot Config Tag was found
    			{
    				if (logger.isDebugEnabled())
        				logger.debug("No config given");
    			}
	    			
    			
    			// If instanceSnapshotConfig is defined but snapshots are turned off
    			if (instanceSnapshotConfig != null && !instanceSnapshotConfig.isSnapshotOn())
    			{
    				logger.info("Snapshots turned off for instance "+instance.getInstanceId()+" - "+snapshotConfigString);
    				continue;
    			}
    			
    			processInstanceBlockDevices(instance.getBlockDeviceMappings(), instanceSnapshotConfig);
    			
        	}
    	}
    	
        
    }
    
    private void processInstanceBlockDevices( List<InstanceBlockDeviceMapping> blockDeviceList,  SnapshotConfig instanceSnapshotConfig)
    {
    	// Looping through the volumes
		for (InstanceBlockDeviceMapping instanceDevice : blockDeviceList)
		{
			instanceDevice.getDeviceName();
			instanceDevice.getEbs().getVolumeId();
			if (logger.isInfoEnabled())
				logger.info("Volume " + instanceDevice.getDeviceName() + " - " + instanceDevice.getEbs().getVolumeId() );
			
			//ec2Client.describeVolumes(describeVolumesRequest)
		}
    }
    
    private void initialiseVolumeSnapshotConfigMap()
    {
    	
    	DescribeVolumesRequest dr = new DescribeVolumesRequest();
    	dr.withFilters(new Filter().withName("attachment.status").withValues("attached"));
    	
    	for (Volume volume :  ec2Client.describeVolumes(dr).getVolumes())
    	{
    		if (logger.isDebugEnabled())
				logger.info("Loading attached volume " + volume.getVolumeId() );
    		volume.getTags();
    	}
    	
    }
}
