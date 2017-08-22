package org.mitre.synthea.modules;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.io.BufferedReader;

import org.mitre.synthea.modules.HealthRecord.Encounter;
import org.mitre.synthea.modules.HealthRecord.Entry;

import com.google.gson.Gson;

public class QualityOfLifeModule extends Module 
{
	
	private static Map<String, Map<String,Object>> disabilityWeights = loadDisabilityWeights();
	
	public QualityOfLifeModule()
	{
		this.name = "Quality of Life";
	}
	
	@Override
	public boolean process(Person person, long time) 
	{
		if (!person.attributes.containsKey("QALY"))
		{
			person.attributes.put("QALY", new Double[128]); // use 128 because it's a nice power of 2, and nobody will reach that age
			person.attributes.put("DALY", new Double[128]); // use Double so we can have nulls to indicate not set
		}

		Double[] qalys = (Double[])person.attributes.get("QALY");
		Double[] dalys = (Double[])person.attributes.get("DALY");
		
		int age = person.ageInYears(time);
		
		if (qalys[age] == null)
		{
			double[] qol = calculate(person, time);
			
			dalys[age] = qol[0];
			qalys[age] = qol[1];
		}
		
		// java modules will never "finish"
		return false;
	}
	
	@SuppressWarnings("unchecked")
	private static Map<String,Map<String,Object>> loadDisabilityWeights() {
		String filename = "/gbd_disability_weights.json";
		try {
			InputStream stream = QualityOfLifeModule.class.getResourceAsStream(filename);
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
	
	public static double[] calculate(Person person, long stop){
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
		for(int i = 0; i < age + 1; i++){
			long yearStart = birthdate + TimeUnit.DAYS.toMillis((long) (365.25 * i));
			long yearEnd = birthdate + (TimeUnit.DAYS.toMillis((long) (365.25 * (i+1) - 1)));
			List<Entry> conditionsInYear = conditionsInYear(allConditions, yearStart, yearEnd);
			
			for(Entry condition : conditionsInYear){
				double disabilityWeight = (double) disabilityWeights.get(condition.codes.get(0).display).get("disability_weight");
				double weight = weight(disabilityWeight, i+1);
				yld += weight;
			}
		}
		
		double daly = yll + yld;
		double qaly = age - yld;
		
		return new double[] {daly, qaly};
	}
	
	public static List<Entry> conditionsInYear(List<Entry> conditions, long yearStart, long yearEnd){
		List<Entry> conditionsInYear = new ArrayList<Entry>();
		for(Entry condition : conditions){
			if(disabilityWeights.containsKey(condition.codes.get(0).display)){
				// condition.stop == 0 for conditions that have not yet ended
				if(yearStart >= condition.start && condition.start <= yearEnd && (condition.stop > yearStart || condition.stop == 0)){
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