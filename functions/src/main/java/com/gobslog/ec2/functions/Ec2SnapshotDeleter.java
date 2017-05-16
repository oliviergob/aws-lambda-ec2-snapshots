package com.gobslog.ec2.functions;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
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

public class Ec2SnapshotDeleter {
	
	/** log4j logger */
    static final Logger logger = Logger.getLogger(Ec2SnapshotDeleter.class);
    /** The EC2 API Client */
    private AmazonEC2 ec2Client = null;
   
    
    public void lambdaHandler(Map<String,Object> input, Context context) {
    	
		Calendar todayCal = Calendar.getInstance();
		todayCal.set(Calendar.HOUR_OF_DAY, 0); 
		todayCal.set(Calendar.MINUTE, 0);
		todayCal.set(Calendar.SECOND, 0);
		todayCal.set(Calendar.MILLISECOND, 0);
		
		Date today = new Date (todayCal.getTimeInMillis());
		
		logger.info("Today's date "+Ec2SnapshotTaker.DATE_FORMAT.format(today));
		
		deleteSnapshotsForRegion(today);
        
    }
    
    
    /**
     * Method deleting Snapshot for the region the Lambda function has been deployed in
     */
    private void deleteSnapshotsForRegion( Date today)
    {
    	// Setting up the EC2 Client Builder with the correct region
    	AmazonEC2ClientBuilder builder = AmazonEC2Client.builder();
    	logger.info("Deleting snapshots for region "+Regions.fromName(System.getenv("AWS_DEFAULT_REGION")).getName());
    	
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
