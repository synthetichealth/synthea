package org.mitre.synthea.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.Location;


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
	//public static final String ORGANIZE = "Organize"; 

	public static final String CITY = "city";
	public static final String DEPLOYMENT = "deployment";
	public static final String DEPLOYMENT_COMMUNITY = "community";
	public static final String DEPLOYMENT_EMERGENCY = "emergency";
	public static final String DEPLOYMENT_POSTDISCHARGE = "postdischarge";
	
	public static int cost = Integer.parseInt(Config.get("generate.chw.cost"));
	public static int budget = Integer.parseInt(Config.get("generate.chw.budget"));
	public static double community = Double.parseDouble(Config.get("generate.chw.community", "0.50"));
	public static double emergency = Double.parseDouble(Config.get("generate.chw.emergency", "0.25"));
	public static double postdischarge = Double.parseDouble(Config.get("generate.chw.postdischarge", "0.25"));

	public static Map<String,List<CommunityHealthWorker>> workers = generateWorkers();
	
	public Map<String,Object> services;
	
	public CommunityHealthWorker(){ 
		services = new ConcurrentHashMap<String,Object>();
	}
	
	private static Map<String,List<CommunityHealthWorker>> generateWorkers() {
		Map<String,List<CommunityHealthWorker>> workers = new HashMap<String,List<CommunityHealthWorker>>();
		int numWorkers = budget / cost;
		int numWorkersGenerated = 0;
		CommunityHealthWorker worker;
		for(int i=0; i < Math.round(numWorkers * community); i++)
		{
			worker = generateCHW(DEPLOYMENT_COMMUNITY);
			String city = (String) worker.services.get(CITY);
			if(!workers.containsKey(city)) {
				workers.put(city, new ArrayList<CommunityHealthWorker>());
			}
			workers.get(city).add(worker);
			numWorkersGenerated++;
		}
		for(int i=0; i < Math.round(numWorkers * emergency); i++)
		{
			worker = generateCHW(DEPLOYMENT_COMMUNITY);
			String city = (String) worker.services.get(CITY);
			if(!workers.containsKey(city)) {
				workers.put(city, new ArrayList<CommunityHealthWorker>());
			}
			workers.get(city).add(worker);
			numWorkersGenerated++;
		}
		for(int i=numWorkersGenerated; i < numWorkers; i++)
		{
			worker = generateCHW(DEPLOYMENT_COMMUNITY);
			String city = (String) worker.services.get(CITY);
			if(!workers.containsKey(city)) {
				workers.put(city, new ArrayList<CommunityHealthWorker>());
			}
			workers.get(city).add(worker);
		}

		return workers;
	}
	
	public static CommunityHealthWorker generateCHW(String deploymentType){
		
		CommunityHealthWorker chw = new CommunityHealthWorker();
		
		chw.services.put(CommunityHealthWorker.ALCOHOL_SCREENING, Boolean.parseBoolean(Config.get("chw.alcohol_screening")));
		chw.services.put(CommunityHealthWorker.ASPIRIN_MEDICATION, Boolean.parseBoolean(Config.get("chw.aspirin_medication")));
		chw.services.put(CommunityHealthWorker.BLOOD_PRESSURE_SCREENING, Boolean.parseBoolean(Config.get("chw.blood_pressure_screening")));
		chw.services.put(CommunityHealthWorker.COLORECTAL_CANCER_SCREENING, Boolean.parseBoolean(Config.get("chw.colorectal_cancer_screening")));
		chw.services.put(CommunityHealthWorker.DIABETES_SCREENING, Boolean.parseBoolean(Config.get("chw.diabetes_screening")));
		chw.services.put(CommunityHealthWorker.DIET_PHYSICAL_ACTIVITY, Boolean.parseBoolean(Config.get("chw.diet_physical_activity")));
		chw.services.put(CommunityHealthWorker.EXERCISE_PT_INJURY_SCREENING, Boolean.parseBoolean(Config.get("chw.exercise_pt_injury_screening")));
		chw.services.put(CommunityHealthWorker.LUNG_CANCER_SCREENING, Boolean.parseBoolean(Config.get("chw.lung_cancer_screening")));
		chw.services.put(CommunityHealthWorker.OBESITY_SCREENING, Boolean.parseBoolean(Config.get("chw.obesity_screening")));
		//chw.services.put(CommunityHealthWorker.ORGANIZE, true);
		chw.services.put(CommunityHealthWorker.OSTEOPOROSIS_SCREENING, Boolean.parseBoolean(Config.get("chw.osteoporosis_screening")));
		chw.services.put(CommunityHealthWorker.PREECLAMPSIA_ASPIRIN, Boolean.parseBoolean(Config.get("chw.preeclampsia_aspirin")));
		chw.services.put(CommunityHealthWorker.PREECLAMPSIA_SCREENING, Boolean.parseBoolean(Config.get("chw.preeclampsia_screening")));
			
		chw.services.put(CommunityHealthWorker.STATIN_Medication, Boolean.parseBoolean(Config.get("chw.statin_medication")));
		chw.services.put(CommunityHealthWorker.TOBACCO_SCREENING, Boolean.parseBoolean(Config.get("chw.tobacco_screening")));
		chw.services.put(CommunityHealthWorker.VITAMIN_D_INJURY_SCREENING, Boolean.parseBoolean(Config.get("chw.vitamin_d_injury_screening")));
		Location.assignCity(chw);
		
		chw.services.put(DEPLOYMENT, deploymentType);
		return chw;
	}

	public static CommunityHealthWorker findNearbyCHW(Person person, long time, String deploymentType)
	{
		CommunityHealthWorker worker = null;
		String city = (String) person.attributes.get(Person.CITY);

		double probability = 0.0;
		switch(deploymentType) {
		case DEPLOYMENT_COMMUNITY:
			if(workers.containsKey(city)) {
				probability = (double)(workers.get(city).size()) / (double)Location.getPopulation(city);
			}
			break;
		case DEPLOYMENT_EMERGENCY:
			probability = 0.9;
			break;
		case DEPLOYMENT_POSTDISCHARGE:
			probability = 0.9;
			break;
		}
		if(person.rand() < probability && workers.containsKey(city)) {
			List<CommunityHealthWorker> candidates = workers.get(city).stream()
					.filter(p -> p.services.get(DEPLOYMENT).equals(deploymentType))
					.collect(Collectors.toList());
			if(!candidates.isEmpty()) {
				worker = candidates.get((int)person.rand(0, candidates.size()-1));
			}
		}
		return worker;
	}
	
	public static int getCost() {
		return CommunityHealthWorker.cost;
	}	
	
	public static void setCost(int cost){
		CommunityHealthWorker.cost = cost;
	}
	
	public static int getBudget(){
		return CommunityHealthWorker.budget;
	}
	
	public static void setBudget(int budget){
		CommunityHealthWorker.budget = budget;
	}
		
}
	
