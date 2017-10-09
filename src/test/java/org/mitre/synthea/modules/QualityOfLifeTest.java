package org.mitre.synthea.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;

import static org.junit.Assert.*;

//test calculate, conditionsInYear, weight

public class QualityOfLifeTest {
	
	private Person person;
	
	public static final long stopTime = ((long) (365.25 * 35)) + 1;
	
	@Before
	public void init(){
		person = new Person(0);
		person.events.create(0, "birth", "QualityOfLifeTest", true);
		person.attributes.put("birthdate", 0L);
		
		// Diabetes - code = 44054006; dw = 0.049
		// ADD - code = 192127007; dw = 0.045
		// Asthma - code = 195967001; dw = 0.015
		
		//                   asthma
		//             |-----------------|
		//               ADD            diabetes
		//             |-----|     |-----------------|
		// |-----|-----|-----|-----|-----|-----|-----|
		// 0     5     10    15    20    25    30    35  
		
		Entry ADDCondition = person.record.conditionStart(TimeUnit.DAYS.toMillis((long) (365.25 * 10)), "192127007");
		ADDCondition.name = "Child attention deficit disorder";
		Code ADDCode = new Code("SNOMED", "192127007", "Child attention deficit disorder");
		ADDCondition.codes.add(ADDCode);
		
		Entry asthmaCondition = person.record.conditionStart(TimeUnit.DAYS.toMillis((long) (365.25 * 10)), "195967001");
		asthmaCondition.name = "Asthma";
		Code asthmaCode = new Code("SNOMED", "195967001", "Asthma");
		asthmaCondition.codes.add(asthmaCode);
		
		person.record.conditionEnd((TimeUnit.DAYS.toMillis((long) (365.25 * 15) - 1)), "192127007"); // ADD ends
		
		Entry diabetesCondition = person.record.conditionStart(TimeUnit.DAYS.toMillis((long) (365.25 * 20)), "44054006");
		diabetesCondition.name = "Diabetes";
		Code diabetesCode = new Code("SNOMED", "4405400", "Diabetes");
		diabetesCondition.codes.add(diabetesCode);
		
		person.record.conditionEnd((TimeUnit.DAYS.toMillis((long) (365.25 * 25) - 1)), "195967001"); // asthma ends
	}
	
	@Test
	public void testCalculateLiving(){
		// living patient
		// + 1 ms because (365.25 * 35) = 12783.75 as double and 12783 as long
		double[] qol = QualityOfLifeModule.calculate(person, TimeUnit.DAYS.toMillis(stopTime));
		
		double daly_living = qol[0];
		double qaly_living = qol[1];
		assertEquals(true, (daly_living > 1.7 && daly_living < 1.8));
		assertEquals(true, (qaly_living > 33 && qaly_living < 34));
	}
	
	@Test
	public void testCalculateDeceased(){
		// deceased patient
		person.events.create(TimeUnit.DAYS.toMillis((long) (365.25 * 35)), "death", "QualityOfLifeTest", true);
		double[] qol = QualityOfLifeModule.calculate(person, TimeUnit.DAYS.toMillis(stopTime));
		
		double daly_deceased = qol[0];
		double qaly_deceased = qol[1];
		assertEquals(true, (daly_deceased > 54 && daly_deceased < 55));
		assertEquals(true, (qaly_deceased > 33 && qaly_deceased < 34));
	}
	
	@Test
	public void testConditionsInYear(){
		List<Entry> allConditions = new ArrayList<Entry>();
		for(Encounter e : person.record.encounters){
			for(Entry condition : e.conditions){
				allConditions.add(condition);
			}
		}
		
		// conditions in year 5
		List<Entry> conditionsYear5 = QualityOfLifeModule.conditionsInYear(allConditions, TimeUnit.DAYS.toMillis((long) (365.25 * 5)), TimeUnit.DAYS.toMillis((long) (365.25 * 6)));
		List<Entry> empty = new ArrayList<Entry>();	
		assertEquals(empty, conditionsYear5);
		
		// conditions in year 10
		List<Entry> conditionsYear10 = QualityOfLifeModule.conditionsInYear(allConditions, TimeUnit.DAYS.toMillis((long) (365.25 * 10)), TimeUnit.DAYS.toMillis((long) (365.25 * 11)));
		assertEquals(2, conditionsYear10.size());
		assertEquals("Child attention deficit disorder", conditionsYear10.get(0).name);
		assertEquals("Asthma", conditionsYear10.get(1).name);
		
		// conditions in year 30
		List<Entry> conditionsYear30 = QualityOfLifeModule.conditionsInYear(allConditions, TimeUnit.DAYS.toMillis((long) (365.25 * 30)), TimeUnit.DAYS.toMillis((long) (365.25 * 31)));
		assertEquals(1, conditionsYear30.size());
		assertEquals("Diabetes", conditionsYear30.get(0).name);
	}
	
	@Test
	public void testWeight(){
		// age 15 with disability weight of 0.45
		double weight = QualityOfLifeModule.weight(0.45,  15);
		assertEquals(true, (weight > 0.614 && weight < 0.615));
	}
	
}