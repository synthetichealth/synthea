package org.mitre.synthea.modules;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


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
    private static final int[] age_chd_m;
    private static final int[] age_chd_f;
    
    private static final int[][] age_chol_chd_m;
    private static final int[][] age_chol_chd_f;
    
    private static final int[] age_smoke_chd_m;
    private static final int[] age_smoke_chd_f;
    
    private static final int[][] sys_bp_chd_m;
    private static final int[][] sys_bp_chd_f;
    
    private static final Map<Integer, Double> risk_chd_m;
    private static final Map<Integer, Double> risk_chd_f; 
    
    private static int[] hdl_lookup_chd;
    
    private static final Map<String, Integer> MEDICATION_AVAILABLE;
    static {
    	age_chd_m = new int[]{-9, -9, -9, -4, 0, 3, 6, 8, 10, 11, 12, 13};
    	age_chd_f = new int[]{-7, -7, -7, -3, 0, 3, 6, 8, 10, 12, 14, 16};

    	age_chol_chd_m = new int[][] 
    			{
    	            // <160, 160-199, 200-239, 240-279, >280
    	            {0, 4, 7, 9, 11}, // 20-29 years
    	            {0, 4, 7, 9, 11}, // 30-39 years
    	            {0, 3, 5, 6, 8}, // 40-49 years
    	            {0, 2, 3, 4, 5}, // 50-59 years
    	            {0, 1, 1, 2, 3}, // 60-69 years
    	            {0, 0, 0, 1, 1} // 70-79 years
    	         };

    	age_chol_chd_f = new int[][]
        	{
                 // <160, 160-199, 200-239, 240-279, >280
                 {0, 4, 8, 11, 13}, // 20-29 years
                 {0, 4, 8, 11, 13}, // 30-39 years
                 {0, 3, 6, 8, 10}, // 40-49 years
                 {0, 2, 4, 5, 7}, // 50-59 years
                 {0, 1, 2, 3, 4}, // 60-69 years
                 {0, 1, 1, 2, 2} // 70-79 years
             };

     	// 20-29, 30-39, 40-49, 50-59, 60-69, 70-79 age ranges
        age_smoke_chd_m = new int[]{8, 8, 5, 3, 1, 1};
        age_smoke_chd_f = new int[]{9, 9, 7, 4, 2, 1};


        hdl_lookup_chd = new int[]{2, 1, 0, -1}; // <40, 40-49, 50-59, >60

        // true/false refers to whether or not blood pressure is treated
        sys_bp_chd_m = new int[][]{
        // true, false
            { 0, 0 }, // <120
            { 1, 0 }, // 120-129
            { 2, 1 }, // 130-139
            { 2, 1 }, // 140-149
            { 2, 1 }, // 150-159
            { 3, 2 } // >=160
        };
        
        sys_bp_chd_f = new int[][]{
         // true, false
            { 0, 0 }, // <120
            { 3, 1 }, // 120-129
            { 4, 2 }, // 130-139
            { 5, 3 }, // 140-149
            { 5, 3 }, // 150-159
            { 6, 4 } // >=160
        };

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
    }

    private static List<String> filter_meds_by_year(List<String> meds, long time)
    {
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(time);
		double year = calendar.get(Calendar.YEAR);
    	return meds.stream().filter( med -> year >= MEDICATION_AVAILABLE.get(med)).collect(Collectors.toList());
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
		double cardioRisk = (double)person.attributes.getOrDefault("cardio_risk", -1.0);
		if (person.attributes.containsKey("coronary_heart_disease"))
		{
			return;
		}
        if (person.rand() < cardioRisk)
        {
        	person.attributes.put("coronary_heart_disease", true);
        	person.events.create(time, "coronary_heart_disease", "onsetCoronaryHeartDisease", true);
        }
	}
	
	private static void coronaryHeartDiseaseProgression(Person person, long time)
	{
		// numbers are from appendix: http://www.ncbi.nlm.nih.gov/pmc/articles/PMC1647098/pdf/amjph00262-0029.pdf	
	}
	
	private static void noCoronaryHeartDisease(Person person, long time)
	{
		// chance of getting a sudden cardiac arrest without heart disease. (Most probable cardiac event w/o cause or history)	
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
