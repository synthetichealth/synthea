package org.mitre.synthea.world.concepts;

import org.mitre.synthea.world.concepts.HealthRecord.Entry;

public class Costs {

  public static void loadCostData() {
    BillingConcept.loadConceptMappings();
    RelativeValueUnit.loadRVUs();
    GeographicalPracticeCostIndex.loadGpciData();
  }

  public static double calculateCost(Entry entry, boolean isFacility) {
    String syntheaCode = entry.codes.get(0).code;

    // get hcpc mapping from concepts
    BillingConcept cncpt = BillingConcept.getConcept(syntheaCode);
    String hcpcCode;
    if (cncpt == null || cncpt.getHcpcCode() == null) {
      // default to total hip replacement procedure if no hcpc mapping
      hcpcCode = "27130";
    } else {
      hcpcCode = cncpt.getHcpcCode();
    }

    // get rvu object using hcpc
    RelativeValueUnit rvu = RelativeValueUnit.getRvu(hcpcCode);
    double workRvu = rvu.getWorkRvu();
    double malpracticeRvu = rvu.getMalpracticeRvu();
    double pracExpenseRvu = rvu.getNonfacilityPeRvu();
    if (isFacility) {
      pracExpenseRvu = rvu.getFacilityPeRvu();
    }

    // TODO - get locality from config/properties
    String locality = "REST OF MASSACHUSETTS";

    // use year 2017
    GeographicalPracticeCostIndex gpci = GeographicalPracticeCostIndex.getGpci(locality);
    double workGpci = gpci.getPwGpci2017();
    double pracExpenseGpci = gpci.getPeGpci2017();
    double malpracticeGpci = gpci.getMpGpci2017();

    // medicare equation
    double conversionFactor = 35.7751;
    return (workRvu * workGpci + pracExpenseRvu * pracExpenseGpci
        + malpracticeRvu * malpracticeGpci) * conversionFactor;
  }
}
