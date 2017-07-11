package org.mitre.synthea.modules;

import java.util.Calendar;
import java.util.concurrent.ThreadLocalRandom;


public final class CardiovascularDiseaseModule extends Module 
{
	
	
	
	@Override
	public boolean process(Person person, long time) 
	{
		// run through all of the rules defined
		// ruby "rules" are converted to static functions here
		// since this is intended to only be temporary
		
		startSmoking(person, time);
		calculateCardioRisk(person, time);
		onsetCoronaryHeartDisease(person, time);
		coronaryHeartDiseaseProgression(person, time);
		noCoronaryHeartDisease(person, time);
		calculateAtrialFibrillationRisk(person, time);
		getAtrialFibrillation(person, time);
		calculateStrokeRisk(person, time);
		getStroke(person, time);
		heartHealthyLifestyle(person, time);
		chdTreatment(person, time);
		atrialFibrillationTreatment(person, time);
		
		// java modules will never "finish"
		return false;
	}

	/////////////////////////
	// MIGRATED JAVA RULES //
	/////////////////////////
	private static void startSmoking(Person person, long time)
	{
		// 9/10 smokers start before age 18. We will use 16.
	    // http://www.cdc.gov/tobacco/data_statistics/fact_sheets/youth_data/tobacco_use/
		if (person.attributes.get("SMOKER") == null && person.ageInYears(time) == 16)
		{
			Calendar calendar = Calendar.getInstance();
			calendar.setTimeInMillis(time);
			long year = calendar.get(Calendar.YEAR);
			Boolean smoker = ThreadLocalRandom.current().nextDouble() < likelihoodOfBeingASmoker(year);
			person.attributes.put("SMOKER", smoker);
		}
	}
	
	private static double likelihoodOfBeingASmoker(long year)
	{
        // 16.1% of MA are smokers in 2016. http://www.cdc.gov/tobacco/data_statistics/state_data/state_highlights/2010/states/massachusetts/
        // but the rate is decreasing over time
		// http://www.cdc.gov/tobacco/data_statistics/tables/trends/cig_smoking/
		// selected #s:
		// 1965 - 42.4%
		// 1975 - 37.1%
		// 1985 - 30.1%
		// 1995 - 24.7%
		// 2005 - 20.9%
		// 2015 - 16.1%
		// assume that it was never significantly higher than 42% pre-1960s, but will continue to drop slowly after 2016
		// it's decreasing about .5% per year
		if (year < 1965)
		{
			return 0.424;
		}
		
		return ((year * -0.4865) + 996.41) / 100.0;
	}
	
	private static void calculateCardioRisk(Person person, long time)
	{
		
	}
	
	private static void onsetCoronaryHeartDisease(Person person, long time)
	{
		
	}
	
	private static void coronaryHeartDiseaseProgression(Person person, long time)
	{
		
	}
	
	private static void noCoronaryHeartDisease(Person person, long time)
	{
		
	}
	
	private static void calculateAtrialFibrillationRisk(Person person, long time)
	{
		
	}
	
	private static void getAtrialFibrillation(Person person, long time)
	{

	}

	private static void calculateStrokeRisk(Person person, long time)
	{

	}

	private static void getStroke(Person person, long time)
	{

	}

	private static void heartHealthyLifestyle(Person person, long time)
	{

	}

	private static void chdTreatment(Person person, long time)
	{

	}

	private static void atrialFibrillationTreatment(Person person, long time)
	{

	}
	
	private static void performEncounter(Person person, long time)
	{
		
	}
	
	private static void performEmergency(Person person, long time)
	{
		
	}
	
}
