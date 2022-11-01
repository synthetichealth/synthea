package org.mitre.synthea.world.agents;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.behaviors.payeradjustment.IPayerAdjustment;
import org.mitre.synthea.world.agents.behaviors.payeradjustment.PayerAdjustmentFixed;
import org.mitre.synthea.world.agents.behaviors.payeradjustment.PayerAdjustmentNone;
import org.mitre.synthea.world.agents.behaviors.payeradjustment.PayerAdjustmentRandom;
import org.mitre.synthea.world.agents.behaviors.planeligibility.PlanEligibilityFinder;
import org.mitre.synthea.world.agents.behaviors.planfinder.IPlanFinder;
import org.mitre.synthea.world.agents.behaviors.planfinder.PlanFinderBestRates;
import org.mitre.synthea.world.agents.behaviors.planfinder.PlanFinderPriority;
import org.mitre.synthea.world.agents.behaviors.planfinder.PlanFinderRandom;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;
import org.mitre.synthea.world.geography.Location;

/**
 * A class that maintains and manages Payers.
 */
public class PayerManager {

  // Payer adjustment algorithm choices:
  private static final String NONE = "none";
  private static final String FIXED = "fixed";
  // Payer selection algorithm choices:
  private static final String RANDOM = "random";
  private static final String BEST_RATE = "best_rate";
  private static final String PRIORITY = "priority";

  // Plan/Payer CSV headers.
  private static final String NAME = "Name";
  private static final String ID = "Id";
  private static final String PRIORITY_LEVEL = "Priority Level";
  private static final String STATES_COVERED = "States Covered";
  private static final String OWNERSHIP = "Ownership";
  private static final String SERVICES_COVERED = "Services Covered";
  private static final String DEDUCTIBLE = "Deductible";
  private static final String COINSURANCE = "Default Coinsurance";
  private static final String COPAY = "Default Copay";
  private static final String MONTHLY_PREMIUM = "Monthly Premium";
  private static final String MEDICARE_SUPPLEMENT = "Medicare Supplement";
  private static final String START_YEAR = "Start Year";
  private static final String END_YEAR = "End Year";
  private static final String PAYER_ID = "Payer Id";
  private static final String PLAN_ID = "Plan Id";
  private static final String ELIGIBILITY_POLICY = "Eligibility Policy";

  public static final String GOV_OWNERSHIP = "GOVERNMENT";
  public static final String PRIVATE_OWNERSHIP = "PRIVATE";
  public static final String NO_INSURANCE = "NO_INSURANCE";

  public static final String MEDICARE =
      Config.get("generate.payers.insurance_companies.medicare", "Medicare");
  public static final String MEDICAID =
      Config.get("generate.payers.insurance_companies.medicaid", "Medicaid");
  public static final String DUAL_ELIGIBLE =
      Config.get("generate.payers.insurance_companies.dual_eligible", "Dual Eligible");

  /* Set of all Private Payers imported. */
  private static final Set<Payer> privatePayers = new HashSet<Payer>();
  /* Map of all Government Payers imported. */
  private static final Map<String, Payer> governmentPayers = new HashMap<String, Payer>();

  /* No Insurance Payer. */
  public static Payer noInsurance;

  /* U.S. States loaded. */
  private static Set<String> statesLoaded = new HashSet<String>();

  // Payer Finder.
  private static IPlanFinder planFinder;

  /**
   * Load into cache the list of payers for a state.
   *
   * @param location the state being loaded.
   */
  public static void loadPayers(Location location) {
    // Load the plan eligibility algorithms.
    String eligibilitiesFile = Config.get("generate.payers.insurance_plans.eligibilities_file");
    PlanEligibilityFinder.buildPlanEligibilities(location.state, eligibilitiesFile);

    // Build the Plan Finder.
    planFinder = buildPlanFinder();
    if (!statesLoaded.contains(location.state)
        || !statesLoaded.contains(Location.getAbbreviation(location.state))
        || !statesLoaded.contains(Location.getStateName(location.state))) {
      try {
        String payerFile = Config.get("generate.payers.insurance_companies.default_file");
        loadPayers(location, payerFile);

        statesLoaded.add(location.state);
        statesLoaded.add(Location.getAbbreviation(location.state));
        statesLoaded.add(Location.getStateName(location.state));
      } catch (IOException e) {
        System.err.println("ERROR: unable to load payers for state: " + location.state);
        e.printStackTrace();
      }
    }
  }

