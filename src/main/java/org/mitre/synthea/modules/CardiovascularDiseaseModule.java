package org.mitre.synthea.modules;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.mitre.synthea.modules.HealthRecord.Code;
import org.mitre.synthea.modules.HealthRecord.Entry;
import org.mitre.synthea.modules.HealthRecord.Procedure;


public final class CardiovascularDiseaseModule extends Module 
{
	public CardiovascularDiseaseModule() {
		this.name = "Cardiovascular Disease";
	}

	@Override
	public boolean process(Person person, long time) 
	{
		// run through all of the rules defined
		// ruby "rules" are converted to static functions here
		// since this is intended to only be temporary
		// until we can convert this module to GMF
		
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

	//////////////
	// RESOURCES//
	//////////////
	
    // estimate cardiovascular risk of developing coronary heart disease (CHD)
    // http://www.nhlbi.nih.gov/health-pro/guidelines/current/cholesterol-guidelines/quick-desk-reference-html/10-year-risk-framingham-table

    // Indices in the array correspond to these age ranges: 20-24, 25-29, 30-34 35-39, 40-44, 45-49,
    // 50-54, 55-59, 60-64, 65-69, 70-74, 75-79
    private static final int[] age_chd_m = {-9, -9, -9, -4, 0, 3, 6, 8, 10, 11, 12, 13};
    private static final int[] age_chd_f = {-7, -7, -7, -3, 0, 3, 6, 8, 10, 12, 14, 16};
    
	private static final int[][] age_chol_chd_m = {
			// <160, 160-199, 200-239, 240-279, >280
			{ 0, 4, 7, 9, 11 }, // 20-29 years
			{ 0, 4, 7, 9, 11 }, // 30-39 years
			{ 0, 3, 5, 6, 8 }, // 40-49 years
			{ 0, 2, 3, 4, 5 }, // 50-59 years
			{ 0, 1, 1, 2, 3 }, // 60-69 years
			{ 0, 0, 0, 1, 1 } // 70-79 years
	};
	
	private static final int[][] age_chol_chd_f = {
			// <160, 160-199, 200-239, 240-279, >280
			{ 0, 4, 8, 11, 13 }, // 20-29 years
			{ 0, 4, 8, 11, 13 }, // 30-39 years
			{ 0, 3, 6, 8, 10 }, // 40-49 years
			{ 0, 2, 4, 5, 7 }, // 50-59 years
			{ 0, 1, 2, 3, 4 }, // 60-69 years
			{ 0, 1, 1, 2, 2 } // 70-79 years
	};
    
	// 20-29, 30-39, 40-49, 50-59, 60-69, 70-79 age ranges
    private static final int[] age_smoke_chd_m = {8, 8, 5, 3, 1, 1};
    private static final int[] age_smoke_chd_f = {9, 9, 7, 4, 2, 1};
    
    // true/false refers to whether or not blood pressure is treated
	private static final int[][] sys_bp_chd_m = {
			// true, false
			{ 0, 0 }, // <120
			{ 1, 0 }, // 120-129
			{ 2, 1 }, // 130-139
			{ 2, 1 }, // 140-149
			{ 2, 1 }, // 150-159
			{ 3, 2 } // >=160
    };
	private static final int[][] sys_bp_chd_f = {
			// true, false
			{ 0, 0 }, // <120
			{ 3, 1 }, // 120-129
			{ 4, 2 }, // 130-139
			{ 5, 3 }, // 140-149
			{ 5, 3 }, // 150-159
			{ 6, 4 } // >=160
	};
    
    private static final Map<Integer, Double> risk_chd_m;
    private static final Map<Integer, Double> risk_chd_f; 
    
    private static final int[] hdl_lookup_chd = new int[]{2, 1, 0, -1}; // <40, 40-49, 50-59, >60
    
    // Framingham score system for calculating atrial fibrillation (significant factor for stroke risk)
	private static final int[][] age_af = {
			// age ranges: 45-49, 50-54, 55-59, 60-64, 65-69, 70-74, 75-79, 80-84, >84
			{ 1, 2, 3, 4, 5, 6, 7, 7, 8 }, // male
			{ -3, -2, 0, 1, 3, 4, 6, 7, 8 } // female
	};
	    
	    // only covers points 1-9. <=0 and >= 10 are in if statement
    private static final double[] risk_af_table = {
	      0.01, // 0 or less
	      0.02, 0.02, 0.03,
	      0.04, 0.06, 0.08,
	      0.12, 0.16, 0.22,
	      0.3 // 10 or greater
	    };
    
    private static final Map<String, Code> LOOKUP;
    private static final Map<String, Integer> MEDICATION_AVAILABLE;
    static {
        // framingham point scores gives a 10-year risk
        risk_chd_m = new HashMap<>();
        risk_chd_m.put(-1, 0.005); // '-1' represents all scores <0
        risk_chd_m.put(0, 0.01);
        risk_chd_m.put(1, 0.01);
        risk_chd_m.put(2, 0.01);
        risk_chd_m.put(3, 0.01);
        risk_chd_m.put(4, 0.01);
        risk_chd_m.put(5, 0.02);
        risk_chd_m.put(6, 0.02);
        risk_chd_m.put(7, 0.03);
        risk_chd_m.put(8, 0.04);
        risk_chd_m.put(9, 0.05);
        risk_chd_m.put(10, 0.06);
        risk_chd_m.put(11, 0.08);
        risk_chd_m.put(12, 0.1);
        risk_chd_m.put(13, 0.12);
        risk_chd_m.put(14, 0.16);
        risk_chd_m.put(15, 0.20);
        risk_chd_m.put(16, 0.25);
        risk_chd_m.put(17, 0.3); // '17' represents all scores >16
            
       risk_chd_f = new HashMap<>();
       risk_chd_f.put(8, 0.005); // '8' represents all scores <9
       risk_chd_f.put(9, 0.01);
       risk_chd_f.put(10, 0.01);
       risk_chd_f.put(11, 0.01);
       risk_chd_f.put(12, 0.01);
       risk_chd_f.put(13, 0.02);
       risk_chd_f.put(14, 0.02);
       risk_chd_f.put(15, 0.03);
       risk_chd_f.put(16, 0.04);
       risk_chd_f.put(17, 0.05);
       risk_chd_f.put(18, 0.06);
       risk_chd_f.put(19, 0.08);
       risk_chd_f.put(20, 0.11);
       risk_chd_f.put(21, 0.14);
       risk_chd_f.put(22, 0.17);
       risk_chd_f.put(23, 0.22);
       risk_chd_f.put(24, 0.27);
       risk_chd_f.put(25, 0.3); // '25' represents all scores >24

        MEDICATION_AVAILABLE = new HashMap<>();
        MEDICATION_AVAILABLE.put("clopidogrel", 1997);
        MEDICATION_AVAILABLE.put("simvastatin", 1991);
        MEDICATION_AVAILABLE.put("amlodipine", 1994);
        MEDICATION_AVAILABLE.put("nitroglycerin", 1878);
        MEDICATION_AVAILABLE.put("warfarin", 1954);
        MEDICATION_AVAILABLE.put("verapamil", 1981);
        MEDICATION_AVAILABLE.put("digoxin", 1954);
        MEDICATION_AVAILABLE.put("atorvastatin", 1996);
        MEDICATION_AVAILABLE.put("captopril", 1981);
        MEDICATION_AVAILABLE.put("alteplase", 1987);
        MEDICATION_AVAILABLE.put("epinephrine", 1906);
        MEDICATION_AVAILABLE.put("amiodarone", 1962);
        MEDICATION_AVAILABLE.put("atropine", 1903);
        
        LOOKUP = new HashMap<>();
        LOOKUP.put("atrial_fibrillation", new Code("SNOMED-CT", "49436004", "Atrial Fibrillation"));
        LOOKUP.put("coronary_heart_disease", new Code("SNOMED-CT", "53741008", "Coronary Heart Disease"));
        LOOKUP.put("stroke", new Code("SNOMED-CT", "230690007", "Stroke"));
        LOOKUP.put("cardiac_arrest", new Code("SNOMED-CT", "410429000", "Cardiac Arrest"));
        LOOKUP.put("myocardial_infarction", new Code("SNOMED-CT", "22298006", "Myocardial Infarction"));
    }

    private static List<String> filter_meds_by_year(List<String> meds, long time)
    {
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(time);
		double year = calendar.get(Calendar.YEAR);
    	return meds.stream().filter( med -> year >= MEDICATION_AVAILABLE.get(med)).collect(Collectors.toList());
    }
    
    
	
	////////////////////
	// MIGRATED RULES //
	////////////////////
	private static void startSmoking(Person person, long time)
	{
		// 9/10 smokers start before age 18. We will use 16.
	    // http://www.cdc.gov/tobacco/data_statistics/fact_sheets/youth_data/tobacco_use/
		if (person.attributes.get("SMOKER") == null && person.ageInYears(time) == 16)
		{
			Calendar calendar = Calendar.getInstance();
			calendar.setTimeInMillis(time);
			long year = calendar.get(Calendar.YEAR);
			Boolean smoker = person.rand() < likelihoodOfBeingASmoker(year);
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
	
	private static int bound(int value, int min, int max)
	{
		return Math.min(Math.max(value, min), max);
	}
	
	private static void calculateCardioRisk(Person person, long time)
	{
		int age = person.ageInYears(time);
		String gender = (String)person.attributes.get(Person.GENDER);
		Double sysBP = person.getVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE);
		Double chol = person.getVitalSign(VitalSign.TOTAL_CHOLESTEROL);
		if (sysBP == null || chol == null)
		{
			return;
		}

		Boolean bpTreated = (Boolean)person.attributes.getOrDefault("bp_treated?", false);

		Double hdl = person.getVitalSign(VitalSign.HDL);
		
       // calculate which index in a lookup array a number corresponds to based on ranges in scoring
      int short_age_range = bound((age - 20) / 5, 0, 11);
      int long_age_range = bound((age - 20) / 10, 0, 5);

      // 0: <160, 1: 160-199, 2: 200-239, 3: 240-279, 4: >280
      int chol_range = bound((chol.intValue() - 160) / 40 + 1, 0, 4);

      // 0: <120, 1: 120-129, 2: 130-139, 3: 140-149, 4: 150-159, 5: >=160
      int bp_range = bound((sysBP.intValue() - 120) / 10 + 1, 0, 5);
      int framingham_points = 0;
      
      int[] age_chd;
      int[][] age_chol_chd;
      int[] age_smoke_chd;
      int[][] sys_bp_chd;
      
      if (gender.equals("M"))
      {
    	  age_chd = age_chd_m;
    	  age_chol_chd = age_chol_chd_m;
    	  age_smoke_chd = age_smoke_chd_m;
    	  sys_bp_chd = sys_bp_chd_m;
      } else
      {
    	  age_chd = age_chd_f;
    	  age_chol_chd = age_chol_chd_f;
    	  age_smoke_chd = age_smoke_chd_f;
    	  sys_bp_chd = sys_bp_chd_f;
      }
      
      framingham_points += age_chd[short_age_range];
      framingham_points += age_chol_chd[long_age_range][chol_range];

      if ((Boolean)person.attributes.getOrDefault("SMOKER", false))
      {
        framingham_points += age_smoke_chd[long_age_range];
      }
      
      // 0: <40, 1: 40-49, 2: 50-59, 3: >60
      int hdl_range = bound((hdl.intValue() - 40) / 10 + 1, 0, 3);
      framingham_points += hdl_lookup_chd[hdl_range];
      
      int bp_treated = bpTreated ? 0 : 1;
      framingham_points += sys_bp_chd[bp_range][bp_treated];
      double risk;
      // restrict lower and upper bound of framingham score
	  if (gender.equals("M"))
      {
		  framingham_points = bound(framingham_points, 0, 17);
		  risk = risk_chd_m.get(framingham_points);
      } else
      {
    	  framingham_points = bound(framingham_points, 8, 25);
    	  risk = risk_chd_f.get(framingham_points);
      }

      person.attributes.put("cardio_risk", Utilities.convertRiskToTimestep(risk, TimeUnit.DAYS.toMillis(3650)));
	}
	
	private static void onsetCoronaryHeartDisease(Person person, long time)
	{
		if (person.attributes.containsKey("coronary_heart_disease"))
		{
			return;
		}
		
		double cardioRisk = (double)person.attributes.getOrDefault("cardio_risk", -1.0);
        if (person.rand() < cardioRisk)
        {
        	person.attributes.put("coronary_heart_disease", true);
        	person.events.create(time, "coronary_heart_disease", "onsetCoronaryHeartDisease", true);
        }
	}
	
	private static void coronaryHeartDiseaseProgression(Person person, long time)
	{
		// numbers are from appendix: http://www.ncbi.nlm.nih.gov/pmc/articles/PMC1647098/pdf/amjph00262-0029.pdf	
		boolean coronary_heart_disease = (Boolean)person.attributes.getOrDefault("coronary_heart_disease", false);
		
		if (!coronary_heart_disease)
		{
			return;
		}
		
		String gender = (String)person.attributes.get(Person.GENDER);
		
		double annual_risk;
		// http://www.ncbi.nlm.nih.gov/pmc/articles/PMC1647098/pdf/amjph00262-0029.pdf
		// annual probability of coronary attack given history of angina
		if (gender.equals("M"))
		{
			annual_risk = 0.042;
		} else
		{
			annual_risk = 0.015;
		}
		
		double cardiac_event_chance = Utilities.convertRiskToTimestep(annual_risk, TimeUnit.DAYS.toMillis(365));
		
		if (person.rand() < cardiac_event_chance)
		{
			String cardiac_event;
			
			if (person.rand() < 0.8) // Proportion of coronary attacks that are MI ,given history of CHD
			{
				cardiac_event = "myocardial_infarction";
			} else
			{
				cardiac_event = "cardiac_arrest";
			}
			
			person.events.create(time, cardiac_event, "coronaryHeartDiseaseProgression", false);
			// creates unprocessed emergency encounter. Will be processed at next time step.
			person.events.create(time, "emergency_encounter", "coronaryHeartDiseaseProgression", false);
			
			// TODO  Synthea::Modules::Encounters.emergency_visit(time, entity)
			
          double survival_rate = 0.095; // http://cpr.heart.org/AHAECC/CPRAndECC/General/UCM_477263_Cardiac-Arrest-Statistics.jsp
          // survival rate triples if a bystander is present
          if (person.rand() < 0.46)  // http://cpr.heart.org/AHAECC/CPRAndECC/AboutCPRFirstAid/CPRFactsAndStats/UCM_475748_CPR-Facts-and-Stats.jsp
          {
        	  survival_rate *= 3.0;
          }
          
          if (person.rand() > survival_rate)
          {        	  
        	  person.recordDeath(time, LOOKUP.get(cardiac_event), "coronaryHeartDiseaseProgression");
          }
		}
		
	}
	
	private static void noCoronaryHeartDisease(Person person, long time)
	{
		// chance of getting a sudden cardiac arrest without heart disease. (Most probable cardiac event w/o cause or history)
		if (person.attributes.containsKey("coronary_heart_disease"))
		{
			return;
		}
		
        double annual_risk = 0.00076;
        double cardiac_event_chance = Utilities.convertRiskToTimestep(annual_risk, TimeUnit.DAYS.toMillis(365));
        if (person.rand() < cardiac_event_chance)
        {
	        person.events.create(time, "cardiac_arrest", "noCoronaryHeartDisease", false);
	        person.events.create(time, "emergency_encounter", "noCoronaryHeartDisease", false);
	        // TODO Synthea::Modules::Encounters.emergency_visit(time, entity)
	        double survival_rate = 1 - (0.00069);
	        if (person.rand() < 0.46)
	        {
	        	survival_rate *= 3.0;
	        }
	        double annual_death_risk = 1 - survival_rate;
	        if(person.rand() < Utilities.convertRiskToTimestep(annual_death_risk, TimeUnit.DAYS.toMillis(365)));
	        {
	        	person.recordDeath(time, LOOKUP.get("cardiac_arrest"), "noCoronaryHeartDisease");
	        }
        }
	}
	
	private static void calculateAtrialFibrillationRisk(Person person, long time)
	{
		int age = person.ageInYears(time);
		if (age < 45 || person.attributes.containsKey("atrial_fibrillation") || person.getVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE) == null
				|| person.getVitalSign(VitalSign.BMI) == null)
		{
			return;
		}

          int af_score = 0;
          int age_range = Math.min((age - 45) / 5, 8);
          int gender_index = (person.attributes.get(Person.GENDER).equals("M")) ? 0 : 1;
          af_score += age_af[gender_index][age_range];
          if(person.getVitalSign(VitalSign.BMI) >= 30)
		  {
        	  af_score += 1;
		  }
          
          if (person.getVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE) >= 160)
          {
        	  af_score += 1;
          }
          
          if ( (Boolean) person.attributes.getOrDefault("bp_treated?", false) )
          {
        	  af_score += 1;
          }
          
          af_score = bound(af_score, 0, 10);

          double af_risk = risk_af_table[af_score]; // 10-yr risk
          person.attributes.put("atrial_fibrillation_risk", Utilities.convertRiskToTimestep(af_risk, TimeUnit.DAYS.toMillis(3650)));
	}
	
