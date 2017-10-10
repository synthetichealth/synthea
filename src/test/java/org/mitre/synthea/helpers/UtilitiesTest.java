package org.mitre.synthea;

import static org.junit.Assert.*;

import java.util.Date;

import org.junit.Test;
import org.mitre.synthea.helpers.Utilities;

public class UtilitiesTest {

	@SuppressWarnings("deprecation")
	@Test
	public void testConvertTime() {
		long year = Utilities.convertCalendarYearsToTime(2005);
		long date = new Date(104,0,1).getTime(); // January 1, 2005
		System.out.println(year);
		System.out.println(date);
		assertTrue(date <= year);
		date = new Date(106,0,1).getTime(); // January 1, 2005
		System.out.println(date);
		assertTrue(date > year);
	}
	
	@Test
	public void testGetYear()
	{
		assertEquals(1970, Utilities.getYear(0L) );
		assertEquals(2017, Utilities.getYear(1504783221000L) ); // the time as of this writing, 2017-09-07
		assertEquals(2009, Utilities.getYear(1234567890000L) );
	}

}