  /**
   * Read the payers from the given resource file, only importing the ones for the
   * given state.
   *
   * @param location the state being loaded
   * @param fileName Location of the file, relative to src/main/resources
   * @throws IOException if the file cannot be read
   */
  private static void loadPayers(Location location, String fileName) throws IOException {
    PayerManager.loadNoInsurance();

    String resource = Utilities.readResourceAndStripBOM(fileName);
    Iterator<? extends Map<String, String>> csv = SimpleCSV.parseLineByLine(resource);

    while (csv.hasNext()) {
      Map<String, String> row = csv.next();
      String payerStates = row.get(STATES_COVERED).toUpperCase();
      String abbreviation = Location.getAbbreviation(location.state).toUpperCase();

      // For now, only allow one U.S. state at a time.
      if (payerStates.contains(abbreviation) || payerStates.contains("*")) {

        Payer parsedPayer = csvLineToPayer(row);
        parsedPayer.setPayerAdjustment(buildPayerAdjustment());

        // Put the payer in their correct List/Map based on Government/Private.
        if (parsedPayer.isGovernmentPayer()) {
          // Government payers go in a map, allowing for easy retrieval of specific
          // government payers.
          PayerManager.governmentPayers.put(parsedPayer.getName(), parsedPayer);
        } else {
          // Private payers go in a list.
          PayerManager.privatePayers.add(parsedPayer);
        }
      }
    }

    PayerManager.loadPlans();
  }

  private static void loadPlans() {
    String fileName = Config.get("generate.payers.insurance_plans.default_file");
    Iterator<? extends Map<String, String>> csv = null;
    try {
      String resource = Utilities.readResourceAndStripBOM(fileName);
      csv = SimpleCSV.parseLineByLine(resource);
    } catch (IOException e) {
      e.printStackTrace();
    }

    while (csv.hasNext()) {
      Map<String, String> row = csv.next();
      csvLineToPlan(row);
    }
  }

  /**
   * Determines the algorithm to use for patients to find a Payer.
   */
  private static IPlanFinder buildPlanFinder() {
    IPlanFinder finder;
    String behavior = Config.get("generate.payers.selection_behavior").toLowerCase();
    switch (behavior) {
      case BEST_RATE:
        finder = new PlanFinderBestRates();
        break;
      case RANDOM:
        finder = new PlanFinderRandom();
        break;
      case PRIORITY:
        finder = new PlanFinderPriority();
        break;
      default:
        throw new RuntimeException("Not a valid Payer Selection Algorithm: " + behavior);
    }
    return finder;
  }

  private static IPayerAdjustment buildPayerAdjustment() {
    IPayerAdjustment adjustment;
    String behavior = Config.get("generate.payers.adjustment_behavior", "none").toLowerCase();
    String rateString = Config.get("generate.payers.adjustment_rate", "0.05");
    double rate = Double.parseDouble(rateString);
    switch (behavior) {
      case NONE:
        adjustment = new PayerAdjustmentNone();
        break;
      case FIXED:
        adjustment = new PayerAdjustmentFixed(rate);
        break;
      case RANDOM:
        adjustment = new PayerAdjustmentRandom(rate);
        break;
      default:
        adjustment = new PayerAdjustmentNone();
    }
    return adjustment;
  }

