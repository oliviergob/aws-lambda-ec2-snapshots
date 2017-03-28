package com.gobslog.ec2.snapshot;

import static org.junit.Assert.*;
import static org.hamcrest.core.Is.*;


import org.junit.Test;

public class SnapshotConfigTest {
	
	
	@Test
	public void badFormats() { 
		SnapshotConfig snapshotConfig = new SnapshotConfig();
		try {
			snapshotConfig.initialise("123");
			fail("Expected an ParseException to be thrown");
		} catch (ParseException e) {
			assertThat(e.getMessage(), is("Configuration 123 does not have a valid format, expecting: [DWM]<9999><,[DWM]<9999>><,[DWM]<9999>>"));
		}
		
		try {
			snapshotConfig.initialise("D123a");
			fail("Expected an ParseException to be thrown");
		} catch (ParseException e) {
			assertThat(e.getMessage(), is("Configuration D123a does not have a valid format, expecting: [DWM]<9999><,[DWM]<9999>><,[DWM]<9999>>"));
		}
		
		try {
			snapshotConfig.initialise("P123");
			fail("Expected an ParseException to be thrown");
		} catch (ParseException e) {
			assertThat(e.getMessage(), is("Configuration P123 does not have a valid format, expecting: [DWM]<9999><,[DWM]<9999>><,[DWM]<9999>>"));
		}
		
		try {
			snapshotConfig.initialise("D07,W30,M12,D02");
			fail("Expected an ParseException to be thrown");
		} catch (ParseException e) {
			assertThat(e.getMessage(), is("Configuration D07,W30,M12,D02 does not have a valid format, expecting: [DWM]<9999><,[DWM]<9999>><,[DWM]<9999>>"));
		}
	}
	
	@Test
	public void validFormats() { 
		SnapshotConfig snapshotConfig = new SnapshotConfig();
		try {
			snapshotConfig.initialise("D10");
			
			assertTrue(snapshotConfig.isDailySnapshot());
			assertFalse(snapshotConfig.isWeeklySnapshot());
			assertFalse(snapshotConfig.isMonthlySnapshot());
			assertEquals(10, snapshotConfig.getDailyRetentionPeriod());
			
		} catch (ParseException e) {
			fail("Unexpected ParseException: "+e.getMessage());
		}
		
		snapshotConfig = new SnapshotConfig();
		try {
			snapshotConfig.initialise("W20");
			
			assertFalse(snapshotConfig.isDailySnapshot());
			assertTrue(snapshotConfig.isWeeklySnapshot());
			assertFalse(snapshotConfig.isMonthlySnapshot());
			assertEquals(20, snapshotConfig.getWeeklyRetentionPeriod());
			
		} catch (ParseException e) {
			fail("Unexpected ParseException: "+e.getMessage());
		}
		
		snapshotConfig = new SnapshotConfig();
		try {
			snapshotConfig.initialise("M30");
			
			assertFalse(snapshotConfig.isDailySnapshot());
			assertFalse(snapshotConfig.isWeeklySnapshot());
			assertTrue(snapshotConfig.isMonthlySnapshot());
			assertEquals(30, snapshotConfig.getMonthlyRetentionPeriod());
			
		} catch (ParseException e) {
			fail("Unexpected ParseException: "+e.getMessage());
		}
		
		snapshotConfig = new SnapshotConfig();
		try {
			snapshotConfig.initialise("D10,W20,M30");
			
			assertTrue(snapshotConfig.isDailySnapshot());
			assertTrue(snapshotConfig.isWeeklySnapshot());
			assertTrue(snapshotConfig.isMonthlySnapshot());
			assertEquals(10, snapshotConfig.getDailyRetentionPeriod());
			assertEquals(20, snapshotConfig.getWeeklyRetentionPeriod());
			assertEquals(30, snapshotConfig.getMonthlyRetentionPeriod());
			
		} catch (ParseException e) {
			fail("Unexpected ParseException: "+e.getMessage());
		}
		
	}

}