	private static void getAtrialFibrillation(Person person, long time)
	{
        if(!person.attributes.containsKey("atrial_fibrillation") 
        		&& person.attributes.containsKey("atrial_fibrillation_risk") 
        		&& person.rand() < (Double) person.attributes.get("atrial_fibrillation_risk"))
        {
        	person.events.create(time, "atrial_fibrillation", "getAtrialFibrillation", false);
        	person.attributes.put("atrial_fibrillation", true);
        }
	}

	// https://www.heart.org/idc/groups/heart-public/@wcm/@sop/@smd/documents/downloadable/ucm_449858.pdf
	private static final double[] stroke_rate_20_39 = {0.002, 0.007}; // Prevalence of stroke by age and sex (Male, Female)
	private static final double[] stroke_rate_40_59 = {0.019, 0.022};
	
	private static final double[][] ten_year_stroke_risk = {
		{ 0, 0.03, 0.03, 0.04, 0.04, 0.05, 0.05, 0.06, 0.07, 0.08, 0.1, // male section
	      0.11, 0.13, 0.15, 0.17, 0.2, 0.22, 0.26, 0.29, 0.33, 0.37,
	      0.42, 0.47, 0.52, 0.57, 0.63, 0.68, 0.74, 0.79, 0.84, 0.88 },
        { 0, 0.01, 0.01, 0.02, 0.02, 0.02, 0.03, 0.04, 0.04, 0.05, 0.06, // female
	      0.08, 0.09, 0.11, 0.13,  0.16, 0.19, 0.23, 0.27, 0.32, 0.37,
	      0.43, 0.5, 0.57, 0.64, 0.71, 0.78, 0.84 }
	};
	
