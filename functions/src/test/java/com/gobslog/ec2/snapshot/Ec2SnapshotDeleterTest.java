package com.gobslog.ec2.snapshot;

import static org.junit.Assert.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.hamcrest.core.Is.*;


import org.junit.Test;

import com.gobslog.ec2.functions.Ec2SnapshotDeleter;

public class Ec2SnapshotDeleterTest {
	
	
	@Test
	public void badFormats() throws ParseException { 
		Date today = new SimpleDateFormat("yyyy-MM-dd").parse("2017-05-01");
		
		Ec2SnapshotDeleter ec2SnapshotDeleter = new Ec2SnapshotDeleter();
		try {
			ec2SnapshotDeleter.isSnapshotDeletable("2017/06/04", today);
			fail("Expected an IllegalStateException to be thrown");
		} catch (IllegalArgumentException e) {
			assertThat(e.getMessage(), is("Invalid date format 2017/06/04"));
		}
		
		ec2SnapshotDeleter = new Ec2SnapshotDeleter();
		try {
			ec2SnapshotDeleter.isSnapshotDeletable("20170401", today);
			fail("Expected an IllegalStateException to be thrown");
		} catch (IllegalArgumentException e) {
			assertThat(e.getMessage(), is("Invalid date format 20170401"));
		}
		
		ec2SnapshotDeleter = new Ec2SnapshotDeleter();
		try {
			ec2SnapshotDeleter.isSnapshotDeletable("", today);
			fail("Expected an IllegalStateException to be thrown");
		} catch (IllegalArgumentException e) {
			assertThat(e.getMessage(), is("Both snapshotDeleteDateTag and today are mandatory parameters"));
		}
		
		ec2SnapshotDeleter = new Ec2SnapshotDeleter();
		try {
			ec2SnapshotDeleter.isSnapshotDeletable(null, today);
			fail("Expected an IllegalStateException to be thrown");
		} catch (IllegalArgumentException e) {
			assertThat(e.getMessage(), is("Both snapshotDeleteDateTag and today are mandatory parameters"));
		}
		
		ec2SnapshotDeleter = new Ec2SnapshotDeleter();
		try {
			ec2SnapshotDeleter.isSnapshotDeletable("2017-05-01", null);
			fail("Expected an IllegalStateException to be thrown");
		} catch (IllegalArgumentException e) {
			assertThat(e.getMessage(), is("Both snapshotDeleteDateTag and today are mandatory parameters"));
		}
		
	}
	
	@Test
	public void validFormats() throws ParseException { 
		
		Date today = new SimpleDateFormat("yyyy-MM-dd").parse("2017-05-01");
		
		Ec2SnapshotDeleter ec2SnapshotDeleter = new Ec2SnapshotDeleter();
		
		
		assertTrue(ec2SnapshotDeleter.isSnapshotDeletable("2017-05-01", today));
		assertTrue(ec2SnapshotDeleter.isSnapshotDeletable("2017-01-15", today));
		assertTrue(ec2SnapshotDeleter.isSnapshotDeletable("2012-07-15", today));
		
		assertFalse(ec2SnapshotDeleter.isSnapshotDeletable("2017-05-02", today));
		assertFalse(ec2SnapshotDeleter.isSnapshotDeletable("2017-07-15", today));
		
		
	}
	
}
