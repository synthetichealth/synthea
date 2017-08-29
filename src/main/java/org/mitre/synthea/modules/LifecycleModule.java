package org.mitre.synthea.modules;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.modules.HealthRecord.Code;
import org.mitre.synthea.world.Location;

import com.github.javafaker.Faker;
import com.google.gson.Gson;

public final class LifecycleModule extends Module 
{
	@SuppressWarnings("rawtypes")
	private static final Map growthChart = loadGrowthChart();
	private static final Faker faker = new Faker();
	private static final String AGE = "AGE";
	private static final String AGE_MONTHS = "AGE_MONTHS";
	
	public LifecycleModule() {
		this.name = "Lifecycle";
	}

	@SuppressWarnings("rawtypes")
	private static Map loadGrowthChart() {
		String filename = "/cdc_growth_charts.json";
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

	@Override
	public boolean process(Person person, long time) 
	{
		// run through all of the rules defined
		// ruby "rules" are converted to static functions here
		// since this is intended to only be temporary
		
		// birth(person, time); intentionally left out - call it only once from Generator
		if( age(person, time) ) {
			grow(person, time);
		}
		quitSmoking(person, time); 
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

		Location.assignPoint(person, (String)attributes.get(Person.CITY));
		boolean hasStreetAddress2 = person.rand() < 0.5;
		attributes.put(Person.ADDRESS, faker.address().streetAddress(hasStreetAddress2));

		double height_percentile = person.rand();
		double weight_percentile = person.rand();
		person.setVitalSign(VitalSign.HEIGHT_PERCENTILE, height_percentile);
		person.setVitalSign(VitalSign.WEIGHT_PERCENTILE, weight_percentile);
		person.setVitalSign(VitalSign.HEIGHT, 51.0); // cm
		person.setVitalSign(VitalSign.WEIGHT, 3.5);  // kg
		
		attributes.put(AGE, 0);
		attributes.put(AGE_MONTHS, 0);

		grow(person, time); // set initial height and weight from percentiles
	}
	
	/**
	 * @return whether or not the patient should grow
	 */
	private static boolean age(Person person, long time)
	{
		int prevAge = (int) person.attributes.get(AGE);
		int prevAgeMos = (int) person.attributes.get(AGE_MONTHS);
		
		int newAge = person.ageInYears(time);
		int newAgeMos = person.ageInMonths(time);
		person.attributes.put(AGE, newAge);
		person.attributes.put(AGE_MONTHS, newAgeMos);

		switch(newAge)
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
		
		boolean shouldGrow;
		if (newAge > 20)
		{
			// adults 20 and over grow once per year
			shouldGrow = (newAge > prevAge);
		} else {
			// people under 20 grow once per month
			shouldGrow = (newAgeMos > prevAgeMos);
		}
		return shouldGrow;
	}

	private static void grow(Person person, long time)
	{
		int age = person.ageInYears(time);
		int adult_max_weight_age = Integer.parseInt( Config.get("lifecycle.adult_max_weight_age", "49"));
		int geriatric_weight_loss_age = Integer.parseInt( Config.get("lifecycle.geriatric_weight_loss_age", "60"));

		double height = person.getVitalSign(VitalSign.HEIGHT);
		double weight = person.getVitalSign(VitalSign.WEIGHT);
		
		if(age < 20) {
			// follow growth charts
			String gender = (String) person.attributes.get(Person.GENDER);
			int ageInMonths = person.ageInMonths(time);
			height = lookupGrowthChart("height", gender, ageInMonths, person.getVitalSign(VitalSign.HEIGHT_PERCENTILE));
			weight = lookupGrowthChart("weight", gender, ageInMonths, person.getVitalSign(VitalSign.WEIGHT_PERCENTILE));
		} else if(age <= adult_max_weight_age) {
			// getting older and fatter
			double min = Double.parseDouble( Config.get("lifecycle.adult_weight_gain.min","1.0"));
			double max = Double.parseDouble( Config.get("lifecycle.adult_weight_gain.max","2.0"));
			double adult_weight_gain = person.rand(min, max);
			weight += adult_weight_gain;
		} else if(age >= geriatric_weight_loss_age) {
			// getting older and wasting away
			double min = Double.parseDouble( Config.get("lifecycle.geriatric_weight_loss.min","1.0"));
			double max = Double.parseDouble( Config.get("lifecycle.geriatric_weight_loss.max","2.0"));
			double geriatric_weight_loss = person.rand(min, max);
			weight -= geriatric_weight_loss;
		}
		
		person.setVitalSign(VitalSign.HEIGHT, height);
		person.setVitalSign(VitalSign.WEIGHT, weight);
		person.setVitalSign(VitalSign.BMI, bmi(height, weight));
	}

	@SuppressWarnings("rawtypes")
	public static double lookupGrowthChart(String heightOrWeight, String gender, int ageInMonths, double percentile)
	{
		String[] percentile_buckets = {"3", "5", "10", "25", "50", "75", "90", "95", "97"};
		
		Map chart = (Map) growthChart.get(heightOrWeight);
		Map byGender = (Map) chart.get(gender);
		Map byAge = (Map) byGender.get(Integer.toString(ageInMonths));
		int bucket = 0;
		for(int i=0; i < percentile_buckets.length; i++) {
			if( (Double.parseDouble(percentile_buckets[i]) / 100.0) <= percentile) {
				bucket = i;
			} else {
				break;
			}
		}
		return Double.parseDouble((String) byAge.get(percentile_buckets[bucket]));
	}
	
	private static double bmi(double heightCM, double weightKG)
	{
		return (weightKG / ((heightCM / 100.0) * (heightCM / 100.0)));
	}
	
	// LIPID PANEL  https://www.nlm.nih.gov/medlineplus/magazine/issues/summer12/articles/summer12pg6-7.html
    private static final int[] CHOLESTEROL = new int[] {160,200,239,259,279,300}; // # mg/dL
    private static final int[] TRIGLYCERIDES = new int[] {100,150,199,499,550,600}; // mg/dL
    private static final int[] HDL = new int[] { 80, 59, 40, 20, 10,  0}; // mg/dL

	
	private static void diabeticVitalSigns(Person person, long time)
	{
		// TODO - most of the rest of the vital signs
		boolean hypertension = (Boolean)person.attributes.getOrDefault("hypertension", false);
		/*
		    blood_pressure:
		      normal:
		        systolic: [100,139] # mmHg
		        diastolic: [70,89]  # mmHg
		      hypertensive:
		        systolic: [140,200] # mmHg
		        diastolic: [90,120] # mmHg
		 */
        if (hypertension)
        {
        	person.setVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE, person.rand(140, 200));
        	person.setVitalSign(VitalSign.DIASTOLIC_BLOOD_PRESSURE, person.rand(90, 120));
        }
        else
        {
        	person.setVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE, person.rand(100, 139));
        	person.setVitalSign(VitalSign.DIASTOLIC_BLOOD_PRESSURE, person.rand(70, 89));
        }
        
