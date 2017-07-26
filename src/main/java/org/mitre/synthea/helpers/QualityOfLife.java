package org.mitre.synthea.helpers;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.io.BufferedReader;

import org.mitre.synthea.modules.HealthRecord.Encounter;
import org.mitre.synthea.modules.HealthRecord.Entry;
import org.mitre.synthea.modules.Person;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

public class QualityOfLife{
	
	public static HashMap<String, LinkedTreeMap> disabilityWeights;
	
	public QualityOfLife(){
		String filename = "/gbd_disability_weights.json";
		try {
			InputStream stream = QualityOfLife.class.getResourceAsStream(filename);
			String json = new BufferedReader(new InputStreamReader(stream)).lines()
					.parallel().collect(Collectors.joining("\n"));
			Gson g = new Gson();
			disabilityWeights = g.fromJson(json, HashMap.class);
		}
		catch (Exception e) {
			System.err.println("ERROR: unable to load json: " + filename);
			e.printStackTrace();
			throw new ExceptionInInitializerError(e);
		}
	}
	
	public static void calculate(Person person, long stop){
        // Disability-Adjusted Life Year = DALY = YLL + YLD
        // Years of Life Lost = YLL = (1) * (standard life expectancy at age of death in years)
        // Years Lost due to Disability = YLD = (disability weight) * (average duration of case)
        // from http://www.who.int/healthinfo/global_burden_disease/metrics_daly/en/
		double yll = 0.0;
		double yld = 0.0;
		
		int age = person.ageInYears(stop);
		long birthdate = (long) person.attributes.get("birthdate");
		
		if(!person.alive(stop)){
	          // life expectancy equation derived from IHME GBD 2015 Reference Life Table
	          // 6E-5x^3 - 0.0054x^2 - 0.8502x + 86.16
	          // R^2 = 0.99978
	          double l = ((0.00006 * Math.pow(age, 3)) - (0.0054 * Math.pow(age, 2)) - (0.8502 * age) + 86.16);
	          yll = l;
		}
		// get list of conditions 
		List<Entry> allConditions = new ArrayList<Entry>();
		for(Encounter encounter : person.record.encounters){
			for(Entry condition : encounter.conditions){
				allConditions.add(condition);
			}
		}
		
		// calculate yld with yearly timestep
		for(int i = 0; i < age; i++){
			long yearStart = birthdate + i * TimeUnit.DAYS.toMillis(365);
			long yearEnd = birthdate + (i+1) * TimeUnit.DAYS.toMillis(365);
			List<Entry> conditionsInYear = conditionsInYear(allConditions, yearStart, yearEnd);
			
			for(Entry condition : conditionsInYear){
				double disabilityWeight = (double) disabilityWeights.get(condition.codes.get(0).display).get("disability_weight");
				double weight = weight(disabilityWeight, i+1);
				yld += weight;
			}
		}
		
		double daly = yll + yld;
		double qaly = age - yld;
		
		person.attributes.put("DALY", daly);
		person.attributes.put("QALY", qaly);
	}
	
	public static List<Entry> conditionsInYear(List<Entry> conditions, long yearStart, long yearEnd){
		List<Entry> conditionsInYear = new ArrayList<Entry>();
		for(Entry condition : conditions){
			if(disabilityWeights.containsKey(condition.codes.get(0).display)){
				if(yearStart >= condition.start && condition.start < yearEnd){
					conditionsInYear.add(condition);
				}
			}
		}
		return conditionsInYear;
	}
	
	public static double weight(double disabilityWeight, int age){
        // age_weight = 0.1658 * age * e^(-0.04 * age)
        // from http://www.who.int/quantifying_ehimpacts/publications/9241546204/en/
        // weight = age_weight * disability_weight
		double ageWeight = 0.1658 * age * Math.exp(-0.04 * age);
		double weight = ageWeight * disabilityWeight;
		return weight;
	}
}