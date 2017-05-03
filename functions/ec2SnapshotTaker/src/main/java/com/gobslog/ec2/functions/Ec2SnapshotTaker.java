package com.gobslog.ec2.functions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.CreateSnapshotRequest;
import com.amazonaws.services.ec2.model.CreateSnapshotResult;
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
import com.gobslog.ec2.Ec2Utils;
import com.gobslog.ec2.snapshot.ParseException;
import com.gobslog.ec2.snapshot.SnapshotConfig;

public class Ec2SnapshotTaker {
	
	/** EC2 Instance Tag to place on the Host or the Volume */
	private static final String INSTANCE_TAG_SNAPSHOT_CONFIG = "SnapshotConfig";
	/** Snapshot Tag - Instance Id */
	private static final String SNAPSHOT_TAG_INSTANCE_ID = "InstanceId";
	/** Snapshot Tag - Name */
	private static final String SNAPSHOT_TAG_NAME = "Name";
	/** Snapshot Tag - Deletion Date */
	protected static final String SNAPSHOT_TAG_DELETION_DATE = "DeletionDate";
	/** Snapshot Tag - Device Name */
	private static final String SNAPSHOT_TAG_DEVICE_NAME = "DeviceName";
	/** Daily Snapshot description used for tagging */
	private static final String SNAPSHOT_DAILY = "daily";
	/** Weekly Snapshot description used for tagging */
	private static final String SNAPSHOT_WEEKLY = "weekly";
	/** Monthly Snapshot description used for tagging */
	private static final String SNAPSHOT_MONTHLY = "monthly";
	/** Date Format - used for tagging the instance */
	protected static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

	
	/** log4j logger */
    static final Logger logger = Logger.getLogger(Ec2SnapshotTaker.class);
    /** The EC2 API Client */
    private AmazonEC2 ec2Client = null;
    /** Map containing all the existing volumes and their associated snapshot configuration  */
    private Map<String, String> volumeSnapshotConfigMap = null;
    /** Boolean flag saying if weekly snapshot should be taken today */
    private boolean isWeeklySnapshot = false;
    /** Boolean flag saying if monthly snapshot should be taken today */
    private boolean isMonthlySnapshot = false;

    
    