	/* Original ruby code here for comparison. Java doesn't have ranges so instead 
	   we'll just keep the lower bound, and check against arr[i] and arr[i+i]
	  
	  # The index for each range corresponds to the number of points

      # data for men is first array, women in second.
      age_stroke = [
        [(54..56), (57..59), (60..62), (63..65), (66..68), (69..72),
         (73..75), (76..78), (79..81), (82..84), (85..999)],

        [(54..56), (57..59), (60..62), (63..64), (65..67), (68..70),
         (71..73), (74..76), (77..78), (79..81), (82..999)]
      ]

      untreated_sys_bp_stroke = [
        [(0..105), (106..115), (116..125), (126..135), (136..145), (146..155),
         (156..165), (166..175), (176..185), (185..195), (196..205)],

        [(0..95), (95..106), (107..118), (119..130), (131..143), (144..155),
         (156..167), (168..180), (181..192), (193..204), (205..216)]
      ]

      treated_sys_bp_stroke = [
        [(0..105), (106..112), (113..117), (118..123), (124..129), (130..135),
         (136..142), (143..150), (151..161), (162..176), (177..205)],

        [(0..95), (95..106), (107..113), (114..119), (120..125), (126..131),
         (132..139), (140..148), (149..160), (161..204), (205..216)]
      ]
	 */
	
