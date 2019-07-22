package org.mitre.synthea.world.agents;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.gson.internal.LinkedTreeMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.world.agents.behaviors.IPayerFinder;
import org.mitre.synthea.world.agents.behaviors.PayerFinderBestRates;
import org.mitre.synthea.world.agents.behaviors.PayerFinderRandom;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.geography.Location;

public class Payer {

  /* ArrayList of all Private Payers imported */
  private static ArrayList<Payer> privatePayerList = new ArrayList<Payer>();
  /* Map of all Government Payers imported */
  private static Map<String, Payer> governmentPayerMap = new HashMap<String,Payer>();

  /* U.S. States loaded */
  private static Set<String> statesLoaded = new HashSet<String>();

  /* Payer Finder */
  private static IPayerFinder payerFinder;
  // Provider Selection Behavior algorithm choices:
  private static final String RANDOM = "random";
  private static final String BESTRATE = "best_rate";

  /* Payer Information */
  private final Map<String, Object> attributes;
  private final String name;
  private final String id;
  public final String uuid;
  private double defaultCopay;
  private double monthlyPremium;
  private double deductible;
  private String ownership;

  /* The States that this payer covers & operates in */
  private Set<String> statesCovered;

  /* The services that this payer covers */
  // Will likely be moved to a Plans class.
  // If an encounterType is contained in a payer's servicesCovered, then they cover it.
  private Set<String> servicesCovered;
  /* The list of plans that this Payer has. */
  // private List<Plan> plans

  /* Payer Statistics */
  private double costsCovered;
  private double costsUncovered;
  private double revenue;
  private double totalQOLS; // Total customer quality of life scores.
  // row: year, column: type, value: count
  public Table<Integer, String, AtomicInteger> encounterUtilization;
  // Unique utilizers of Payer, by Person ID
  public HashMap<String, AtomicInteger> customerUtilization;

  /* Default NO_INSURANCE object */
  public static Payer noInsurance;

  /**
   * Payer Constructor.
   */
  private Payer(String name, String id) {
    if (name == null || name.isEmpty()) {
      throw new RuntimeException("ERROR: Payer must have a non-null name.");
    }
    this.name = name;
    this.id = id;
    this.uuid = UUID.nameUUIDFromBytes((this.id + this.name).getBytes()).toString();
    this.attributes = new LinkedTreeMap<>();
    this.defaultCopay = 0.0;
    this.monthlyPremium = 0.0;
    this.deductible = 0.0;  // Currently, deductible is not set.
    this.ownership = "";
    this.encounterUtilization = HashBasedTable.create();
    this.customerUtilization = new HashMap<String, AtomicInteger>();
    this.costsCovered = 0.0;
    this.costsUncovered = 0.0;
    this.revenue = 0.0;
    this.totalQOLS = 0.0;
  }

