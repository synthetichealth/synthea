package org.mitre.synthea.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.Location;
import org.mitre.synthea.world.Provider;


public class CommunityHealthWorker extends Provider {

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
	public static final String STATIN_MEDICATION = "Statin preventive medication";	

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
	
	private CommunityHealthWorker()
	{
		// don't allow anyone else to instantiate this
	}
	
	private static Map<String,List<CommunityHealthWorker>> generateWorkers() {
		Map<String,List<CommunityHealthWorker>> workers = new HashMap<String,List<CommunityHealthWorker>>();
		int numWorkers = budget / cost;
		int numWorkersGenerated = 0;
		CommunityHealthWorker worker;
		for(int i=0; i < Math.round(numWorkers * community); i++)
		{
			worker = generateCHW(DEPLOYMENT_COMMUNITY);
			String city = (String) worker.attributes.get(CITY);
			if(!workers.containsKey(city)) {
				workers.put(city, new ArrayList<CommunityHealthWorker>());
			}
			workers.get(city).add(worker);
			numWorkersGenerated++;
		}
		for(int i=0; i < Math.round(numWorkers * emergency); i++)
		{
			worker = generateCHW(DEPLOYMENT_EMERGENCY);
			String city = (String) worker.attributes.get(CITY);
			if(!workers.containsKey(city)) {
				workers.put(city, new ArrayList<CommunityHealthWorker>());
			}
			workers.get(city).add(worker);
			numWorkersGenerated++;
		}
		for(int i=numWorkersGenerated; i < numWorkers; i++)
		{
			worker = generateCHW(DEPLOYMENT_POSTDISCHARGE);
			String city = (String) worker.attributes.get(CITY);
			if(!workers.containsKey(city)) {
				workers.put(city, new ArrayList<CommunityHealthWorker>());
			}
			workers.get(city).add(worker);
		}

		return workers;
	}
	
	public static CommunityHealthWorker generateCHW(String deploymentType){
		
		CommunityHealthWorker chw = new CommunityHealthWorker();
		
		chw.attributes.put(CommunityHealthWorker.ALCOHOL_SCREENING, Boolean.parseBoolean(Config.get("chw.alcohol_screening")));
		chw.attributes.put(CommunityHealthWorker.ASPIRIN_MEDICATION, Boolean.parseBoolean(Config.get("chw.aspirin_medication")));
		chw.attributes.put(CommunityHealthWorker.BLOOD_PRESSURE_SCREENING, Boolean.parseBoolean(Config.get("chw.blood_pressure_screening")));
		chw.attributes.put(CommunityHealthWorker.COLORECTAL_CANCER_SCREENING, Boolean.parseBoolean(Config.get("chw.colorectal_cancer_screening")));
		chw.attributes.put(CommunityHealthWorker.DIABETES_SCREENING, Boolean.parseBoolean(Config.get("chw.diabetes_screening")));
		chw.attributes.put(CommunityHealthWorker.DIET_PHYSICAL_ACTIVITY, Boolean.parseBoolean(Config.get("chw.diet_physical_activity")));
		chw.attributes.put(CommunityHealthWorker.EXERCISE_PT_INJURY_SCREENING, Boolean.parseBoolean(Config.get("chw.exercise_pt_injury_screening")));
		chw.attributes.put(CommunityHealthWorker.LUNG_CANCER_SCREENING, Boolean.parseBoolean(Config.get("chw.lung_cancer_screening")));
		chw.attributes.put(CommunityHealthWorker.OBESITY_SCREENING, Boolean.parseBoolean(Config.get("chw.obesity_screening")));
		chw.attributes.put(CommunityHealthWorker.OSTEOPOROSIS_SCREENING, Boolean.parseBoolean(Config.get("chw.osteoporosis_screening")));
		chw.attributes.put(CommunityHealthWorker.PREECLAMPSIA_ASPIRIN, Boolean.parseBoolean(Config.get("chw.preeclampsia_aspirin")));
		chw.attributes.put(CommunityHealthWorker.PREECLAMPSIA_SCREENING, Boolean.parseBoolean(Config.get("chw.preeclampsia_screening")));
			
		chw.attributes.put(CommunityHealthWorker.STATIN_MEDICATION, Boolean.parseBoolean(Config.get("chw.statin_medication")));
		chw.attributes.put(CommunityHealthWorker.TOBACCO_SCREENING, Boolean.parseBoolean(Config.get("chw.tobacco_screening")));
		chw.attributes.put(CommunityHealthWorker.VITAMIN_D_INJURY_SCREENING, Boolean.parseBoolean(Config.get("chw.vitamin_d_injury_screening")));
		Location.assignCity(chw);

		chw.attributes.put(DEPLOYMENT, deploymentType);

		//resourceID so that it's the same as Provider.
		chw.attributes.put("resourceID", UUID.randomUUID().toString());
		
		chw.attributes.put("name", "CHW providing " + deploymentType + " services in " + chw.attributes.get(CITY));

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
					.filter(p -> p.attributes.get(DEPLOYMENT).equals(deploymentType))
					.collect(Collectors.toList());
			if(!candidates.isEmpty()) {
				worker = candidates.get((int)person.rand(0, candidates.size()-1));
			}
		}
		return worker;
	}		
}
	