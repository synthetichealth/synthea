package org.mitre.synthea.world.agents;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.gson.internal.LinkedTreeMap;

import java.io.IOException;
import java.util.ArrayList;
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
import org.mitre.synthea.world.agents.behaviors.IPayerFinder;
import org.mitre.synthea.world.agents.behaviors.PayerFinderBestRates;
import org.mitre.synthea.world.agents.behaviors.PayerFinderRandom;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.geography.Location;

public class Payer {

  // ArrayList of all private payers imported
  private static ArrayList<Payer> privatePayerList = new ArrayList<Payer>();
  // ArrayList of all government payers imported
  private static Map<String, Payer> governmentPayerMap = new HashMap<String,Payer>();

  // U.S. States loaded
  private static Set<String> statesLoaded = new HashSet<String>();

  private static IPayerFinder payerFinder = buildPayerFinder();
  // Provider Selection Behavior algorithm choices:
  private static final String RANDOM = "random";
  private static final String BESTRATE = "best_rate";

  /* Payer information */
  private Map<String, Object> attributes;
  public String uuid;
  private String id;
  private String name;
  private double defaultCopay;
  private double monthlyPremium;
  private double deductible;
  public String ownership;

  // The services that this payer covers. Currently unimplemented.
  // Will likely be moved to a Plans class.
  private List<EncounterType> servicesCovered;

  // The list of plans that this Payer has.
  // private List<Plan> plans

  /* Payer statistics. May be better to move to attributes. */
  private double costsCovered;
  private double revenue;
  /* Quality of Life Stats. [0]: Total QOLS, [1]: Total Years */
  private double[] qualityOfLifeStatistics;
  // row: year, column: type, value: count
  private Table<Integer, String, AtomicInteger> utilization;
  // Unique utilizers of Payer, by Person ID
  private HashMap<String, AtomicInteger> customerUtilization;

  // Static default NO_INSURANCE object
  public static Payer noInsurance;

  /**
   * Create a new Payer with no information.
   */
  public Payer() {
    uuid = UUID.randomUUID().toString();
    attributes = new LinkedTreeMap<>();
    utilization = HashBasedTable.create();
    customerUtilization = new HashMap<String, AtomicInteger>();
    costsCovered = 0.0;
    revenue = 0.0;
    monthlyPremium = 0.0;
    deductible = 0.0;
    defaultCopay = 0.0;
    qualityOfLifeStatistics = new double[] { 0.0, 0.0 };
  }