  /**
   * Load into cache the list of payers for a state.
   * 
   * @param location the state being loaded.
   */
  public static void loadPayers(Location location) {

    // Build the Payer Finder
    payerFinder = buildPayerFinder();

    if (!statesLoaded.contains(location.state)
        || !statesLoaded.contains(Location.getAbbreviation(location.state))
        || !statesLoaded.contains(Location.getStateName(location.state))) {
      try {
        String payerFile
            = Config.get("generate.payers.insurance_companies.default_file");
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

    Payer.loadNoInsurance();

    String resource = Utilities.readResource(fileName);
    Iterator<? extends Map<String, String>> csv = SimpleCSV.parseLineByLine(resource);

    while (csv.hasNext()) {
      Map<String, String> row = csv.next();
      String payerStates = row.get("states_covered").toUpperCase();
      String abbreviation = Location.getAbbreviation(location.state).toUpperCase();

      // For now, only allow one U.S. state at a time.
      if (payerStates.contains(abbreviation) || payerStates.contains("*")) {

        Payer parsedPayer = csvLineToPayer(row);

        // Put the payer in their correct List/Map based on Government/Private.
        if (parsedPayer.ownership.equalsIgnoreCase("government")) {
          // Government payers go in a map, allowing for easy retrieval of specific gov payers.
          Payer.governmentPayerMap.put(parsedPayer.getName(), parsedPayer);
        } else {
          // Private payers go in a list.
          Payer.privatePayerList.add(parsedPayer);
        }
      }
    }
  }

  /**
   * Loads the noInsurance Payer.
   */
  public static void loadNoInsurance() {
    noInsurance = new Payer("NO_INSURANCE", "000000");
    noInsurance.ownership = "NO_INSURANCE";
    // noInsurance does not 'cover' any services.
    noInsurance.servicesCovered = new HashSet<String>();
    // noInsurance 'covers' all states.
    noInsurance.statesCovered = new HashSet<String>();
    noInsurance.statesCovered.add("*");
  }

  /**
   * Given a line of parsed CSV input, convert the data into a Payer.
   * 
   * @param line read a csv line to a payer's attributes
   * @return the new payer.
   */
  private static Payer csvLineToPayer(Map<String, String> line) {

    // Uses .remove() instead of .get() so we can iterate over the remaining keys later.
    Payer newPayer = new Payer(line.remove("name"), line.remove("id"));
    newPayer.statesCovered = commaSeparatedStringToHashSet(line.remove("states_covered"));
    newPayer.servicesCovered = commaSeparatedStringToHashSet(line.remove("services_covered"));
    newPayer.defaultCopay = Double.parseDouble(line.remove("default_copay"));
    newPayer.monthlyPremium = Double.parseDouble(line.remove("monthly_premium"));
    newPayer.ownership = line.remove("ownership");
    // Add remaining columns we didn't map to first-class fields to attributes map.
    for (Map.Entry<String, String> e : line.entrySet()) {
      newPayer.attributes.put(e.getKey(), e.getValue());
    }
    return newPayer;
  }

  /**
   * Given a Comma Seperated String, convert the data into a Set.
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
   * Returns the list of all loaded private payers.
   */
  public static List<Payer> getPrivatePayers() {
    return Payer.privatePayerList;
  }

  /**
   * Returns the List of all loaded government payers.
   */
  public static List<Payer> getGovernmentPayers() {
    return Payer.governmentPayerMap.values().stream().collect(Collectors.toList());
  }

  /**
   * Returns the List of all loaded payers.
   * TODO - This is inefficient.
   * Creates a whole new list with a duplicate set of pointers to each Payer.
   * Gotta figure out a better way than this.
   */
  public static List<Payer> getAllPayers() {
    List<Payer> allPayers = new ArrayList<>();
    allPayers.addAll(Payer.getGovernmentPayers());
    allPayers.addAll(Payer.getPrivatePayers());
    return allPayers;
  }

  /**
   * Returns the government payer with the given name.
   * 
   * @param governmentPayerName the government payer to get.
   */
  public static Payer getGovernmentPayer(String governmentPayerName) {
    Payer governmentPayer = Payer.governmentPayerMap.get(governmentPayerName);
    if (governmentPayer == null) {
      throw new RuntimeException(
          "ERROR: Government Payer '" + governmentPayerName + "' does not exist.");
    }
    return Payer.governmentPayerMap.get(governmentPayerName);
  }

  /**
   * Clear the list of loaded and cached Payers.
   * Currently only used in tests.
   */
  public static void clear() {
    governmentPayerMap.clear();
    privatePayerList.clear();
    statesLoaded.clear();
    payerFinder = buildPayerFinder();
  }

  /**
   * Determines the algorithm to use for patients to find a Payer.
   */
  private static IPayerFinder buildPayerFinder() {
    IPayerFinder finder;
    String behavior = Config.get("generate.payers.selection_behavior").toLowerCase();
    switch (behavior) {
      case BESTRATE:
        finder = new PayerFinderBestRates();
        break;
      case RANDOM:
        finder = new PayerFinderRandom();
        break;
      default:
        throw new RuntimeException("Not a valid Payer Selction Algorithm: " + behavior);
    }
    return finder;
  }

  /**
   * Returns a Payer that the person can qualify for.
   * 
   * @param person the person who needs to find insurance.
   * @param service the EncounterType the person would like covered.
   * @param time the time that the person requires insurance.
   * @return a payer who the person can accept and vice versa.
   */
  public static Payer findPayer(Person person, EncounterType service, long time) {
    return Payer.payerFinder.find(Payer.getPrivatePayers(), person, service, time);
  }

  /**
   * Returns the payer's unique ID.
   */
  public String getResourceID() {
    return uuid;
  }

  /**
   * Returns the name of the payer.
   */
  public String getName() {
    return this.name;
  }

  /**
   * Returns the monthly premium of the payer.
   */
  public double getMonthlyPremium() {
    return this.monthlyPremium;
  }

  /**
   * Returns the yearly deductible of this payer.
   */
  public double getDeductible() {
    return this.deductible;
  }

  /**
   * Returns the ownserhip type of the payer (Government/Private).
   */
  public String getOwnership() {
    return this.ownership;
  }

  /**
   * Returns the Map of the payer's second class attributes.
   * Currently: ADDRESS,ZIP,CITY.
   */
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  /**
   * Increments the number of unique users.
   * 
   * @param person the person to add to the payer.
   */
  public void incrementCustomers(Person person) {
    if (!customerUtilization.containsKey(person.attributes.get(Person.ID))) {
      customerUtilization.put((String) person.attributes.get(Person.ID), new AtomicInteger(0));
    }
    customerUtilization.get(person.attributes.get(Person.ID)).incrementAndGet();
  }

  /**
   * Increments the entries covered by this payer.
   * 
   * @param entry the entry covered.
   */
  public void incrementEntriesCovered(Entry entry) {

    String entryType;

    if (entry instanceof Encounter) {
      entryType = Provider.ENCOUNTERS;
    } else if (entry instanceof Medication) { 
      entryType = "medications";
    } else {
      throw new RuntimeException("Attempted to inrement Payer entries with invalid type "
          + entry.getClass());
    }
    incrementEncounters(Utilities.getYear(entry.start), "covered-" + entryType);
    incrementEncounters(Utilities.getYear(entry.start), "covered-" + entryType + "-" + entry.type);
  }
  
  /**
   * Increments the entries covered by this payer.
   * 
   * @param entry the entry covered.
   */
  public void incrementEntriesNotCovered(Entry entry) {

    String entryType;

    if (entry instanceof Encounter) {
      entryType = Provider.ENCOUNTERS;
    } else if (entry instanceof Medication) {
      entryType = "medications";
    } else {
      throw new RuntimeException("Attempted to inrement Payer entries with invalid type "
          + entry.getClass());
    }
    incrementEncounters(Utilities.getYear(entry.start), "uncovered-" + entryType);
    incrementEncounters(Utilities.getYear(entry.start), "uncovered-" + entryType
        + "-" + entry.type);
  }

  /**
   * Increments encounter utiilization for a given year and encounter type.
   * 
   * @param year the year of the encounter to add
   * @param key the key (the encounter type and whether it was covered/uncovered)
   */
  private synchronized void incrementEncounters(Integer year, String key) {
    if (!encounterUtilization.contains(year, key)) {
      encounterUtilization.put(year, key, new AtomicInteger(0));
    }
    encounterUtilization.get(year, key).incrementAndGet();
  }

  /**
   * Will this payer accept the given person at the given time?.
   * 
   * @param person Person to consider
   * @param time   Time the person seeks care
   * @return whether or not the payer will accept this patient as a customer
   */
  public boolean accepts(Person person, long time) {

    // For now, assume that all payers accept all patients.
    // EXCEPT Medicare/Medicaid.
    if (this.getOwnership().equals("Government")) {

      if (this.name.equals("Medicare")) {
        // Return whether the person satisfies the conditions for Medicare acceptance.
        int age = person.ageInYears(time);
        boolean esrd = (person.attributes.containsKey("end_stage_renal_disease")
            && (boolean) person.attributes.get("end_stage_renal_disease"));
        boolean sixtyFive = (age >= 65);

        boolean medicare = sixtyFive || esrd;
        return medicare;

      } else if (this.name.equals("Medicaid")) {
        // Return whether the person satisfies the conditions for Medicaid acceptance.
        boolean female = (person.attributes.get(Person.GENDER).equals("F"));
        boolean pregnant = (person.attributes.containsKey("pregnant")
            && (boolean) person.attributes.get("pregnant"));
        boolean blind = (person.attributes.containsKey("blindness")
            && (boolean) person.attributes.get("blindness"));
        int income = (Integer) person.attributes.get(Person.INCOME);
        boolean medicaidIncomeEligible = (income <= HealthInsuranceModule.medicaidLevel);

        boolean medicaid = (female && pregnant) || blind || medicaidIncomeEligible;
        return medicaid;
      }
    }
    // The payer is not Medicare/Medicaid so they will accept any and every person. For now.
    return true;
  }

  /**
   * Is the given Provider in this Payer's network?.
   * Currently just returns true until Networks are implemented.
   * 
   * @param provider Provider to consider
   * @return whether or not the provider is in the payer network
   */
  public boolean isInNetwork(Provider provider) {
    return true;
  }

  /**
   * Returns whether the payer covers the given service.
   * 
   * @param service the entry type to check
   * @return whether the payer covers the given service
   */
  public boolean coversService(String service) {
    return service == null
        || this.servicesCovered.contains(service)
        || this.servicesCovered.contains("*");
  }

  /**
   * Increases the total costs incurred by the payer by the given amount.
   * 
   * @param costToPayer the cost of the current encounter, after the patient's copay.
   */
  public void addCost(double costToPayer) {
    this.costsCovered += costToPayer;
  }

  /**
   * Increases the costs the payer did not cover by the given amount.
   * 
   * @param costToPatient the costs that the payer did not cover.
   */
  public void addUncoveredCost(double costToPatient) {
    this.costsUncovered += costToPatient;
  }

  /**
   * Returns the number of encounters this payer paid for.
   */
  public int getEncountersCoveredCount() {
    return encounterUtilization.column("covered-"
        + Provider.ENCOUNTERS).values().stream().mapToInt(ai -> ai.get()).sum();
  }

  /**
   * Returns the number of encounters this payer did not cover for their customers.
   */
  public int getEncountersUncoveredCount() {
    return encounterUtilization.column("uncovered-"
        + Provider.ENCOUNTERS).values().stream().mapToInt(ai -> ai.get()).sum();
  }

  /**
   * Returns the number of Unique customers of this payer.
   */
  public int getUniqueCustomers() {
    return customerUtilization.size();
  }

  /**
   * Returns the number of years the given customer was with this Payer.
   */
  public int getCustomerUtilization(Person person) {
    return customerUtilization.get(person.attributes.get(Person.ID)).get();
  }

  /**
   * Returns the amount of money the payer paid to providers.
   */
  public double getAmountCovered() {
    return this.costsCovered;
  }

  /**
   * Returns the amount of money the payer did not cover.
   */
  public double getAmountUncovered() {
    return this.costsUncovered;
  }

  /**
   * Returns the total amount of money recieved from patients.
   */
  public double getRevenue() {
    return this.revenue;
  }

  /**
   * Returns the number of member years covered by this payer.
   */
  public int getNumYearsCovered() {
    return this.customerUtilization.values().stream().mapToInt(AtomicInteger::intValue).sum();
  }

  /**
   * Determines the copay owed for this Payer based on the type of encounter.
   * May change from encounter to entry to get access to medications/procedure/etc.
   */
  public double determineCopay(Encounter encounter) {

    // TODO - Currently just returns a default copay. May add different copays for
    // each Encounter type (AMB/EMERGENCY/MEDICATION/etc).

    double copay = this.defaultCopay;
    /*
    // Encounter inpatient
    if (encounter.type.equalsIgnoreCase("inpatient")) {
      //copay = inpatientCopay;
    } else {
      // Outpatient Encounter, Encounter for 'checkup', Encounter for symptom,
      copay = outpatientCopay
    }
    */
    return copay;
  }

  /**
   * Pays the given premium to the Payer, increasing their revenue.
   * 
   * @param monthlyPremium the monthly premium to be paid, in dollars.
   */
  public void payPremium(double monthlyPremium) {
    this.revenue += monthlyPremium;
  }

  /**
   * Adds the Quality of Life Score (QOLS) of a patient of the current (past?)
   * year. Increments the total number of years covered (for averaging out
   * purposes).
   * 
   * @param qols the Quality of Life Score to be added.
   */
  public void addQols(double qols) {
    totalQOLS += qols;
  }

  /**
   * Returns the average of the payer's QOLS of customers over the number of years covered.
   */
  public double getQOLAverage() {
    double numYears = this.getNumYearsCovered();
    return this.totalQOLS / numYears;
  }
}