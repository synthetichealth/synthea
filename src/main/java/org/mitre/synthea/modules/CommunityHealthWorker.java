package org.mitre.synthea.modules;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.mitre.synthea.helpers.Config;


public class CommunityHealthWorker {

	public static final String LUNG_CANCER_SCREENING = "Lung cancer screening";	
	public static final String TOBACCO_SCREENING = "Tobacco screening";	
	public static final String ALCOHOL_SCREENING = "Alcohol screening";	
	public static final String OBESITY_SCREENING = "Obesity screening";	
	public static final String BLOOD_PRESSURE_SCREENING = "Blood pressure screening";
	public static final String DIABETES_SCREENING = "Diabetes screening";
	public static final String COLORECTAL_CANCER_SCREENING = "Colorectal cancer screening"; 
	public static final String PREECLAMPSIA_SCREENING = "Preeclampsia screening";	
	public static final String OSTEOPOROSIS_SCREENING = "Osteoporosis screening";	
	
	public static final String ASPIRIN_MEDICATION = "Aspirin Medication";	
	public static final String PREECLAMPSIA_ASPIRIN = "Preeclampsia aspirin";	
	public static final String EXERCISE_PT_INJURY_SCREENING = "Fall prevention in older adults: Exercise or physical therapy";
	public static final String VITAMIN_D_INJURY_SCREENING = "Fall prevention in older adults: Vitamin D";
	public static final String DIET_PHYSICAL_ACTIVITY = "Diet and physical activity counseling";
	public static final String STATIN_Medication = "Statin preventive medication";	
	
	// TODO social support variable, to be measured/implemented later on
	public static final String ORGANIZE = "Organize"; 

	public static final String CITY = "city";
	
	public static final int cost = Integer.parseInt(Config.get("generate.cost"));

	// TODO Should be global variables, not necessarily specific to CHWs
	// Will determine the scenarios in which CHWs are assigned
	//public static boolean enabledforER;
	//public static boolean discharged;
	
	public static Map<String,Object> services;
	
	//TODO possible arguments, randomization/computation of services later on
	public CommunityHealthWorker(){ 
		services = new ConcurrentHashMap<String,Object>();
	}

	public static void generateCHW(CommunityHealthWorker chw){
					
		services.put(CommunityHealthWorker.ALCOHOL_SCREENING, chw);
		services.put(CommunityHealthWorker.ASPIRIN_MEDICATION, chw);
		services.put(CommunityHealthWorker.BLOOD_PRESSURE_SCREENING, chw);
		services.put(CommunityHealthWorker.COLORECTAL_CANCER_SCREENING, chw);
		services.put(CommunityHealthWorker.DIABETES_SCREENING, chw);
		services.put(CommunityHealthWorker.DIET_PHYSICAL_ACTIVITY, chw);
		services.put(CommunityHealthWorker.EXERCISE_PT_INJURY_SCREENING, chw);
		services.put(CommunityHealthWorker.LUNG_CANCER_SCREENING, chw);
		services.put(CommunityHealthWorker.OBESITY_SCREENING, chw);
		services.put(CommunityHealthWorker.ORGANIZE, chw);
		services.put(CommunityHealthWorker.OSTEOPOROSIS_SCREENING, chw);
		services.put(CommunityHealthWorker.PREECLAMPSIA_ASPIRIN, chw);
		services.put(CommunityHealthWorker.PREECLAMPSIA_SCREENING, chw);
			
		services.put(CommunityHealthWorker.STATIN_Medication, chw);
		services.put(CommunityHealthWorker.TOBACCO_SCREENING, chw);
		services.put(CommunityHealthWorker.VITAMIN_D_INJURY_SCREENING, chw);

	}

	public static int getCost() {
		return CommunityHealthWorker.cost;
	}		
}
	
