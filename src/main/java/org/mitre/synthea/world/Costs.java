package org.mitre.synthea.world;

import org.mitre.synthea.helpers.GeographicalPracticeCostIndex;
import org.mitre.synthea.helpers.RelativeValueUnit;

public class Costs {

	public static void loadCostData(){
		BillingConcept.loadConceptMappings();
		RelativeValueUnit.loadRVUs();
		GeographicalPracticeCostIndex.loadGpciData();
	}
	
	public double calculateCost(String syntheaCode, boolean isFacility){
		//get hcpc mapping from concepts
		BillingConcept cncpt = BillingConcept.getConcept(syntheaCode); 
		String hcpcCode;
		try{
			hcpcCode = cncpt.getHcpcCode();
			
			//cncpt exists but has null hcpc value
			if(hcpcCode == null){
				Exception e = new Exception();
				throw e;
			}
		}catch(Exception e){
			//default to total hip replacement procedure if no hcpc mapping 
			hcpcCode = "27130";
			System.out.println("can't find hcpc mapping. default to 27130");
		}
			
		//get rvu object using hcpc
		RelativeValueUnit rvu = RelativeValueUnit.getRvu(hcpcCode);
		double workRvu = rvu.getWorkRvu();
		double malpracticeRvu = rvu.getMalpracticeRvu();
		double pracExpenseRvu = rvu.getNonfacilityPeRvu();
		if(isFacility)
			pracExpenseRvu = rvu.getFacilityPeRvu();
		
		//TODO - get locality from config/properties
		String locality = "REST OF MASSACHUSETTS";
		
		//use year 2017
		GeographicalPracticeCostIndex gpci = GeographicalPracticeCostIndex.getGpci(locality);
		double workGpci = gpci.getPwGpci2017();
		double pracExpenseGpci = gpci.getPeGpci2017();
		double malpracticeGpci = gpci.getMpGpci2017();
		
		//medicare equation
		double conversionFactor = 35.7751;
		return (workRvu * workGpci + pracExpenseRvu * pracExpenseGpci + malpracticeRvu
				* malpracticeGpci) * conversionFactor;
	}
	
	

}
