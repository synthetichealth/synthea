package org.mitre.synthea.modules;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.mitre.synthea.modules.HealthRecord.Entry;

import com.google.gson.Gson;

/**
This is a complete, but fairly simplistic approach to synthesizing immunizations.  It is encounter driven;
whenever an encounter occurs, the doctor checks for due immunizations and gives them.  In at least one case
(HPV) this means that the immunization schedule isn't strictly followed since the encounter schedule doesn't
match the immunization schedule (e.g., 11yrs, 11yrs2mo, 11yrs6mo) -- but in most cases they do line up.
This module also assumes perfect doctors and compliant patients.  Every patient eventually receives every
recommended immunization (unless they die first).  This module also does not implement any deviations or
contraindications based on patient conditions.  For now, we've avoided specific brand names, preferring the
general CVX codes.
 */
public class Immunizations 
{
	public static final String IMMUNIZATIONS = "immunizations";
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static final Map<String,Map> immunizationSchedule = loadImmunizationSchedule();

	@SuppressWarnings("rawtypes")
	private static Map loadImmunizationSchedule() {
		String filename = "/immunization_schedule.json";
		try {
			InputStream stream = LifecycleModule.class.getResourceAsStream(filename);
			String json = new BufferedReader(new InputStreamReader(stream)).lines()
					.parallel().collect(Collectors.joining("\n"));
			Gson g = new Gson();
			return g.fromJson(json, HashMap.class);
		} catch (Exception e) {
			System.err.println("ERROR: unable to load json: " + filename);
			e.printStackTrace();
			throw new ExceptionInInitializerError(e);
		}
	}
	
	@SuppressWarnings("unchecked")
	public static void performEncounter(Person person, long time)
	{
		Map<String,List<Long>> immunizationsGiven;
		if(person.attributes.containsKey(IMMUNIZATIONS)) {
			immunizationsGiven = (Map<String,List<Long>>) person.attributes.get(IMMUNIZATIONS);
		} else {
			immunizationsGiven = new HashMap<String,List<Long>>();
			person.attributes.put(IMMUNIZATIONS, immunizationsGiven);
		}
		
		for(String immunization : immunizationSchedule.keySet()) {
			if(immunizationDue(immunization, person, time, immunizationsGiven))
			{
				List<Long> history = immunizationsGiven.get(immunization);
				history.add(time);
				HealthRecord.Entry entry = person.record.immunization(time, immunization);
				Map code = (Map) immunizationSchedule.get(immunization).get("code");
				HealthRecord.Code immCode = new HealthRecord.Code(code.get("system").toString(), code.get("code").toString(), code.get("display").toString());
				entry.codes.add(immCode);
			}
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static boolean immunizationDue(String immunization, Person person, long time, Map<String,List<Long>> immunizationsGiven)
	{
		int ageInMonths = person.ageInMonths(time);
		
		List<Long> history = null;
		if(immunizationsGiven.containsKey(immunization)) {
			history = immunizationsGiven.get(immunization);
		} else {
			history = new ArrayList<Long>();
			immunizationsGiven.put(immunization, history);
		}
		
		// Don't administer if the immunization wasn't historically available at the date of the encounter
		Map schedule = immunizationSchedule.get(immunization);
		Double firstAvailable = (Double) schedule.getOrDefault("first_available", 1900);
		if(time < Utilities.convertCalendarYearsToTime(firstAvailable.intValue()))
		{
			return false;
		}
		
		// Don't administer if all recommended doses have already been given
		List at_months = new ArrayList((List) schedule.get("at_months"));
		if(history.size() >= at_months.size()) {
			return false;
		}
		
		// See if the patient should receive a dose based on their current age and the recommended dose ages;
		// we can't just see if greater than the recommended age for the next dose they haven't received
		// because i.e. we don't want to administer the HPV vaccine to someone who turns 90 in 2006 when the
		// vaccine is released; we can't just use a simple test of, say, within 4 years after the recommended
		// age for the next dose they haven't received because i.e. PCV13 is given to kids and seniors but was
		// only available starting in 2010, so a senior in 2012 who has never received a dose should get one,
		// but only one; what we do is:
		
		// 1) eliminate any recommended doses that are not within 4 years of the patient's age
		// at_months = at_months.reject { |am| age_in_months - am >= 48 }
		Predicate<Double> notWithinFourYears = p -> ((ageInMonths - p) >= 48);
		at_months.removeIf(notWithinFourYears);
		if(at_months.isEmpty()) {
			return false;
		}

		// 2) eliminate recommended doses that were actually administered
		for(Long date : history) {
			int ageAtDate = person.ageInMonths(date);
			double recommendedAge = (double) at_months.get(0);
			if(ageAtDate >= recommendedAge && ((ageAtDate - recommendedAge) < 48)) {
				at_months.remove(0);
				if(at_months.isEmpty()) {
					return false;
				}
			}
		}

		// 3) see if there are any recommended doses remaining that this patient is old enough for
		return !at_months.isEmpty() && ageInMonths >= (double) at_months.get(0);
	}
}
