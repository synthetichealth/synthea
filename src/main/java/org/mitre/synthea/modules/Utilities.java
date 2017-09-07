package org.mitre.synthea.modules;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.mitre.synthea.helpers.Config;

import com.google.gson.JsonPrimitive;

public class Utilities {
	/**
	 * Convert a quantity of time in a specified units into milliseconds
	 * @param units : "hours", "minutes", "days", "years", or "months"
	 * @param value : quantity of units
	 * @return milliseconds
	 */
	public static long convertTime(String units, long value) {
		switch(units) {
		case "hours":
			return TimeUnit.HOURS.toMillis(value);
		case "minutes":
			return TimeUnit.MINUTES.toMillis(value);
		case "days":
			return TimeUnit.DAYS.toMillis(value);
		case "years":
			return TimeUnit.DAYS.toMillis(365 * value);
		case "months":
			return TimeUnit.DAYS.toMillis(30 * value);
		}
		return (long) value;
	}
	
	public static long convertCalendarYearsToTime(int years) {
		return convertTime( "years", (long)(years - 1970) );
	}
	
	public static int getYear(long time)
	{
		Calendar calendar = Calendar.getInstance( TimeZone.getTimeZone("UTC") );
		calendar.setTimeInMillis(time);
		return calendar.get(Calendar.YEAR);
	}

	/**
	 * Converts a JsonPrimitive into a primitive Boolean, Double, or String.
	 * @param p : JsonPrimitive
	 * @return Boolean, Double, or String
	 */
	public static Object primitive(JsonPrimitive p) {
		Object retVal = null;
		if(p.isBoolean()) {
			retVal = p.getAsBoolean();
		} else if(p.isNumber()) {
			double doubleVal = p.getAsDouble();
			
			if (doubleVal == Math.rint(doubleVal))
			{
				retVal = (int) doubleVal;
			} else
			{
				retVal = doubleVal;
			}
		} else if(p.isString()) {
			retVal = p.getAsString();
		}
		return retVal;
	}
	
	public static double convertRiskToTimestep(double risk, double originalPeriodInMS)
	{
		double currTimeStepInMS = Double.parseDouble( Config.get("generate.timestep") );
		
		return 1 - Math.pow(1 - risk, currTimeStepInMS / originalPeriodInMS);
	}
	
	public static boolean compare(Object lhs, Object rhs, String operator) {
		if(operator.equals("is nil")) {
			return lhs == null;
		} else if(operator.equals("is not nil")) {
			return lhs != null;
		} else if(lhs == null) {
			return false;
		}
		if(lhs instanceof Number && rhs instanceof Number) {
			return compare(((Number)lhs).doubleValue(), ((Number)rhs).doubleValue(), operator);
		} else if(lhs instanceof Boolean && rhs instanceof Boolean) {
			return compare((Boolean)lhs, (Boolean)rhs, operator);			
		} else if(lhs instanceof String && rhs instanceof String) {
			return compare((String)lhs, (String)rhs, operator);
		} else {
			System.out.format("Cannot compare %s to %s.\n", lhs.getClass().getName(), rhs.getClass().getName());
			return false;
		}
	}
	
    public static boolean compare(Double lhs, Double rhs, String operator) {
		switch(operator) {
		case "<":
		  return lhs < rhs;
		case "<=":
		  return lhs <= rhs;
		case "==":
		  return lhs == rhs;
		case ">=":
		  return lhs >= rhs;
		case ">":
		  return lhs > rhs;
		case "!=":
		  return lhs != rhs;
		case "is nil":
		  return lhs == null;
		case "is not nil":
		  return lhs != null;
		default:
		  System.err.format("Unsupported operator: %s\n", operator);
		  return false;
		}
    }
    
    public static boolean compare(Boolean lhs, Boolean rhs, String operator) {
		switch(operator) {
		case "<":
		  return lhs != rhs;
		case "<=":
		  return lhs != rhs;
		case "==":
		  return lhs == rhs;
		case ">=":
		  return lhs != rhs;
		case ">":
		  return lhs != rhs;
		case "!=":
		  return lhs != rhs;
		case "is nil":
		  return lhs == null;
		case "is not nil":
		  return lhs != null;
		default:
		  System.err.format("Unsupported operator: %s\n", operator);
		  return false;
		}
    }
    
    public static boolean compare(String lhs, String rhs, String operator) {
		switch(operator) {
		case "<":
		  return lhs.compareTo(rhs) < 0;
		case "<=":
		  return lhs.compareTo(rhs) <= 0;
		case "==":
		  return lhs == rhs;
		case ">=":
		  return lhs.compareTo(rhs) >= 0;
		case ">":
		  return lhs.compareTo(rhs) > 0;
		case "!=":
		  return lhs != rhs;
		case "is nil":
		  return lhs == null;
		case "is not nil":
		  return lhs != null;
		default:
		  System.err.format("Unsupported operator: %s\n", operator);
		  return false;
		}
    }
}
