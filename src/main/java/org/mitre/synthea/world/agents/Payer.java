package org.mitre.synthea.world.agents;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.gson.internal.LinkedTreeMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.behaviors.IPayerFinder;
import org.mitre.synthea.world.agents.behaviors.PayerFinderBestRates;
import org.mitre.synthea.world.agents.behaviors.PayerFinderRandom;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.geography.Location;

public class Payer {

  // ArrayList of all payers imported
  private static ArrayList<Payer> payerList = new ArrayList<Payer>();
  // ArrayList of all government payers imported (Maybe?)
  // private static ArrayList<Payer> governmentPayerList = new ArrayList<Payer>();

  // U.S. States loaded
  private static Set<String> statesLoaded = new HashSet<String>();
  // Number of payers loaded (?) (can't we just use payerlist.size?)
  private static int loaded = 0;

  private static IPayerFinder payerFinder = buildPayerFinder();
  // Provider Selection Behavior algorithm choices:
  private static final String RANDOM = "random";
  private static final String BESTRATE = "best_rate";

  // Payer information
  private Map<String, Object> attributes;
  public String uuid;
  private String id;
  private String name;
  private double defaultCopay;
  private double monthlyPremium;
  private double deductible;
  public String ownership;
  // public ArrayList<EncounterType> servicesCovered;
  // row: year, column: type, value: count
  private Table<Integer, String, AtomicInteger> utilization;

  // Payer statistics
  private double costsCovered;
  private double revenue;
  private int encounterCount;

  // Static default NO_INSURANCE object
  public static Payer noInsurance;

  /**
   * Create a new Payer with no information.
   */
  public Payer() {
    uuid = UUID.randomUUID().toString();
    attributes = new LinkedTreeMap<>();
    utilization = HashBasedTable.create();
    costsCovered = 0.0;
    revenue = 0.0;
    monthlyPremium = 0.0;
    deductible = 0.0;
    defaultCopay = 0.0;
    encounterCount = 0;
  }

  /**
   * Determines the algorithm to use to find a Payer.
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
        throw new RuntimeException("Not a valid Payer Selction Algorithm");
    }
    return finder;
  }

  /**
   * Returns the payer's unique ID.
   */
  public String getResourceID() {
    return uuid;
  }

  /**
   * Returns the map of payer's second class attributes.
   */
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  /**
   * Increments the encounters the payer has covered.
   */
  public void incrementEncountersCovered(EncounterType service, int year) {
    increment(year, "ENCOUNTERS");
  }

  /**
   * Increments utiilization. TODO - need some guidance with what this does.
   * Currently called by incrementEncountersCovered which is never referenced.
   * Copied from Providers.java.
   */
  private synchronized void increment(Integer year, String key) {
    if (!utilization.contains(year, key)) {
      utilization.put(year, key, new AtomicInteger(0));
    }
    utilization.get(year, key).incrementAndGet();
  }

  public Table<Integer, String, AtomicInteger> getUtilization() {
    return utilization;
  }

  // Person will choose their insurance externally.
  // May need this for when a payer starts making decisions about who to insure.
  // May be useful for choosing different patients based on different policies
  // (pre-existing conditions, etc).
  // Every insurer will have a different set of guidelines for accepting
  // amcustomer, where to keep these? In the table? How?
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
   * 
   * @param provider Provider to consider
   * @return whether or not the provider is in the payer network
   */
  public boolean inNetwork(Provider provider) {
    return true;
  }

  /**
   * Is the given Provider in this Payer's network?.
   * 
   * @return the payer selection algorithm
   */
  public static IPayerFinder getPayerFinder() {
    return payerFinder;
  }

  /**
   * Clear the list of loaded and cached payers.
   */
  public static void clear() {
    payerList.clear();
    // governmentPayerList.clear();
    statesLoaded.clear();
    loaded = 0;
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
        String insuranceCompanyFile =
            Config.get("generate.payers.insurance_companies.default_file");
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
    noInsurance.uuid = null;

    String resource = Utilities.readResource(fileName);
    Iterator<? extends Map<String, String>> csv = SimpleCSV.parseLineByLine(resource);

    while (csv.hasNext()) {
      Map<String, String> row = csv.next();
      String currState = row.get("state");
      String ownership = row.get("ownership");
      String abbreviation = Location.getAbbreviation(location.state);

      // for now, only allow one U.S. state at a time
      // NOTE: If there is no given state, then it is Medicaid/Medicare/DualEligible
      if ((location.state == null) || ownership.equalsIgnoreCase("government")
          || (location.state != null && location.state.equalsIgnoreCase(currState))
          || (abbreviation != null && abbreviation.equalsIgnoreCase(currState))) {

        Payer parsedPayer = csvLineToPayer(row);

        // add any remaining columns we didn't explicitly map to first-class fields
        // into the attributes table
        for (Map.Entry<String, String> e : row.entrySet()) {
          parsedPayer.attributes.put(e.getKey(), e.getValue());
        }

        Payer.payerList.add(parsedPayer);
        loaded++;
      }
    }
  }

  /**
   * Given a line of parsed CSV input, convert the data into a Payer.
   * 
   * @param line - read a csv line to a provider's attributes
   * @return A payer.
   */
  private static Payer csvLineToPayer(Map<String, String> line) {
    Payer newPayer = new Payer();
    // using remove instead of get here so that we can iterate over the remaining
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
   * Returns the list of all loaded payers.
   */
  public static List<Payer> getPayerList() {
    return payerList;
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
   * Returns the Payer's default copay.
   */
  public double getCopay() {
    return this.defaultCopay;
  }

  /**
   * Increases the total costs incurred by the payer by the given amount.
   * 
   * @param costToPayer the total cost of the current encounter
   */
  public void addCost(double costToPayer) {
    this.costsCovered += costToPayer;
  }

  /**
   * Increases the total revenue earned by the payer by the given amount.
   * 
   * @param patientPayment the amount paid by a patient to the payer
   */
  public void addRevenue(double patientPayment) {
    this.revenue += patientPayment;
  }

  /**
   * Returns the number of encounters this payer paid for.
   */
  public int getEncounterCount() {
    return this.encounterCount;
  }

  /**
   * Returns the name of the payer.
   */
  public String getName() {
    return this.name;
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
   * Increments the number of encounters the payer has covered.
   */
  public void incrementEncountersPaid() {
    this.encounterCount++;
  }

  /**
   * Returns the ownserhip type of the payer (Government/Private).
   */
  public String getOwnership() {
    return this.ownership;
  }
}