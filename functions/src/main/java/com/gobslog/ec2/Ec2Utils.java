package com.gobslog.ec2;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.amazonaws.regions.Regions;

public class Ec2Utils {
	
	/** log4j logger */
    static final Logger logger = Logger.getLogger(Ec2Utils.class);
	
	/**
	 * This method reads a comma separated list of regions and validated them
	 * @param regionsConfig - the pipe separated list of region to load
	 * @return - a List with the region names
	 */
	public static List<String> loadRegions(String regionsConfig)
    {
		List<String> regions = new ArrayList<String>();
		
    	// If no region config given
		if (regionsConfig == null || regionsConfig.length() == 0)
		{
			logger.info("No region configured, action will be taken only in your default region");
			return regions;
		}
		
		// Let's split the different configuration item
		for (String regionName : regionsConfig.split("\\|"))
		{
			regionName = regionName.trim();
			try
			{
				Regions.fromName(regionName);
				regions.add(regionName);
			}
			catch (IllegalArgumentException e)
			{
				logger.error(regionName + "is not a valid AWS Region");
				continue;
			}
			
		}
		
		return regions;
    }

}
