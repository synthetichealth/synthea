package org.mitre.synthea.modules;

import java.util.Arrays;
import java.util.List;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

public class HealthInsuranceModule extends Module 
{
	public static final String INSURANCE = "insurance";
	
	public static final String NO_INSURANCE = "no_insurance";
	public static final String PRIVATE = "private";
	public static final String MEDICAID = "medicaid";
	public static final String MEDICARE = "medicare";
	public static final String DUAL_ELIGIBLE = "dual_eligible";
	
	public long mandate_time;
	public double mandate_occupation;
	public int private_income_threshold;
	public double poverty_level;
	public double medicaid_level;
	
	public HealthInsuranceModule() {
		int mandate_year = Integer.parseInt( Config.get("generate.insurance.mandate.year", "2006") );
		mandate_time = Utilities.convertCalendarYearsToTime(mandate_year);
		mandate_occupation = Double.parseDouble( Config.get("generate.insurance.mandate.occupation", "0.2") );
		private_income_threshold = Integer.parseInt( Config.get("generate.insurance.private.minimum_income", "24000") );
		poverty_level = Double.parseDouble( Config.get("generate.demographics.socioeconomic.income.poverty","11000") );
		medicaid_level = 1.33 * poverty_level;
	}
	
	@SuppressWarnings("unchecked")
	public boolean process(Person person, long time) {

		if (!person.attributes.containsKey(INSURANCE))
		{
			// use 128 because it's a nice power of 2, and nobody will reach that age
			// nulls indicate not set
			person.attributes.put(INSURANCE, Arrays.asList(new String[128])); 
		}

		List<String> insurance = (List<String>) person.attributes.get(INSURANCE);
		
		int age = person.ageInYears(time);
		
		if (insurance.get(age) == null)
		{
			insurance.set(age, determineInsurance(person, age, time));
		}
		
		// java modules will never "finish"
		return false;
	}
	
	private String determineInsurance(Person person, int age, long time) {
		boolean female = (person.attributes.get(Person.GENDER).equals("F"));
		boolean pregnant = (person.attributes.containsKey("pregnant") && (boolean) person.attributes.get("pregnant"));
		boolean blind = (person.attributes.containsKey("blindness") && (boolean) person.attributes.get("blindness"));
		boolean esrd = (person.attributes.containsKey("end_stage_renal_disease") && (boolean) person.attributes.get("end_stage_renal_disease"));
		boolean sixty_five = (age >= 65);		
		double occupation = (Double) person.attributes.get(Person.OCCUPATION_LEVEL);
		int income = (Integer) person.attributes.get(Person.INCOME);
		boolean medicaid_income_eligible = ( income <= medicaid_level );
		
		boolean medicare = false;
		boolean medicaid = false;
		
		if(sixty_five || esrd) {
			medicare = true;
		}
		
		if((female && pregnant) || blind || medicaid_income_eligible) {
			medicaid = true;
		}
		
		if(medicare && medicaid) {
			return DUAL_ELIGIBLE;
		} else if(medicare) {
			return MEDICARE;
		} else if(medicaid) {
			return MEDICAID;
		} else {
			if(time >= mandate_time && occupation >= mandate_occupation) {
				return PRIVATE;
			}
			if(income >= private_income_threshold) {
				return PRIVATE;
			}
		}
		
		return NO_INSURANCE;
	}
}
