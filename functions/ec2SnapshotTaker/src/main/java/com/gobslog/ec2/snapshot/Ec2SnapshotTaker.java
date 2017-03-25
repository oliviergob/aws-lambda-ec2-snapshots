package com.gobslog.ec2.snapshot;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceBlockDeviceMapping;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.lambda.runtime.Context;
import java.util.Map;

import org.apache.log4j.Logger;

public class Ec2SnapshotTaker {
	
	private static String  SNAPSHOT_CONFIG = "SNAPSHOT_CONFIG";
	// Initialize the Log4j logger.
    static final Logger logger = Logger.getLogger(Ec2SnapshotTaker.class);
    
    public void lambdaHandler(Map<String,Object> input, Context context) {
        
    	AmazonEC2ClientBuilder builder = AmazonEC2Client.builder();
    	
    	AmazonEC2 ec2Client = builder.build();
    	
    	DescribeInstancesResult result = ec2Client.describeInstances();
    	// For each reservation
    	for (Reservation reservation : result.getReservations())
    	{
    		// Looping through all the instances
    		for (Instance instance : reservation.getInstances())
        	{
    			if (logger.isInfoEnabled())
    				logger.info("Processing instance " + instance.getInstanceId());
    			
    			SnapshotConfig instanceSnapshotConfig = new SnapshotConfig();
    			for (Tag tag : instance.getTags())
    			{
    				if (SNAPSHOT_CONFIG.equals(tag.getKey()))
					{
    					String snapshotConfigString = tag.getValue();
    					try {
							instanceSnapshotConfig.initialise(snapshotConfigString);
						} catch (ParseException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
    			}
    			
    			// Lopping through the volumes
    			for (InstanceBlockDeviceMapping instanceDevice : instance.getBlockDeviceMappings())
    			{
    				instanceDevice.getDeviceName();
    				instanceDevice.getEbs().getVolumeId();
    				if (logger.isInfoEnabled())
    					logger.info("Volume " + instanceDevice.getDeviceName() + " - " + instanceDevice.getEbs().getVolumeId() );
    			}
        	}
    	}
    	
        
    }
}
