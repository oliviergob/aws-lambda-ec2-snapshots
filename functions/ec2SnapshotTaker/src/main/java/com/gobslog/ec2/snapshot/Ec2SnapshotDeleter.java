package com.gobslog.ec2.snapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.Snapshot;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.lambda.runtime.Context;
import com.gobslog.ec2.Ec2Utils;

public class Ec2SnapshotDeleter {
	
	/** Snapshot Tag - Deletion Date */
	private static final String SNAPSHOT_TAG_DELETION_DATE = "DeletionDate";
	/** Snapshot Tag - Device Name */
	/** Date Format - used for tagging the instance */
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

	
	/** log4j logger */
    static final Logger logger = Logger.getLogger(Ec2SnapshotDeleter.class);
    /** The EC2 API Client */
    private AmazonEC2 ec2Client = null;
   
    
    public void lambdaHandler(Map<String,Object> input, Context context) {
    	
    	// Getting the regions delete snapshot from
    	String regionsString = System.getenv("REGIONS");
    	// Validating and loading the regions
    	List<String> regions = Ec2Utils.loadRegions(regionsString);
		
    	if (regions == null || regions.isEmpty())
    		deleteSnapshotsForRegion(null);
    	else
    		for (String region : regions)
    		{
    			deleteSnapshotsForRegion(region);
    		}
        
    }
    
    
    /**
     * Method deleting Snapshot for a region
     * @param region - the region to take snapshots in
     */
    private void deleteSnapshotsForRegion(String region)
    {
    	// Building EC2 CLient
    	AmazonEC2ClientBuilder builder;
    	if (region != null)
    	{
    		logger.info("Deleting snapshots for region "+region);
    		builder = AmazonEC2Client.builder().withRegion(region);
    	}
    	else
    	{
    		logger.info("Deleting snapshots for default region "+Regions.fromName(System.getenv("AWS_DEFAULT_REGION")).getName());
    		builder = AmazonEC2Client.builder();
    	}
    		
		
		
		ec2Client = builder.build();
		
		
		// For all the snapshots
		for (Snapshot snapshot : ec2Client.describeSnapshots().getSnapshots())
		{
			String snapshotDeleteDateTag = null;
			// Reading the snapshot tags
			for (Tag tag : snapshot.getTags())
			{
				// Looking for the Snapshot Config Tag
				if (SNAPSHOT_TAG_DELETION_DATE.equals(tag.getKey()))
					snapshotDeleteDateTag = tag.getValue();
			}
			
		}
		
    }
    
    /**
     * Method checking if a snapshot is to be deleted
     * @param snapshotDeleteDateTag - The String representing the snapshot to be deleted
     * @param today - today's date
     * @return true if the snapshot date to delete is today or in the past
     * @throws IllegalArgumentException
     */
    public boolean isSnapshotDeletable(String snapshotDeleteDateTag, Date today) throws IllegalArgumentException
    {
    	
    	try {
			Date deletionDate = DATE_FORMAT.parse(snapshotDeleteDateTag);
			return ( today.compareTo(deletionDate) >= 0  );
		} catch (ParseException e) 
    	{
			throw new IllegalStateException("Invalid date format "+snapshotDeleteDateTag);
		}
    }
    
    
}