	// the index for each range corresponds to the number of points
	private static final int[][] age_stroke = {
		{ 54, 57, 60, 63, 66, 69, 73, 76, 79, 82, 85 }, // male
		{ 54, 57, 60, 63, 65, 68, 71, 74, 77, 79, 82 }  // female
	};
	
	private static final int[][] untreated_sys_bp_stroke = {
		{ 0, 106, 116, 126, 136, 146, 156, 166, 176, 185, 196 }, // male
		{ 0, 95, 107, 119, 131, 144, 156, 168, 181, 193, 205}  // female
	};
	
	private static final int[][] treated_sys_bp_stroke = {
		{ 0, 106, 113, 118, 124, 130, 136, 143, 151, 162, 177 }, // male
		{ 0, 95, 107, 114, 120, 126, 132, 140, 149, 161, 205 }  // female
	};
	
	private static final int getIndexForValueInRangelist(int value, int[] data)
	{
		for (int i = 0 ; i < data.length - 1 ; i++)
		{
			if (data[i] <= value && value <= data[i+1])
			{
				return i;
			}
		}
		// the last segment is open-ended
		if (value >= data[data.length - 1])
		{
			return data.length - 1;
		}
		
		// shouldn't be possible to get here if we do everything right
		throw new RuntimeException("unexpected value " + value + " for data " + Arrays.toString(data));
	}
	
