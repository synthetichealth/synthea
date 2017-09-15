package org.mitre.synthea.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.modules.HealthRecord.Code;
import org.mitre.synthea.modules.HealthRecord.Encounter;
import org.mitre.synthea.modules.HealthRecord.Procedure;
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
	
	private CommunityHealthWorker(String deploymentType)
	{
		// don't allow anyone else to instantiate this
		
		attributes.put(CommunityHealthWorker.ALCOHOL_SCREENING, Boolean.parseBoolean(Config.get("chw.alcohol_screening")));
		attributes.put(CommunityHealthWorker.ASPIRIN_MEDICATION, Boolean.parseBoolean(Config.get("chw.aspirin_medication")));
		attributes.put(CommunityHealthWorker.BLOOD_PRESSURE_SCREENING, Boolean.parseBoolean(Config.get("chw.blood_pressure_screening")));
		attributes.put(CommunityHealthWorker.COLORECTAL_CANCER_SCREENING, Boolean.parseBoolean(Config.get("chw.colorectal_cancer_screening")));
		attributes.put(CommunityHealthWorker.DIABETES_SCREENING, Boolean.parseBoolean(Config.get("chw.diabetes_screening")));
		attributes.put(CommunityHealthWorker.DIET_PHYSICAL_ACTIVITY, Boolean.parseBoolean(Config.get("chw.diet_physical_activity")));
		attributes.put(CommunityHealthWorker.EXERCISE_PT_INJURY_SCREENING, Boolean.parseBoolean(Config.get("chw.exercise_pt_injury_screening")));
		attributes.put(CommunityHealthWorker.LUNG_CANCER_SCREENING, Boolean.parseBoolean(Config.get("chw.lung_cancer_screening")));
		attributes.put(CommunityHealthWorker.OBESITY_SCREENING, Boolean.parseBoolean(Config.get("chw.obesity_screening")));
		attributes.put(CommunityHealthWorker.OSTEOPOROSIS_SCREENING, Boolean.parseBoolean(Config.get("chw.osteoporosis_screening")));
		attributes.put(CommunityHealthWorker.PREECLAMPSIA_ASPIRIN, Boolean.parseBoolean(Config.get("chw.preeclampsia_aspirin")));
		attributes.put(CommunityHealthWorker.PREECLAMPSIA_SCREENING, Boolean.parseBoolean(Config.get("chw.preeclampsia_screening")));
		attributes.put(CommunityHealthWorker.STATIN_MEDICATION, Boolean.parseBoolean(Config.get("chw.statin_medication")));
		attributes.put(CommunityHealthWorker.TOBACCO_SCREENING, Boolean.parseBoolean(Config.get("chw.tobacco_screening")));
		attributes.put(CommunityHealthWorker.VITAMIN_D_INJURY_SCREENING, Boolean.parseBoolean(Config.get("chw.vitamin_d_injury_screening")));
		
		Location.assignCity(this);

		attributes.put(DEPLOYMENT, deploymentType);

		//resourceID so that it's the same as Provider.
		attributes.put("resourceID", UUID.randomUUID().toString());
		
		attributes.put("name", "CHW providing " + deploymentType + " services in " + attributes.get(CITY));
	}
	
	private static Map<String,List<CommunityHealthWorker>> generateWorkers() {
		Map<String,List<CommunityHealthWorker>> workers = new HashMap<String,List<CommunityHealthWorker>>();
		int numWorkers = budget / cost;
		int numWorkersGenerated = 0;
		CommunityHealthWorker worker;
		for(int i=0; i < Math.round(numWorkers * community); i++)
		{
			worker = new CommunityHealthWorker(DEPLOYMENT_COMMUNITY);
			String city = (String) worker.attributes.get(CITY);
			if(!workers.containsKey(city)) {
				workers.put(city, new ArrayList<CommunityHealthWorker>());
			}
			workers.get(city).add(worker);
			numWorkersGenerated++;
		}
		for(int i=0; i < Math.round(numWorkers * emergency); i++)
		{
			worker = new CommunityHealthWorker(DEPLOYMENT_EMERGENCY);
			String city = (String) worker.attributes.get(CITY);
			if(!workers.containsKey(city)) {
				workers.put(city, new ArrayList<CommunityHealthWorker>());
			}
			workers.get(city).add(worker);
			numWorkersGenerated++;
		}
		for(int i=numWorkersGenerated; i < numWorkers; i++)
		{
			worker = new CommunityHealthWorker(DEPLOYMENT_POSTDISCHARGE);
			String city = (String) worker.attributes.get(CITY);
			if(!workers.containsKey(city)) {
				workers.put(city, new ArrayList<CommunityHealthWorker>());
			}
			workers.get(city).add(worker);
		}

		return workers;
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
	
	/**
	 * Check whether this CHW offers the given service.
	 * @param service Service name
	 * 
	 * @return true if the service is offered by this CHW
	 */
	public boolean offers(String service)
	{
		return (boolean) this.attributes.getOrDefault(service, false);
	}
	
	public void performEncounter(Person person, long time, String deploymentType)
	{
		// encounter class doesn't fit into the FHIR-prescribed set
		// so we use our own "community" encounter class
		Encounter enc = person.record.encounterStart(time, "community");
		enc.chw = this;
		// TODO - different codes based on different services offered?
		enc.codes.add( new Code("SNOMED-CT","389067005","Community health procedure") );

		this.incrementEncounters(deploymentType, Utilities.getYear(time));

		int chw_interventions = (int) person.attributes.getOrDefault(Person.CHW_INTERVENTION, 0);
		chw_interventions++;
		person.attributes.put(Person.CHW_INTERVENTION, chw_interventions);
		
		tobaccoScreening(person, time);
		alcoholScreening(person, time);
		lungCancerScreening(person, time);
		bloodPressureScreening(person, time);

		double adherence_chw_delta = Double.parseDouble( Config.get("lifecycle.aherence.chw_delta", "0.3"));
		double probability = (double) person.attributes.get(LifecycleModule.ADHERENCE_PROBABILITY);
		probability += (adherence_chw_delta);
		person.attributes.put(LifecycleModule.ADHERENCE_PROBABILITY, probability);
		
		enc.stop = time + TimeUnit.MINUTES.toMillis(35); // encounter lasts 35 minutes on avg
	}

	///////////////////////////
	// INDIVIDUAL SCREENINGS //
	///////////////////////////
	
	private void tobaccoScreening(Person person, long time)
	{
		if((boolean) person.attributes.getOrDefault(Person.SMOKER, false) && this.offers(TOBACCO_SCREENING)) 
		{
			Procedure ct = person.record.procedure(time, "Tobacco usage screening (procedure)");
			
			ct.codes.add(new Code("SNOMED-CT","171209009","Tobacco usage screening (procedure)"));

			double quit_smoking_chw_delta = Double.parseDouble( Config.get("lifecycle.quit_smoking.chw_delta", "0.3"));
			double smoking_duration_factor_per_year = Double.parseDouble( Config.get("lifecycle.quit_smoking.smoking_duration_factor_per_year", "1.0"));
			double probability = (double) person.attributes.get(LifecycleModule.QUIT_SMOKING_PROBABILITY);
			int numberOfYearsSmoking = (int) person.ageInYears(time) - 15;
			probability += (quit_smoking_chw_delta / (smoking_duration_factor_per_year * numberOfYearsSmoking));
			person.attributes.put(LifecycleModule.QUIT_SMOKING_PROBABILITY, probability);
		}
	}
	
	private void alcoholScreening(Person person, long time)
	{
		if((boolean) person.attributes.getOrDefault(Person.ALCOHOLIC, false) && this.offers(ALCOHOL_SCREENING)) 
		{
			Procedure ct = person.record.procedure(time, "Screening for alcohol abuse (procedure)");
			
			ct.codes.add(new Code("SNOMED-CT","713107002","Screening for alcohol abuse (procedure)"));
			
			double quit_alcoholism_chw_delta = Double.parseDouble( Config.get("lifecycle.quit_alcoholism.chw_delta", "0.3"));
			double alcoholism_duration_factor_per_year = Double.parseDouble( Config.get("lifecycle.quit_alcoholism.alcoholism_duration_factor_per_year", "1.0"));
			double probability = (double) person.attributes.get(LifecycleModule.QUIT_ALCOHOLISM_PROBABILITY);
			int numberOfYearsAlcoholic = (int) person.ageInYears(time) - 25;
			probability += (quit_alcoholism_chw_delta / (alcoholism_duration_factor_per_year * numberOfYearsAlcoholic));
			person.attributes.put(LifecycleModule.QUIT_ALCOHOLISM_PROBABILITY, probability);
		}
	}
	
	// The USPSTF recommends annual screening for lung cancer with low-dose computed tomography 
	// in adults ages 55 to 80 years who have a 30 pack-year smoking history and currently smoke 
	// or have quit within the past 15 years. 
	// Screening should be discontinued once a person has not smoked for 15 years or 
	// develops a health problem that substantially limits life expectancy or the ability or willingness to have curative lung surgery.
	private void lungCancerScreening(Person person, long time)
	{
		int age = person.ageInYears(time);
		boolean isSmoker = (boolean) person.attributes.getOrDefault(Person.SMOKER, false);
		int quitSmokingAge = (int)person.attributes.getOrDefault(LifecycleModule.QUIT_SMOKING_AGE, 0);
		int yearsSinceQuitting = age - quitSmokingAge;
		
		// TODO: 30-year pack history
		if (this.offers(LUNG_CANCER_SCREENING) && age >= 55 && age <= 80 && (isSmoker || yearsSinceQuitting <= 15))
		{
			Procedure ct = person.record.procedure(time, "Low dose computed tomography of thorax (procedure)");
			
			ct.codes.add(new Code("SNOMED-CT","16334891000119106","Low dose computed tomography of thorax"));
			
			if((boolean) person.attributes.getOrDefault("lung_cancer", false))
			{
				person.attributes.put("probability_of_lung_cancer_treatment", 1.0); // screening caught lung cancer, send them to treatment
			}
		}
	}
	
	private void bloodPressureScreening(Person person, long time){
		if (this.offers(BLOOD_PRESSURE_SCREENING)){
			Procedure ct = person.record.procedure(time, "Blood pressure screening - first call (procedure)");
			
			ct.codes.add(new Code("SNOMED-CT","185665008","Blood pressure screening - first call (procedure)"));
			
			double blood_pressure_chw_delta = Double.parseDouble( Config.get("lifecycle.blood_pressure.chw_delta", "0.1"));

			if(person.attributes.containsKey("cardio_risk")){
				double cardioRisk = (double) person.attributes.get("cardio_risk");
				cardioRisk = cardioRisk / (2 + blood_pressure_chw_delta);
				person.attributes.put("cardio_risk", cardioRisk);
			}

			if(person.attributes.containsKey("atrial_fibrillation_risk")){
				double af_risk = (double) person.attributes.get("atrial_fibrillation_risk");
				af_risk = af_risk / (2 + blood_pressure_chw_delta);
				person.attributes.put("atrial_fibrillation_risk", af_risk);
			}
			
			if(person.attributes.containsKey("stroke_risk")){
				double stroke_risk = (double) person.attributes.get("stroke_risk");
				stroke_risk = stroke_risk / (2 + blood_pressure_chw_delta);
				person.attributes.put("stroke_risk", stroke_risk);
			}
			
			if(person.attributes.containsKey("stroke_points")){
				int stroke_points = (int) person.attributes.get("stroke_points");
				stroke_points = stroke_points - 2;
				person.attributes.put("stroke_points", Math.max(0, stroke_points));
			}	
		}
	}
	
}
	