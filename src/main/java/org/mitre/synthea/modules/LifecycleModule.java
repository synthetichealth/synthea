package org.mitre.synthea.modules;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.mitre.synthea.modules.HealthRecord.Code;
import org.mitre.synthea.world.Location;

import com.github.javafaker.Faker;

public final class LifecycleModule extends Module 
{
	private static final Faker faker = new Faker();
	
	@Override
	public boolean process(Person person, long time) 
	{
		// run through all of the rules defined
		// ruby "rules" are converted to static functions here
		// since this is intended to only be temporary
		
		// birth(person, time); intentionally left out - call it only once from Generator
		age(person, time);
		grow(person, time);
		diabeticVitalSigns(person, time);
		death(person, time);
		
		// java modules will never "finish"
		return false;
	}
	
	public static void birth(Person person, long time)
	{
		Map<String, Object> attributes = person.attributes;
		
		attributes.put(Person.ID,  UUID.randomUUID().toString());
		attributes.put(Person.BIRTHDATE, time);
		person.events.create(time, Event.BIRTH, "Generator.run", true);
		attributes.put(Person.NAME, faker.name().name());
		attributes.put(Person.SOCIOECONOMIC_CATEGORY, "Middle"); // High Middle Low
		attributes.put(Person.RACE, "White"); // "White", "Native" (Native American), "Hispanic", "Black", "Asian", and "Other"
		attributes.put(Person.GENDER, person.rand() < 0.5 ? "M" : "F");

		Location.assignPoint(person, null);
		boolean hasStreetAddress2 = person.rand() < 0.5;
		attributes.put(Person.ADDRESS, faker.address().streetAddress(hasStreetAddress2));

		
		attributes.put(Person.HEIGHT, 51.0); // cm
		attributes.put(Person.WEIGHT, 3.5); // kg
		attributes.put("height-percentile", 50);
		attributes.put("weight-percentile", 50);
		
		attributes.put("AGE", 0);
		attributes.put("AGE-months", 0);
	}
	
	private static void age(Person person, long time)
	{
		int age = person.ageInYears(time);
		
		switch(age)
		{
		// TODO - none of these are critical so leaving them out for now
		case 16:
			// driver's license
			break;
		case 18:
			// name prefix
			break;
		case 20:
			// passport number
			break;
		case 27:
			// get married
			break;
		case 30:
			// "overeducated" -> suffix
			break;
		}
	}
	
	private static void grow(Person person, long time)
	{
		Map<String, Object> attributes = person.attributes;
		
		int prevAge = (int) attributes.get("AGE");
		int prevAgeMos = (int) attributes.get("AGE-months");
		
		int newAge = person.ageInYears(time);
		int newAgeMos = person.ageInMonths(time);
		attributes.put("AGE", newAge);
		attributes.put("AGE-months", newAgeMos);
		
		boolean shouldGrow;
		
		if (newAge > 20)
		{
			// adults 20 and over grow once per year
			shouldGrow = (newAge > prevAge);
		} else
		{
			// people under 20 grow once per month
			shouldGrow = (newAgeMos > prevAgeMos);
		}

		if (!shouldGrow)
		{
			return;
		}
		
		
		
		
	}
	
	private static double bmi(double heightCM, double weightKG)
	{
		return (weightKG / ((heightCM / 100.0) * (heightCM / 100.0)));
	}
	
	private static void diabeticVitalSigns(Person person, long time)
	{
		
	}
	
	private static final Code NATURAL_CAUSES = new Code("SNOMED-CT", "9855000", "Natural death with unknown cause");
	private static void death(Person person, long time)
	{
		double roll = person.rand();
		double likelihoodOfDeath = likelihoodOfDeath( person.ageInYears(time) );
		if (roll < likelihoodOfDeath)
		{
			person.recordDeath(time, NATURAL_CAUSES, "death");
		}
	}
	
	private static double likelihoodOfDeath(int age)
	{
		double yearlyRisk;
		
		if (age < 1) 
		{
			yearlyRisk = 508.1 / 100_000.0;
		} else if (age >= 1 && age <= 4) 
		{
			yearlyRisk = 15.6 / 100_000.0;
		} else if (age >= 5 && age <= 14) 
		{
			yearlyRisk = 10.6 / 100_000.0;
		} else if (age >= 15 && age <= 24) 
		{
			yearlyRisk = 56.4 / 100_000.0;
		} else if (age >= 25 && age <= 34) 
		{
			yearlyRisk = 74.7 / 100_000.0;
		} else if (age >= 35 && age <= 44) 
		{
			yearlyRisk = 145.7 / 100_000.0;
		} else if (age >= 45 && age <= 54) 
		{
			yearlyRisk = 326.5 / 100_000.0;
		} else if (age >= 55 && age <= 64) 
		{
			yearlyRisk = 737.8 / 100_000.0;
		} else if (age >= 65 && age <= 74) 
		{
			yearlyRisk = 1817.0 / 100_000.0;
		} else if (age >= 75 && age <= 84) 
		{
			yearlyRisk = 4877.3 / 100_000.0;
		} else if (age >= 85 && age <= 94) 
		{
			yearlyRisk = 13_499.4 / 100_000.0;
		} else 
		{
			yearlyRisk = 50_000.0 / 100_000.0;
		}
		
		double oneYearInMs = TimeUnit.DAYS.toMillis(365);
		double adjustedRisk = Utilities.convertRiskToTimestep(yearlyRisk, oneYearInMs);
		
		return adjustedRisk;
	}
	
}