	private static final double[] diabetes_stroke = { 2, 3 };
	private static final double[] chd_stroke_points = { 4, 2 };
	private static final double[] atrial_fibrillation_stroke_points = { 4, 6 };
	
	private static void calculateStrokeRisk(Person person, long time)
	{
		Double bloodPressure = person.getVitalSign(VitalSign.SYSTOLIC_BLOOD_PRESSURE);
		if (bloodPressure == null)
		{
			return;
		}
		
		// https://www.heart.org/idc/groups/heart-public/@wcm/@sop/@smd/documents/downloadable/ucm_449858.pdf
        // calculate stroke risk based off of prevalence of stroke in age group for people younger than 54. Framingham score system does not cover these.
		
		int genderIndex = ((String)person.attributes.get(Person.GENDER)).equals("M") ? 0 : 1;
		
		int age = person.ageInYears(time);
		
		
		if (age < 20)
		{
			// no risk set
			return;
		} else if (age < 40)
		{
			double rate = stroke_rate_20_39[genderIndex];
			person.attributes.put("stroke_risk", Utilities.convertRiskToTimestep(rate, TimeUnit.DAYS.toMillis(3650)));
			return;
		} else if (age < 55)
		{
			double rate = stroke_rate_40_59[genderIndex];
			person.attributes.put("stroke_risk", Utilities.convertRiskToTimestep(rate, TimeUnit.DAYS.toMillis(3650)));
			return;
		}
		
		int stroke_points = 0;
		if ( (Boolean) person.attributes.getOrDefault("SMOKER", false))
		{
			stroke_points += 3;
		}
		if ( (Boolean) person.attributes.getOrDefault("left_ventricular_hypertrophy", false))
		{
			stroke_points += 5;
		}
		
		stroke_points += getIndexForValueInRangelist(age, age_stroke[genderIndex]);
		
		int bp = bloodPressure.intValue();
		// TODO treating blood pressure currently is not a feature. Modify this for when it is.
		if ( (Boolean) person.attributes.getOrDefault("bp_treated?", false))
		{
			stroke_points += getIndexForValueInRangelist(bp, treated_sys_bp_stroke[genderIndex]);
		} else
		{
			stroke_points += getIndexForValueInRangelist(bp, untreated_sys_bp_stroke[genderIndex]);
		}
		
		if ( (Boolean) person.attributes.getOrDefault("diabetes", false))
		{
			stroke_points += diabetes_stroke[genderIndex];
		}
		
		if ( (Boolean) person.attributes.getOrDefault("coronary_heart_disease", false))
		{
			stroke_points += chd_stroke_points[genderIndex];
		}

		if ( (Boolean) person.attributes.getOrDefault("atrial_fibrillation", false))
		{
			stroke_points += atrial_fibrillation_stroke_points[genderIndex];
		}

		double ten_stroke_risk;
		
		if (stroke_points >= ten_year_stroke_risk[genderIndex].length)
		{
			ten_stroke_risk = ten_year_stroke_risk[genderIndex][stroke_points];
		} else
		{
			// off the charts
			int worst_case = ten_year_stroke_risk[genderIndex].length - 1;
			ten_stroke_risk = ten_year_stroke_risk[genderIndex][worst_case];
		}
		
		// divide 10 year risk by 365 * 10 to get daily risk.
		person.attributes.put("stroke_risk", Utilities.convertRiskToTimestep(ten_stroke_risk, TimeUnit.DAYS.toMillis(3650)));
		person.attributes.put("stroke_points", stroke_points); 
	}

