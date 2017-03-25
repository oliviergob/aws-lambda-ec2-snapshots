package com.gobslog.ec2.snapshot;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SnapshotConfig {
	
	public static String SNAPSHOT_OFF = "off";
	
	private static String CONFIG_REGEX = "([DWM][0-9]*)([,][DWM][0-9]*){0,2}";
	
	/** If false, no snapshot will be taken at all for this instance / volume */
	private boolean isSnapshotOn = false;

	/** If true, snapshot will be taken daily for this instance / volume */
	private boolean isDailySnapshot = false;
	
	/** Retention period for Daily snapshots, in days */
	private int dailyRetentionPeriod = 7;
	
	/** If true, snapshot will be taken Weekly for this instance / volume */
	private boolean isWeeklySnapshot = false;
	
	/** Retention period for Weekly snapshots, in days */
	private int weeklyRetentionPeriod = 30;
	
	/** If true, snapshot will be taken Monthly for this instance / volume */
	private boolean isMonthlySnapshot = false;
	
	/** Retention period for Daily snapshots, in days */
	private int monthlyRetentionPeriod = 90;

	public boolean isSnapshotOn() {
		return isSnapshotOn;
	}
	
	public boolean isDailySnapshot() {
		return isDailySnapshot;
	}

	public int getDailyRetentionPeriod() {
		return dailyRetentionPeriod;
	}

	public boolean isWeeklySnapshot() {
		return isWeeklySnapshot;
	}

	public int getWeeklyRetentionPeriod() {
		return weeklyRetentionPeriod;
	}

	public boolean isMonthlySnapshot() {
		return isMonthlySnapshot;
	}
	
	public int getMonthlyRetentionPeriod() {
		return monthlyRetentionPeriod;
	}

	
	public void initialise(String config) throws ParseException
	{
		// If no config given
		if (config.length() == 0)
			throw new ParseException("Empty configuration given");
		
		// If Snapshots are turned off, let's keep the default value
		if (config.toLowerCase().startsWith(SNAPSHOT_OFF))
			return;
		
		// Let's check if the config is valid
		Pattern pattern = Pattern.compile(CONFIG_REGEX);
		Matcher matcher = pattern.matcher(config);
		if (!matcher.matches())
			throw new ParseException("Configuration "+config+" does not have a valid format, expecting: [DWM]<9999><,[DWM]<9999>><,[DWM]<9999>>");
		
		// Let's split the different configuration item
		for (String configItem : config.split(","))
		{
			
			switch (configItem.charAt(0))
			{
				case 'D':  
					isDailySnapshot = true;
					if (configItem.length() > 1)
						dailyRetentionPeriod = Integer.valueOf(configItem.substring(1));
	        		break;
				case 'W':
	        		isWeeklySnapshot = true;
					if (configItem.length() > 1)
						weeklyRetentionPeriod = Integer.valueOf(configItem.substring(1));
	        		break;
				case 'M':
	        		isMonthlySnapshot = true;
					if (configItem.length() > 1)
						monthlyRetentionPeriod = Integer.valueOf(configItem.substring(1));
	        		break;
			}
			
			
		}
		
	}

}