        int index = 0;
        if (person.attributes.containsKey("diabetes_severity"))
        {
        	index = (Integer) person.attributes.getOrDefault("diabetes_severity", 1);
        }
        
        double total_cholesterol = person.rand(CHOLESTEROL[index], CHOLESTEROL[index+1]);
        double triglycerides = person.rand(TRIGLYCERIDES[index], TRIGLYCERIDES[index+1]);
        double hdl = person.rand(HDL[index], HDL[index+1]);
        double ldl = total_cholesterol - hdl - (0.2 * triglycerides);
        
        person.setVitalSign(VitalSign.TOTAL_CHOLESTEROL, total_cholesterol);
        person.setVitalSign(VitalSign.TRIGLYCERIDES, triglycerides);
        person.setVitalSign(VitalSign.HDL, hdl);
        person.setVitalSign(VitalSign.LDL, ldl);
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
	
	public static void quitSmoking(Person person, long time){
		
		int age = person.ageInYears(time);
		
		if(person.attributes.containsKey("SMOKER")){
			if(person.attributes.get("SMOKER").equals(true) && person.attributes.containsKey(Person.INTERVENTION)){
				int smokingDuration = age - 16;
				int ageFirstCHW = ageFirstCHWIntervention(person);
							
				if(smokingDuration < 5){
					if(person.rand() < .8){
						person.attributes.put("SMOKER", false);
						person.attributes.put("QUIT SMOKING", ageFirstCHW);
					}
				} else if(smokingDuration > 5 && smokingDuration < 10){
					if(person.rand() < .6){
						person.attributes.put("SMOKER", false);
						person.attributes.put("QUIT SMOKING", ageFirstCHW);					}
				}
				else if(smokingDuration > 10 && smokingDuration < 20){
					if(person.rand() < .4){
						person.attributes.put("SMOKER", false);
						person.attributes.put("QUIT SMOKING", ageFirstCHW);					}
				} else{
					if(person.rand() < .2){
						person.attributes.put("SMOKER", false);
						person.attributes.put("QUIT SMOKING", ageFirstCHW);					}
				}
			}
		}
	}
	
	//get patient's age at first contact with CHW
	
	public static int ageFirstCHWIntervention (Person person){
		
		Map<Integer, CommunityHealthWorker> chws = (Map) person.attributes.get("communityHealthWorker");

		ArrayList<Integer> list = new ArrayList<Integer>(chws.keySet());
		
		  int age = list.get(0);
		  
		  for(int i=1;i<list.size();i++){
		    if(list.get(i) < age){
			  age = list.get(i);
			}
		  }
		return age;
		
	}
	
}