    public void lambdaHandler(Map<String,Object> input, Context context) {
    	
    	// Getting the regions take snapshot in
    	String regionsString = System.getenv("REGIONS");
    	// Validating and loading the regions
    	List<String> regions = Ec2Utils.loadRegions(regionsString);
		
    	if (regions == null || regions.isEmpty())
    		takeSnapshotsForRegion(null);
    	else
    		for (String region : regions)
    		{
    			takeSnapshotsForRegion(region);
    		}
        
    }
    
    
    /**
     * Method taking Snapshot for a region
     * @param region - the region to take snapshots in
     */
    private void takeSnapshotsForRegion(String region)
    {
    	// Setting up the EC2 Client Builder with the correct region
    	AmazonEC2ClientBuilder builder;
    	if (region != null)
    	{
    		logger.info("Taking snapshots for region "+region);
    		builder = AmazonEC2Client.builder().withRegion(region);
    	}
    	else
    	{
    		logger.info("Taking snapshots for default region "+Regions.fromName(System.getenv("AWS_DEFAULT_REGION")).getName());
    		builder = AmazonEC2Client.builder();
    	}
    		
		
    	// Building EC2 CLient
		ec2Client = builder.build();
    	
    	// Loading EC2 Volumes
    	initialiseVolumeSnapshotConfigMap();
    	
    	Calendar c = Calendar.getInstance();
    	if (c.get(Calendar.DAY_OF_MONTH) == 1)
    	{
    		if (logger.isInfoEnabled())
				logger.info("First day of the month, taking monthly snapshots");
    		
    		isMonthlySnapshot = true;
    	}	
    	
    	if (c.get(Calendar.DAY_OF_WEEK) == 1)
    	{
    		if (logger.isInfoEnabled())
				logger.info("First day of the week, taking weekly snapshots");
    		isWeeklySnapshot = true;
    	}
    	
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
    				if (INSTANCE_TAG_SNAPSHOT_CONFIG.equals(tag.getKey()))
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
			if (logger.isDebugEnabled())
				logger.debug("Volume " + instanceDevice.getDeviceName() + " - " + instanceDevice.getEbs().getVolumeId() );
			
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
			takeVolumeSnapshot(instanceId, instanceDevice.getDeviceName(), instanceDevice.getEbs().getVolumeId(), volumeSnapshotConfig);
		}
    }
    
    
    /**
     * This method is taking a snapshot for a volume depending on the given configuration
     * If it is the 1st day of the Month and the config mandate monthly snapshots a monthly snapshot is taken
     * Otherwise, Ifprivate Calendar today = Calendar.getInstance(); // Used to calculate deletion date it is the 1st day of the week and the config mandate weekly snapshots a weekly snapshot is taken
     * Otherwise If the config mandate daily snapshots a daily snapshot is taken
     * @param instanceId - The instance for which the snapshots are being taken
     * @param deviceName - The name of the device on the instance
     * @param volumeId - The volume to take a snapshot of
     * @param snapshotConfig - The snapshot config to be used
     */
    private void takeVolumeSnapshot(String instanceId, String deviceName, String volumeId, SnapshotConfig snapshotConfig)
    {
    	if (isMonthlySnapshot && snapshotConfig.isMonthlySnapshot())
		{
    		takeVolumeSnapshot(instanceId, deviceName, volumeId, SNAPSHOT_MONTHLY, snapshotConfig.getMonthlyRetentionPeriod());
		}
    	else if (isWeeklySnapshot && snapshotConfig.isWeeklySnapshot())
    	{
    		takeVolumeSnapshot(instanceId, deviceName, volumeId, SNAPSHOT_WEEKLY, snapshotConfig.getWeeklyRetentionPeriod());
		}
    	else if (snapshotConfig.isDailySnapshot())
    	{
    		takeVolumeSnapshot(instanceId, deviceName, volumeId, SNAPSHOT_DAILY, snapshotConfig.getDailyRetentionPeriod());
		}
    }
    
    /**
     * Takes a snapshot of the given volume
     * @param instanceId - For tagging
     * @param deviceName - The name of the device on the instance
     * @param volumeId - The volume to take a snapshot of
     * @param period - monthly / weekly / daily
     * @param retentionPeriod - in days
     */
    private void takeVolumeSnapshot(String instanceId, String deviceName,  String volumeId, String period, int retentionPeriod)
    {
    	
    	String description = period+" snapshot for instance "+instanceId+" volume "+volumeId+" - "+retentionPeriod+" days retention";
    	if (logger.isInfoEnabled())
			logger.info("Taking "+description);
    	
    	// Creating the snapshot
    	CreateSnapshotRequest createSnapshotRequest = new CreateSnapshotRequest(volumeId, description);
    	CreateSnapshotResult createSnapshotResult = ec2Client.createSnapshot(createSnapshotRequest);
    	
    	
    	List<Tag> tagList = new ArrayList<Tag>();
    	// Adding Instance Id tag
    	tagList.add(new Tag(SNAPSHOT_TAG_INSTANCE_ID, instanceId));
    	// Adding Snapshot Name tag
    	tagList.add(new Tag(SNAPSHOT_TAG_NAME, instanceId+"-"+volumeId+"-"+period));
    	// Adding Snapshot Deletion Date tag
    	tagList.add(new Tag(SNAPSHOT_TAG_DELETION_DATE, getDeletionDate(retentionPeriod)));
    	// Adding Device Name tag
    	tagList.add(new Tag(SNAPSHOT_TAG_DEVICE_NAME, deviceName));
    	
    	
    	List<String> snapshotIdList = new ArrayList<String>();
    	snapshotIdList.add(createSnapshotResult.getSnapshot().getSnapshotId());
    	
    	// Adding tags onto the snapshot
    	CreateTagsRequest  createTagsRequest = new CreateTagsRequest(snapshotIdList, tagList);
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
				if (INSTANCE_TAG_SNAPSHOT_CONFIG.equals(tag.getKey()))
				{
					// Adding the config to the config map for later use
					volumeSnapshotConfigMap.put(volume.getVolumeId(), tag.getValue());
					break;
				}
					
			}
			
    	}
    	
    }
    
    /**
     * Return the deletion date given a retention period
     * @param retentionPeriod
     * @return the deletion date formated
     */
    private String getDeletionDate(int retentionPeriod)
    {
    	Calendar cal = Calendar.getInstance();
    	
    	cal.add(Calendar.DAY_OF_MONTH, retentionPeriod);
    	
    	return DATE_FORMAT.format(cal.getTime());
    }
    
    
}
