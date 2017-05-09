package com.gobslog.ec2.functions;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DeleteSnapshotRequest;
import com.amazonaws.services.ec2.model.DescribeSnapshotsRequest;
import com.amazonaws.services.ec2.model.Snapshot;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.util.StringUtils;
import com.gobslog.ec2.Ec2Utils;

public class Ec2SnapshotDeleter {
	
	/** log4j logger */
    static final Logger logger = Logger.getLogger(Ec2SnapshotDeleter.class);
    /** The EC2 API Client */
    private AmazonEC2 ec2Client = null;
   
    
    public void lambdaHandler(Map<String,Object> input, Context context) {
    	
    	// Getting the regions delete snapshot from
    	String regionsString = System.getenv("REGIONS");
    	// Validating and loading the regions
    	List<String> regions = Ec2Utils.loadRegions(regionsString);
    	
		Calendar todayCal = Calendar.getInstance();
		todayCal.set(Calendar.HOUR_OF_DAY, 0); 
		todayCal.set(Calendar.MINUTE, 0);
		todayCal.set(Calendar.SECOND, 0);
		todayCal.set(Calendar.MILLISECOND, 0);
		
		Date today = new Date (todayCal.getTimeInMillis());
		
		logger.info("Today's date "+Ec2SnapshotTaker.DATE_FORMAT.format(today));
		
    	if (regions == null || regions.isEmpty())
    		deleteSnapshotsForRegion(null, today);
    	else
    		for (String region : regions)
    		{
    			deleteSnapshotsForRegion(region, today);
    		}
        
    }
    
    
    /**
     * Method deleting Snapshot for a region
     * @param region - the region to take snapshots in
     */
    private void deleteSnapshotsForRegion(String region, Date today)
    {
    	// Setting up the EC2 Client Builder with the correct region
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
    	
    	// Building EC2 CLient
		ec2Client = builder.build();
		
		// Building a request to read only snapshots owned by this AWS account
		DescribeSnapshotsRequest request = new DescribeSnapshotsRequest();
		request.setOwnerIds(Arrays.<String>asList("self"));
		
		// For all the snapshots
		for (Snapshot snapshot : ec2Client.describeSnapshots(request).getSnapshots())
		{
			String snapshotDeleteDateTag = null;
			// Reading the snapshot tags
			for (Tag tag : snapshot.getTags())
			{
				// Looking for the Snapshot Deletion Tag
				if (Ec2SnapshotTaker.SNAPSHOT_TAG_DELETION_DATE.equals(tag.getKey()))
				{
					snapshotDeleteDateTag = tag.getValue();
					break;
				}
					
			}
			
			// If no snapshot tag could be found
			if (StringUtils.isNullOrEmpty(snapshotDeleteDateTag))
			{
				logger.error("Snapshot "+snapshot.getSnapshotId()+" is missing tag "+Ec2SnapshotTaker.SNAPSHOT_TAG_DELETION_DATE);
				continue;
			}
			
			// if the snapshot should be deleted?
			try{
				if (isSnapshotDeletable(snapshotDeleteDateTag, today))
				{
					logger.info("deleting snapshot "+snapshot.getSnapshotId());
					DeleteSnapshotRequest deleteSnapshotRequest = new DeleteSnapshotRequest(snapshot.getSnapshotId());
					ec2Client.deleteSnapshot(deleteSnapshotRequest);
				}
			}
			catch (IllegalArgumentException e)
			{
				logger.error("Error with snapshot "+snapshot.getSnapshotId()+" "+e.getMessage());
				continue;
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
    	if ( StringUtils.isNullOrEmpty(snapshotDeleteDateTag) || today == null)
    	{
    		throw new IllegalArgumentException("Both snapshotDeleteDateTag and today are mandatory parameters");
    	}
    	
    	try {
			Date deletionDate = Ec2SnapshotTaker.DATE_FORMAT.parse(snapshotDeleteDateTag);
			return ( today.compareTo(deletionDate) >= 0  );
		} catch (ParseException e) 
    	{
			throw new IllegalArgumentException("Invalid date format "+snapshotDeleteDateTag);
		}
    }
    
    
}