	private static void getStroke(Person person, long time)
	{
        if(person.attributes.containsKey("stroke_risk") 
        		&& person.rand() < (Double)person.attributes.get("stroke_risk"))
        {
        	person.events.create(time, "stroke", "getStroke", false);
        	person.attributes.put("stroke_history", true);
            person.events.create(time + TimeUnit.MINUTES.toMillis(10), "emergency_encounter", "getStroke", false);
           // TODO Synthea::Modules::Encounters.emergency_visit(time + 15.minutes, entity)
            if (person.rand() < 0.15) // Strokes are fatal 10-20 percent of cases https://stroke.nih.gov/materials/strokechallenges.htm
            {
            	person.recordDeath(time, LOOKUP.get("stroke"), "getStroke");
            }
        }
	}

	private static void heartHealthyLifestyle(Person person, long time)
	{
		// TODO - intentionally ignoring this rule for now; it only sets careplan activities and reasons
	}

	private static void chdTreatment(Person person, long time)
	{
		List<String> meds = filter_meds_by_year(Arrays.asList("clopidogrel", "simvastatin", "amlodipine", "nitroglycerin"), time);
		
		if ( (Boolean) person.attributes.getOrDefault("coronary_heart_disease", false))
		{
			for (String med : meds)
			{
//				prescribe_medication();
			}
		} else if (person.attributes.containsKey("medications"))
		{
			for (String med : meds)
			{
				// stop_medication
			}
		}
		
	}

