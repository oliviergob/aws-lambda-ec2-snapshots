package com.gobslog.ec2;

import java.util.List;
import static org.junit.Assert.*;

import org.junit.Test;

public class Ec2UtilsTest {
	
	@Test
	public void badFormats() { 
		
		List<String> regionList = Ec2Utils.loadRegions(null);
		assertEquals(0, regionList.size());
		
		regionList = Ec2Utils.loadRegions("");
		assertEquals(0, regionList.size());
		
		regionList = Ec2Utils.loadRegions("asdjasd");
		assertEquals(0, regionList.size());
		
		regionList =  Ec2Utils.loadRegions("us-east-1,bbbbbbbbbb");
		assertEquals(1, regionList.size());
		assertEquals("us-east-1", regionList.get(0));
		
		regionList =  Ec2Utils.loadRegions("us-east-1,iiiiiii, eu-west-1");
		assertEquals(2, regionList.size());
		assertEquals("us-east-1", regionList.get(0));
		assertEquals("eu-west-1", regionList.get(1));
	}

}
