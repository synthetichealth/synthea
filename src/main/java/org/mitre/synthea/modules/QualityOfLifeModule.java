package org.mitre.synthea.modules;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;

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
			person.attributes.put("QALY", new LinkedHashMap<Integer,Double>());
			person.attributes.put("DALY", new LinkedHashMap<Integer,Double>());
			person.attributes.put("QOL", new LinkedHashMap<Integer,Double>());
			// linked hashmaps to preserve insertion order, and then we can iterate by year
		}

		Map<Integer,Double> qalys = (Map<Integer,Double>)person.attributes.get("QALY");
		Map<Integer,Double> dalys = (Map<Integer,Double>)person.attributes.get("DALY");
		Map<Integer,Double> qols = (Map<Integer,Double>)person.attributes.get("QOL");
		
		int year = Utilities.getYear(time);
		
		if (!qalys.containsKey(year))
		{
//			double age = person.ageInYears(time) + 1;
			double[] values = calculate(person, time);

			dalys.put(year, values[0]);
			qalys.put(year, values[1]);
			qols.put(year, values[2]);
			person.attributes.put("most-recent-daly", values[0]);
			person.attributes.put("most-recent-qaly", values[1]);
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
	
	/**
	 * Calculate the HALYs for this person, at the given time.
	 * HALYs include QALY and DALY.
	 * @param person Person to calculate
	 * @param stop current timestamp
	 * @return array of [daly (cumulative), qaly (cumulative), current disability weight]
	 */
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
		
		double disabilityWeight = 0.0;
		// calculate yld with yearly timestep
		for(int i = 0; i < age + 1; i++){
			long yearStart = birthdate + TimeUnit.DAYS.toMillis((long) (365.25 * i));
			long yearEnd = birthdate + (TimeUnit.DAYS.toMillis((long) (365.25 * (i+1) - 1)));
			List<Entry> conditionsInYear = conditionsInYear(allConditions, yearStart, yearEnd);
			
			disabilityWeight = 0.0;
			
			for(Entry condition : conditionsInYear){
				disabilityWeight += (double) disabilityWeights.get(condition.codes.get(0).display).get("disability_weight");
			}
			
			disabilityWeight = Math.min(1.0, weight(disabilityWeight, i+1));
			yld += disabilityWeight;
		}
		
		double daly = yll + yld;
		double qaly = age - yld;
		
		return new double[] {daly, qaly, 1-disabilityWeight};
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