  /**
   * Given a line of parsed CSV input, convert the data into a Payer.
   *
   * @param line read a csv line to a payer's attributes
   * @return the new payer.
   */
  private static Payer csvLineToPayer(Map<String, String> line) {

    // Uses .remove() instead of .get() so we can iterate over remaining keys later.
    String payerName = line.remove(NAME).trim();
    String payerId = line.remove(ID).trim();
    Set<String> statesCovered = commaSeparatedStringToHashSet(line.remove(STATES_COVERED).trim());
    String ownership = line.remove(OWNERSHIP).trim();
    if (ownership.equalsIgnoreCase(GOV_OWNERSHIP)
        || ownership.equalsIgnoreCase(PRIVATE_OWNERSHIP)) {
      ownership = ownership.toUpperCase();
    } else {
      throw new RuntimeException("A Payer's ownership must be tagged as either "
          + GOV_OWNERSHIP + " or " + PRIVATE_OWNERSHIP + ". Payer " + payerName
          + " " + payerId + " has ownership of " + ownership + ".");
    }
    String priorityString = line.remove(PRIORITY_LEVEL).trim();
    int priority = Integer.MAX_VALUE;
    // A blank priority is minimum priority, so give it the maximum value.
    if (!StringUtils.isBlank(priorityString)) {
      priority = Integer.parseInt(priorityString);
    }

    Payer newPayer = new Payer(payerName, payerId, statesCovered, ownership, priority);

    // Add remaining columns we didn't map to first-class fields.
    for (Map.Entry<String, String> e : line.entrySet()) {
      newPayer.addAttribute(e.getKey(), e.getValue());
    }

    return newPayer;
  }

  /**
   * Converts a key-value CSV line to a plan.
   * @param line The Map with the CSV key-value pairs.
   */
  private static void csvLineToPlan(Map<String, String> line) {
    String payerId = line.remove(PAYER_ID).trim();
    Payer payer = PayerManager.getPayerById(payerId);
    if (payer == null) {
      // return without an error, because the given payer might
      // only exist in another state.
      return;
    }

    String planId = line.remove(PLAN_ID).trim();
    String planName = line.remove(NAME).trim();
    Set<String> servicesCovered
        = commaSeparatedStringToHashSet(line.remove(SERVICES_COVERED).trim());
    double deductible = Double.parseDouble(line.remove(DEDUCTIBLE).trim());
    double defaultCoinsurance = Double.parseDouble(line.remove(COINSURANCE).trim());
    double defaultCopay = Double.parseDouble(line.remove(COPAY).trim());
    double monthlyPremium = Double.parseDouble(line.remove(MONTHLY_PREMIUM).trim());
    boolean medicareSupplement = Boolean.parseBoolean(line.remove(MEDICARE_SUPPLEMENT).trim());
    int yearStart = Integer.parseInt(line.remove(START_YEAR).trim());
    String yearEndStr = line.remove(END_YEAR).trim();
    int yearEnd = Utilities.getYear(System.currentTimeMillis()) + 1;
    if (!StringUtils.isBlank(yearEndStr)) {
      yearEnd = Integer.parseInt(yearEndStr);
    }
    String eligibilityName = line.remove(ELIGIBILITY_POLICY);

    payer.createPlan(servicesCovered, deductible, defaultCoinsurance,
        defaultCopay, monthlyPremium, medicareSupplement, yearStart, yearEnd, eligibilityName);
  }

  private static Payer getPayerById(String payerId) {
    List<Payer> payerList = getAllPayers().stream().filter(payer ->
        payer.getPlanLinkId().equals(payerId)).collect(Collectors.toList());
    if (payerList.size() == 1) {
      return payerList.get(0);
    }
    if (payerList.size() > 1) {
      throw new RuntimeException(payerList.size()
          + " payers have id '" + payerId + "'. Ids should be unique.");
    }
    return null;
  }

  /**
   * Given a comma seperated string, convert the data into a Set.
   *
   * @param field the string to extract the Set from.
   * @return the HashSet of services covered.
   */
  private static Set<String> commaSeparatedStringToHashSet(String field) {
    String[] commaSeparatedField = field.split("\\s*,\\s*");
    List<String> parsedValues = Arrays.stream(commaSeparatedField).collect(Collectors.toList());
    return new HashSet<String>(parsedValues);
  }

  /**
   * Loads the noInsurance Payer.
   */
  public static void loadNoInsurance() {
    // noInsurance 'covers' all states.
    Set<String> statesCovered = new HashSet<String>();
    statesCovered.add("*");
    PayerManager.noInsurance = new Payer(NO_INSURANCE, "000000",
        statesCovered, NO_INSURANCE, Integer.MAX_VALUE);
    PayerManager.noInsurance.createPlan(new HashSet<String>(), 0.0, 0.0, 0.0, 0.0, false, 0,
        Utilities.getYear(System.currentTimeMillis()) + 1, PlanEligibilityFinder.GENERIC);
    PayerManager.noInsurance.setPayerAdjustment(new PayerAdjustmentNone());
  }

