package com.gobslog.ec2.snapshot;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.CreateSnapshotRequest;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeVolumesRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceBlockDeviceMapping;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.Volume;
import com.amazonaws.services.lambda.runtime.Context;

import java.util.ArrayList;
import java.util.HashMap;
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
    
    private boolean isWeeklySnapshot = false;
    
    private boolean isMonthlySnapshot = false;
    
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
						// If snapshots are turned off
		    			if (!instanceSnapshotConfig.isSnapshotOn())
		    			{
		    				if (logger.isInfoEnabled())
		    					logger.info("Snapshots turned off for instance "+instance.getInstanceId()+" - "+snapshotConfigString);
		    				continue;
		    			}
		    			
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
	    		
    			processInstanceBlockDevices(instance.getInstanceId(), instance.getBlockDeviceMappings(), instanceSnapshotConfig);
    			
        	}
    	}
    	
        
    }
    
    /**
     * This method loop through all the volumes from an EC2 Instance and get the appropriate snapshot config for the volume
     * If a snapshot config is defined at the instance level, it will override the one defined at the volume level
     * @param instanceId - The instance for which the snapshots are being taken
     * @param blockDeviceList - The list of volumes
     * @param instanceSnapshotConfig - The snapshot config set up at the instance level
     */
    private void processInstanceBlockDevices( String instanceId, List<InstanceBlockDeviceMapping> blockDeviceList,  SnapshotConfig instanceSnapshotConfig)
    {
    	// Looping through the volumes
		for (InstanceBlockDeviceMapping instanceDevice : blockDeviceList)
		{
			SnapshotConfig volumeSnapshotConfig = instanceSnapshotConfig;
			if (logger.isInfoEnabled())
				logger.info("Volume " + instanceDevice.getDeviceName() + " - " + instanceDevice.getEbs().getVolumeId() );
			
			// Is not snapshot config was set at the Instance level
			if (instanceSnapshotConfig == null)
			{
				String volumeSnapshotConfigString = volumeSnapshotConfigMap.get(instanceDevice.getEbs().getVolumeId());
				
				// If no config has been set up for the volume, let's skip to the next one
				if (volumeSnapshotConfigString == null)
				{
					if (logger.isInfoEnabled())
						logger.info("Volume " +instanceDevice.getEbs().getVolumeId()+" has no snapshot config, skipping to the next volume" );
					
					continue;
				}
				
				try {
					// Parsing the EC2 Tag received
					volumeSnapshotConfig = new SnapshotConfig();
					volumeSnapshotConfig.initialise(volumeSnapshotConfigString);
					
					// If snapshots are turned off for the volume
	    			if (!volumeSnapshotConfig.isSnapshotOn())
	    			{
	    				if (logger.isInfoEnabled())
	    					logger.info("Snapshots turned off for volume "+instanceDevice.getEbs().getVolumeId()+" - "+volumeSnapshotConfigString);
	    				continue;
	    			}
	    			
				} catch (ParseException e) 
				{
					logger.error("Error with volumme " + instanceDevice.getEbs().getVolumeId() + " : "+e.getMessage());
					continue;
				}
			}
			
			
			// Let's take the appropriate snapshot
			takeVolumeSnapshot(instanceId, instanceDevice.getEbs().getVolumeId(), volumeSnapshotConfig);
		}
    }
    
    
    /**
     * This method is taking a snapshot for a volume depending on the given configuration
     * If it is the 1st day of the Month and the config mandate monthly snapshots a monthly snapshot is taken
     * Otherwise, If it is the 1st day of the week and the config mandate weekly snapshots a weekly snapshot is taken
     * Otherwise If the config mandate daily snapshots a daily snapshot is taken
     * @param instanceId - The instance for which the snapshots are being taken
     * @param volumeId - The volume to take a snapshot of
     * @param snapshotConfig - The snapshot config to be used
     */
    private void takeVolumeSnapshot(String instanceId, String volumeId, SnapshotConfig snapshotConfig)
    {
    	if (isMonthlySnapshot && snapshotConfig.isMonthlySnapshot())
		{
    		takeVolumeSnapshot(instanceId, volumeId, "monthly", snapshotConfig.getMonthlyRetentionPeriod());
		}
    	else if (isWeeklySnapshot && snapshotConfig.isWeeklySnapshot())
    	{
    		takeVolumeSnapshot(instanceId, volumeId, "weekly", snapshotConfig.getWeeklyRetentionPeriod());
		}
    	else if (snapshotConfig.isDailySnapshot())
    	{
    		takeVolumeSnapshot(instanceId, volumeId, "daily", snapshotConfig.getWeeklyRetentionPeriod());
		}
    }
    
    /**
     * Takes a snapshot of the given volume
     * @param instanceId - For tagging
     * @param volumeId - The volume to take a snapshot of
     * @param period - monthly / weekly / daily
     * @param retentionPeriod - in days
     */
    private void takeVolumeSnapshot(String instanceId, String volumeId, String period, int retentionPeriod)
    {
    	if (logger.isInfoEnabled())
			logger.info("Taking "+period+" snapshot for instance "+instanceId+" volume "+period+" with a retention period of "+retentionPeriod);
    	
    	// Creating the snapshot
    	CreateSnapshotRequest createSnapshotRequest = new CreateSnapshotRequest(volumeId, period);
    	ec2Client.createSnapshot(createSnapshotRequest);
    	
    	List<Tag> tagList = new ArrayList<Tag>();
    	tagList.add(new Tag("INSTANCE_ID", instanceId));
    	
    	List<String> volumeList = new ArrayList<String>();
    	volumeList.add(volumeId);
    	
    	// Adding tags onto the snapshot
    	CreateTagsRequest  createTagsRequest = new CreateTagsRequest(volumeList, tagList);
    	ec2Client.createTags(createTagsRequest);
    }
    
    
    /**
     * This method reads all the attached volumes for the region and 
     * initialise a map with snapshot configuration for each of them
     */
    private void initialiseVolumeSnapshotConfigMap()
    {
    	volumeSnapshotConfigMap = new HashMap<String, String>();
    	DescribeVolumesRequest dr = new DescribeVolumesRequest();
    	dr.withFilters(new Filter().withName("attachment.status").withValues("attached"));
    	
    	// For each attached volumes in the region
    	for (Volume volume :  ec2Client.describeVolumes(dr).getVolumes())
    	{
    		if (logger.isDebugEnabled())
				logger.debug("Loading attached volume " + volume.getVolumeId() );
    		 
    		// Reading the instance tags
			for (Tag tag : volume.getTags())
			{
				// Looking for the Snapshot Config Tag
				if (SNAPSHOT_CONFIG_TAG.equals(tag.getKey()))
				{
					// Adding the config to the config map for later use
					volumeSnapshotConfigMap.put(volume.getVolumeId(), tag.getValue());
					break;
				}
					
			}
			
    	}
    	
    }
}