  /**
   * Load into cache the list of payers for a state.
   * 
   * @param location the state being loaded.
   */
  public static void loadPayers(Location location) {
    if (!statesLoaded.contains(location.state)
        || !statesLoaded.contains(Location.getAbbreviation(location.state))
        || !statesLoaded.contains(Location.getStateName(location.state))) {
      try {
        String insuranceCompanyFile
            = Config.get("generate.payers.insurance_companies.default_file");
        loadPayers(location, insuranceCompanyFile);

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
  public static void loadPayers(Location location, String fileName) throws IOException {

    // No Insurance object
    noInsurance = new Payer();
    noInsurance.name = "NO_INSURANCE";
    noInsurance.ownership = "NO_INSURANCE";
    noInsurance.uuid = "NO_INSURANCE";

    String resource = Utilities.readResource(fileName);
    Iterator<? extends Map<String, String>> csv = SimpleCSV.parseLineByLine(resource);

    while (csv.hasNext()) {
      Map<String, String> row = csv.next();
      String currState = row.get("state");
      String ownership = row.get("ownership");
      String abbreviation = Location.getAbbreviation(location.state);

      // for now, only allow one U.S. state at a time
      if (ownership.equalsIgnoreCase("government")
          || (location.state != null && location.state.equalsIgnoreCase(currState))
          || (abbreviation != null && abbreviation.equalsIgnoreCase(currState))) {

        Payer parsedPayer = csvLineToPayer(row);

        // add any remaining columns we didn't explicitly map to first-class fields
        // into the attributes map
        for (Map.Entry<String, String> e : row.entrySet()) {
          parsedPayer.attributes.put(e.getKey(), e.getValue());
        }

        // Put the payer in their correct List/Map.
        if (parsedPayer.ownership.equalsIgnoreCase("government")) {
          // Government payers go in a map, allowing for easy retrieval of specific payers.
          Payer.governmentPayerMap.put(parsedPayer.getName(), parsedPayer);
        } else {
          // Private payers go in a list.
          Payer.privatePayerList.add(parsedPayer);
        }

      }
    }
  }

  /**
   * Given a line of parsed CSV input, convert the data into a Payer.
   * 
   * @param line - read a csv line to a provider's attributes
   * @return the new payer.
   */
  private static Payer csvLineToPayer(Map<String, String> line) {
    Payer newPayer = new Payer();
    // Using .remove() instead of .get() so that we can iterate over the remaining
    // keys later
    newPayer.id = line.remove("id");
    newPayer.name = line.remove("name");
    if (newPayer.name == null || newPayer.name.isEmpty()) {
      newPayer.name = newPayer.id;
    }
    String base = newPayer.id + newPayer.name;
    newPayer.uuid = UUID.nameUUIDFromBytes(base.getBytes()).toString();
    newPayer.defaultCopay = Double.parseDouble(line.remove("default_copay"));
    newPayer.monthlyPremium = Double.parseDouble(line.remove("monthly_premium"));
    newPayer.ownership = line.remove("ownership");

    return newPayer;
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
    if (governmentPayer != null) {
      return Payer.governmentPayerMap.get(governmentPayerName);
    } else {
      throw new RuntimeException(
          "ERROR: Government Payer '" + governmentPayerName + "' does not exist.");
    }
  }

  /**
   * Clear the list of loaded and cached Payers.
   * Currently only used for testing.
   */
  public static void clear() {
    governmentPayerMap.clear();
    privatePayerList.clear();
    statesLoaded.clear();
  }

  /**
   * Determines the algorithm to use for patients to find a Payer.
   */
  private static IPayerFinder buildPayerFinder() {
    IPayerFinder finder = null;
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
   * Returns the selection algorithm for payers in this simulation.
   * 
   * @return the payer selection algorithm
   */
  public static IPayerFinder getPayerFinder() {
    return payerFinder;
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
   * Returns the ownserhip type of the payer (Government/Private).
   */
  public String getOwnership() {
    return this.ownership;
  }

  /**
   * Returns the Map of the payer's second class attributes.
   * ADDRESS,ZIP,CITY
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
   * Increments the encounters and encounterTypes the payer has covered. Changed service from
   * EncounterType to String to simplify for now. Would like to change back to
   * EncounterType later.
   * 
   * @param service the service type of the encounter
   * @param year the year of the encounter
   */
  public void incrementEncountersCovered(String service, int year) {
    increment(year, Provider.ENCOUNTERS);
    increment(year, Provider.ENCOUNTERS + "-" + service);
  }

  /**
   * Increments utiilization for a given year and service.
   */
  private synchronized void increment(Integer year, String key) {
    if (!utilization.contains(year, key)) {
      utilization.put(year, key, new AtomicInteger(0));
    }
    utilization.get(year, key).incrementAndGet();
  }

  /** 
   * Person chooses their insurance externally.
   * May need this for when a payer starts making decisions about who to insure.
   * May be useful for choosing different patients based on different policies
   * (pre-existing conditions, etc).
   * Every insurer will have a different set of guidelines for accepting
   * a customer, where should these be kept? In the payer/plans csv table?
   */
  /**
   * Will this payer accept the given person at the given time?.
   * 
   * @param person Person to consider
   * @param time   Time the person seeks care
   * @return whether or not the payer will accept this patient as a customer
   */
  public boolean accepts(Person person, long time) {
    // for now assume every payer accepts every patient
    // EXCEPT for medicaire & medicaid (Don't have all correct requirements)
    if (this.name.equals("Medicare") && person.ageInYears(time) < 65) {
      return false;
    } else if (this.name.equals("Medicaid") && !person.attributes.containsKey("blind")) {
      return false;
    } else if (this.name.equals("Dual Eligible") && !person.attributes.containsKey("blind")
        && person.ageInYears(time) < 65) {
      return false;
    }
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
   * Returns whether the payer covers the given encounter type. For now, insurance
   * will cover all services.
   * 
   * @param service the encounter type to check
   * @return whether the payer covers the given encounter type
   */
  public boolean coversService(EncounterType service) {
    return true;
  }

  /**
   * Increases the total costs incurred by the payer by the given amount.
   * 
   * @param costToPayer the cost of the current encounter, after the paid copay.
   */
  public void addCost(double costToPayer) {
    this.costsCovered += costToPayer;
  }

  /**
   * Returns the number of encounters this payer paid for.
   */
  public int getEncounterCount() {
    return utilization.column(Provider.ENCOUNTERS).values().stream().mapToInt(ai -> ai.get()).sum();
  }

  /**
   * Returns the number of Unique customers for this payer.
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
   * Returns the amount of money the payer paid.
   */
  public double getAmountPaid() {
    return this.costsCovered;
  }

  /**
   * Returns the total amount recieved from patients.
   */
  public double getRevenue() {
    return this.revenue;
  }

  /**
   * Determines the copay owed for this Payer based on the type of encounter.
   * May change from encounter to entry. Could give access to medications/procedure/etc.
   */
  public double determineCopay(Encounter encounter) {

    // TODO - Currently just returns a default copay. May add different copays for
    // each Encounter type.

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
  public void addQOLS(double qols) {
    // Add QOLS to QOLS Total.
    qualityOfLifeStatistics[0] += qols;
    // Increment the number of years covered.
    qualityOfLifeStatistics[1]++;
  }

  /**
   * Returns the average of the payer's QOLS of customers over the number of years covered.
   */
  public double getQOLAverage() {
    double qolsTotal = qualityOfLifeStatistics[0];
    double numYears = qualityOfLifeStatistics[1];
    return qolsTotal / numYears;
  }
}