	private static void atrialFibrillationTreatment(Person person, long time)
	{
		List<String> meds = filter_meds_by_year(Arrays.asList("warfarin", "verapamil", "digoxin"), time);
		
		if ( (Boolean) person.attributes.getOrDefault("atrial_fibrillation", false))
		{
			for (String med : meds)
			{
//				prescribe_medication();
			}
			
	          // catheter ablation is a more extreme measure than electrical cardioversion and is usually only performed
	          // when medication and other procedures are not preferred or have failed. As a rough simulation of this,
	          // we arbitrarily chose a 20% chance of getting catheter ablation and 80% of getting cardioversion
	          String afib_procedure = person.rand() < 0.2 ? "catheter_ablation" : "electrical_cardioversion";
	          
	          Map<String,List<String>> cardiovascular_procedures = (Map<String,List<String>>) person.attributes.get("cardiovascular_procedures");
	          
	          if (cardiovascular_procedures == null)
	          {
	        	  cardiovascular_procedures = new HashMap<String, List<String>>();
	        	  person.attributes.put("cardiovascular_procedures", cardiovascular_procedures);
	          }
	          cardiovascular_procedures.put("atrial_fibrillation", Arrays.asList(afib_procedure));
		} else if (person.attributes.containsKey("medications"))
		{
			for (String med : meds)
			{
				// stop_medication
			}
		}
	}
	
	private static void performEncounter(Person person, long time)
	{
		// step 1 - diagnosis
		for (String diagnosis : new String[] {"coronary_heart_disease", "atrial_fibrillation"})
		{
			if ( (Boolean) person.attributes.getOrDefault(diagnosis, false) && !person.record.present.containsKey(diagnosis))
			{
				Code code = LOOKUP.get(diagnosis);
				Entry conditionEntry = person.record.conditionStart(time, code.display);
				conditionEntry.codes.add(code);
			}
		}

		// step 2 - care plan
		// TODO - intentionally ignored at the moment
		
		// step 3 - medications
		
		// step 4 - procedures
		Map<String,List<String>> cardiovascular_procedures = (Map<String,List<String>>) person.attributes.get("cardiovascular_procedures");
        
        if (cardiovascular_procedures != null)
        {
        	for (Map.Entry<String, List<String>> entry : cardiovascular_procedures.entrySet())
        	{
        		String reason = entry.getKey();
        		List<String> procedures = entry.getValue();
        		
        		for (String proc : procedures)
        		{
        			if (!person.record.present.containsKey(proc))
        			{
        				// TODO: assumes a procedure will only be performed once, might need to be revisited
        				Code code = LOOKUP.get(proc);
        				Procedure procedure = person.record.procedure(time, code.display);
        				procedure.codes.add(code);
        				procedure.reasons.add(reason);
        				// TODO provider stuff, increment procedures
        			}
        		}
        		
        	}
        }
	}
	
	private static void performEmergency(Person person, long time)
	{
		
	}
	
}
