package org.mitre.synthea;

import static org.junit.Assert.*;

import java.util.Date;

import org.junit.Test;
import org.mitre.synthea.modules.Utilities;

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

}