  /**
   * Returns the list of all loaded private payers.
   */
  public static Set<Payer> getPrivatePayers() {
    return PayerManager.privatePayers;
  }

  /**
   * Returns the List of all loaded government payers.
   */
  public static Set<Payer> getGovernmentPayers() {
    return PayerManager.governmentPayers.values().stream().collect(Collectors.toSet());
  }

  /**
   * Returns a List of all loaded payers.
   */
  public static Set<Payer> getAllPayers() {
    Set<Payer> allPayers = new HashSet<>();
    allPayers.addAll(PayerManager.getGovernmentPayers());
    allPayers.addAll(PayerManager.getPrivatePayers());
    return allPayers;
  }

  /**
   * Returns the government payer with the given name.
   *
   * @param governmentPayerName the government payer to get.
   * @return returns null if the government payer does not exist.
   */
  public static Payer getGovernmentPayer(String governmentPayerName) {
    return PayerManager.governmentPayers.get(governmentPayerName);
  }

  /**
   * Clear the list of loaded and cached Payers.
   * Currently only used in tests.
   */
  public static void clear() {
    governmentPayers.clear();
    privatePayers.clear();
    statesLoaded.clear();
    planFinder = buildPlanFinder();
  }

  /**
   * Returns a Payer that the person can qualify for.
   *
   * @param person  the person who needs to find insurance.
   * @param service the EncounterType the person would like covered.
   * @param time    the time that the person requires insurance.
   * @return a payer who the person can accept and vice versa.
   */
  public static InsurancePlan findPlan(Person person, EncounterType service, long time) {
    Set<InsurancePlan> plans = getActivePlans(getAllPayers(), time);
    // Remove medicare supplement plans from this check.
    plans = plans.stream().filter(plan -> !plan.isMedicareSupplementPlan())
        .collect(Collectors.toSet());
    InsurancePlan potentialPlan = planFinder.find(plans, person, service, time);
    if (potentialPlan.isGovernmentPlan()) {
      // Person will always choose a government plan.
      return potentialPlan;
    }
    // If the person cannot get a government plan, they will try to keep their existing insurance.
    InsurancePlan previousPlan = person.coverage
        .getPlanAtTime(time - Config.getAsLong("generate.timestep"));
    if (previousPlan != null && !previousPlan.isNoInsurance()
        && previousPlan.accepts(person, time) && previousPlan.isActive(time)
        && IPlanFinder.meetsAffordabilityRequirements(previousPlan, person, null, time)) {
      // People will keep their previous year's insurance if they can.
      return previousPlan;
    }
    return potentialPlan;
  }

  /**
   * Returns all active plans in the given payers based on the given time.
   * @param payers  The payers.
   * @param time  The time for the plan to be active in.
   * @return The set of active plans.
   */
  public static Set<InsurancePlan> getActivePlans(Set<Payer> payers, long time) {
    Set<InsurancePlan> plans = payers.stream().map(payer ->
        payer.getPlans()).flatMap(Set::stream).collect(Collectors.toSet());
    plans = plans.stream().filter(plan -> plan.isActive(time)).collect(Collectors.toSet());
    return plans;
  }

  /**
   * Returns the no insurance plan.
   * @return
   */
  public static InsurancePlan getNoInsurancePlan() {
    return noInsurance.getPlans().iterator().next();
  }

  /**
   * Finds an eligible medicare supplement plan for the given person.
   * @param person  The person for whom to find a medicare supplement plan.
   * @param service The service the plan should cover.
   * @param time  The time.
   * @return  A potential Medicare Supplement plan, if eligible and affordable.
   */
  public static InsurancePlan findMedicareSupplement(Person person,
      EncounterType service, long time) {
    Set<InsurancePlan> plans = getActivePlans(getAllPayers(), time);
    // Remove non-medicare supplement plans from this check.
    plans = plans.stream().filter(plan ->
        plan.isMedicareSupplementPlan()).collect(Collectors.toSet());
    InsurancePlan potentialPlan = planFinder
        .find(plans, person, service, time);
    return potentialPlan;
  }
}