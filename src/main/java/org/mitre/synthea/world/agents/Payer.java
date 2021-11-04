package org.mitre.synthea.world.agents;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.gson.internal.LinkedTreeMap;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.world.agents.behaviors.IPayerAdjustment;
import org.mitre.synthea.world.agents.behaviors.IPayerFinder;
import org.mitre.synthea.world.agents.behaviors.PayerAdjustmentFixed;
import org.mitre.synthea.world.agents.behaviors.PayerAdjustmentNone;
import org.mitre.synthea.world.agents.behaviors.PayerAdjustmentRandom;
import org.mitre.synthea.world.agents.behaviors.PayerFinderBestRates;
import org.mitre.synthea.world.agents.behaviors.PayerFinderRandom;
import org.mitre.synthea.world.concepts.Claim.ClaimEntry;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Immunization;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;
import org.mitre.synthea.world.geography.Location;

public class Payer implements Serializable {

  /* ArrayList of all Private Payers imported. */
  private static List<Payer> privatePayers = new ArrayList<Payer>();
  /* Map of all Government Payers imported. */
  private static Map<String, Payer> governmentPayers = new HashMap<String,Payer>();
  /* No Insurance Payer. */
  public static Payer noInsurance;

  /* U.S. States loaded. */
  private static Set<String> statesLoaded = new HashSet<String>();

  /* Payer Finder. */
  private static IPayerFinder payerFinder;
  // Payer selection algorithm choices:
  private static final String RANDOM = "random";
  private static final String BESTRATE = "best_rate";

  /* Payer Adjustment strategy. */
  private IPayerAdjustment payerAdjustment;
  // Payer adjustment algorithm choices:
  private static final String NONE = "none";
  private static final String FIXED = "fixed";

  /* Payer Attributes. */
  private final Map<String, Object> attributes;
  private final String name;
  public final String uuid;
  private double deductible;
  private double defaultCopay;
  private double defaultCoinsurance;
  private double monthlyPremium;
  private String ownership;
  // The States that this payer covers & operates in.
  private Set<String> statesCovered;
  // The services that this payer covers. May be moved to a potential plans class.
  private Set<String> servicesCovered;

  /* Payer Statistics. */
  private double revenue;
  private double costsCovered;
  private double costsUncovered;
  private double totalQOLS; // Total customer quality of life scores.
  // Unique utilizers of Payer, by Person ID, with number of utilizations per Person.
  private final Map<String, AtomicInteger> customerUtilization;
  // row: year, column: type, value: count.
  private transient Table<Integer, String, AtomicInteger> entryUtilization;
  
  /**
   * Simple bean used to add Java Serialization support to 
   * com.google.common.collect.Table&lt;Integer, String, AtomicInteger&gt; which doesn't natively
   * support Serialization.
   */
  static class UtilizationBean implements Serializable {
    public Integer year;
    public String type;
    public AtomicInteger count;
    
    public UtilizationBean(Integer year, String type, AtomicInteger count) {
      this.year = year;
      this.type = type;
      this.count = count;
    }
  }
  
  /**
   * Java Serialization support for the entryUtilization field.
   * @param oos stream to write to
   */
  private void writeObject(ObjectOutputStream oos) throws IOException {
    oos.defaultWriteObject();
    ArrayList<UtilizationBean> entryUtilizationElements = null;
    if (entryUtilization != null) {
      entryUtilizationElements = new ArrayList<>(entryUtilization.size());
      for (Table.Cell<Integer, String, AtomicInteger> cell: entryUtilization.cellSet()) {
        entryUtilizationElements.add(
                new UtilizationBean(cell.getRowKey(), cell.getColumnKey(), cell.getValue()));
      }
    }
    oos.writeObject(entryUtilizationElements);
  }

  /**
   * Java Serialization support for the entryUtilization field.
   * @param ois stream to read from
   */
  private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
    ois.defaultReadObject();
    ArrayList<UtilizationBean> entryUtilizationElements = 
            (ArrayList<UtilizationBean>)ois.readObject();
    if (entryUtilizationElements != null) {
      this.entryUtilization = HashBasedTable.create();
      for (UtilizationBean u: entryUtilizationElements) {
        this.entryUtilization.put(u.year, u.type, u.count);
      }
    }
  }

  /**
   * Payer Constructor.
   */
  private Payer(String name, String id) {
    if (name == null || name.isEmpty()) {
      throw new RuntimeException("ERROR: Payer must have a non-null name.");
    }
    this.name = name;
    this.uuid = UUID.nameUUIDFromBytes((id + this.name).getBytes()).toString();
    this.attributes = new LinkedTreeMap<>();
    this.entryUtilization = HashBasedTable.create();
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
        parsedPayer.payerAdjustment = buildPayerAdjustment();

        // Put the payer in their correct List/Map based on Government/Private.
        if (parsedPayer.ownership.equalsIgnoreCase("government")) {
          // Government payers go in a map, allowing for easy retrieval of specific gov payers.
          Payer.governmentPayers.put(parsedPayer.getName(), parsedPayer);
        } else {
          // Private payers go in a list.
          Payer.privatePayers.add(parsedPayer);
        }
      }
    }
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
    newPayer.deductible = Double.parseDouble(line.remove("deductible"));
    newPayer.defaultCoinsurance = Double.parseDouble(line.remove("default_coinsurance"));
    newPayer.defaultCopay = Double.parseDouble(line.remove("default_copay"));
    newPayer.monthlyPremium = Double.parseDouble(line.remove("monthly_premium"));
    newPayer.ownership = line.remove("ownership");
    // Add remaining columns we didn't map to first-class fields to payer's attributes map.
    for (Map.Entry<String, String> e : line.entrySet()) {
      newPayer.attributes.put(e.getKey(), e.getValue());
    }
    return newPayer;
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
    noInsurance = new Payer("NO_INSURANCE", "000000");
    noInsurance.ownership = "NO_INSURANCE";
    noInsurance.deductible = 0.0;
    noInsurance.defaultCoinsurance = 0.0;
    noInsurance.defaultCopay = 0.0;
    noInsurance.monthlyPremium = 0.0;
    noInsurance.payerAdjustment = new PayerAdjustmentNone();
    // noInsurance does not cover any services.
    noInsurance.servicesCovered = new HashSet<String>();
    // noInsurance 'covers' all states.
    noInsurance.statesCovered = new HashSet<String>();
    noInsurance.statesCovered.add("*");
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
   * Returns the list of all loaded private payers.
   */
  public static List<Payer> getPrivatePayers() {
    return Payer.privatePayers;
  }

  /**
   * Returns the List of all loaded government payers.
   */
  public static List<Payer> getGovernmentPayers() {
    return Payer.governmentPayers.values().stream().collect(Collectors.toList());
  }

  /**
   * Returns a List of all loaded payers.
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
   * @return returns null if the government payer does not exist.
   */
  public static Payer getGovernmentPayer(String governmentPayerName) {
    return Payer.governmentPayers.get(governmentPayerName);
  }

  /**
   * Clear the list of loaded and cached Payers.
   * Currently only used in tests.
   */
  public static void clear() {
    governmentPayers.clear();
    privatePayers.clear();
    statesLoaded.clear();
    payerFinder = buildPayerFinder();
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
   * Returns the Coinsurance of this payer.
   */
  public double getCoinsurance() {
    return this.defaultCoinsurance;
  }

  /**
   * Returns the ownserhip type of the payer (Government/Private).
   */
  public String getOwnership() {
    return this.ownership;
  }

  /**
   * Returns the Map of the payer's second class attributes.
   */
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  /**
   * Returns whether a payer will accept the given patient at this time. Currently returns
   * true by default, except for Medicare/Medicaid which have hardcoded requirements.
   * 
   * @param person Person to consider
   * @param time   Time the person seeks care
   * @return whether or not the payer will accept this patient as a customer
   */
  public boolean accepts(Person person, long time) {

    // For now, assume that all payers accept all patients EXCEPT Medicare/Medicaid.
    if (this.name.equals("Medicare")) {
      boolean esrd = (person.attributes.containsKey("end_stage_renal_disease")
          && (boolean) person.attributes.get("end_stage_renal_disease"));
      boolean sixtyFive = (person.ageInYears(time) >= 65);

      boolean medicareEligible = sixtyFive || esrd;
      return medicareEligible;

    } else if (this.name.equals("Medicaid")) {
      boolean female = (person.attributes.get(Person.GENDER).equals("F"));
      boolean pregnant = (person.attributes.containsKey("pregnant")
          && (boolean) person.attributes.get("pregnant"));
      boolean blind = (person.attributes.containsKey("blindness")
          && (boolean) person.attributes.get("blindness"));
      int income = (Integer) person.attributes.get(Person.INCOME);
      boolean medicaidIncomeEligible = (income <= HealthInsuranceModule.medicaidLevel);

      boolean medicaidEligible = (female && pregnant) || blind || medicaidIncomeEligible;
      return medicaidEligible;
    }
    // The payer is not Medicare/Medicaid so they will accept any and every person. For now.
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
   * Returns whether or not this payer will cover the given entry.
   * 
   * @param entry the entry that needs covering.
   */
  public boolean coversCare(Entry entry) {
    // Payer.isInNetwork() always returns true. For Now.
    return this.coversService(entry.type)
        && this.isInNetwork(null);
    // Entry doesn't have a provider but encounter does, need to find a way to get provider.
  }

  /**
   * Determines the copay owed for this Payer based on the type of entry.
   * For now, this returns a default copay. But in the future there will be different
   * copays depending on the encounter type covered. If the entry is a wellness visit
   * and the time is after the mandate year, then the copay is $0.00.
   * 
   * @param entry the entry to calculate the copay for.
   */
  public double determineCopay(Entry entry) {
    double copay = this.defaultCopay;
    if (entry.type.equalsIgnoreCase(EncounterType.WELLNESS.toString())
        && entry.start > HealthInsuranceModule.mandateTime) {
      copay = 0.0;
    }
    return copay;
  }

  /**
   * Determines whether or not this payer will adjust this claim, and by how
   * much. This determination is based on the claim adjustment strategy configuration,
   * which defaults to none.
   * @param claimEntry The claim entry to be adjusted.
   * @param person The person making the claim.
   * @return The dollar amount the claim entry was adjusted.
   */
  public double adjustClaim(ClaimEntry claimEntry, Person person) {
    return payerAdjustment.adjustClaim(claimEntry, person);
  }

  /**
   * Pays the given premium to the Payer, increasing their revenue.
   * 
   * @return the monthly premium amount.
   */
  public double payMonthlyPremium() {
    this.revenue += this.monthlyPremium;
    return this.monthlyPremium;
  }

  /**
   * Increments the number of unique users.
   * 
   * @param person the person to add to the payer.
   */
  public synchronized void incrementCustomers(Person person) {
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
  public void incrementCoveredEntries(Entry entry) {

    String entryType = getEntryType(entry);

    incrementEntries(Utilities.getYear(entry.start), "covered-" + entryType);
    incrementEntries(Utilities.getYear(entry.start), "covered-" + entryType + "-" + entry.type);
  }
  
  /**
   * Increments the entries not covered by this payer.
   * 
   * @param entry the entry covered.
   */
  public void incrementUncoveredEntries(Entry entry) {

    String entryType = getEntryType(entry);

    incrementEntries(Utilities.getYear(entry.start), "uncovered-" + entryType);
    incrementEntries(Utilities.getYear(entry.start), "uncovered-" + entryType
        + "-" + entry.type);
  }

  // Perhaps move to HealthRecord.java
  /**
   * Determines what entry type (Immunization/Encounter/Procedure/Medication) of the given entry.
   * 
   * @param entry the entry to parse.
   */
  private String getEntryType(Entry entry) {

    String entryType;

    if (entry instanceof Encounter) {
      entryType = HealthRecord.ENCOUNTERS;
    } else if (entry instanceof Medication) {
      entryType = HealthRecord.MEDICATIONS;
    } else if (entry instanceof Procedure) {
      entryType = HealthRecord.PROCEDURES;
    } else if (entry instanceof Immunization) {
      entryType = HealthRecord.IMMUNIZATIONS;
    } else {
      // Not an entry with a cost.
      entryType = "no_cost";
    }
    return entryType;
  }

  /**
   * Increments encounter utilization for a given year and encounter type.
   * 
   * @param year the year of the encounter to add
   * @param key the key (the encounter type and whether it was covered/uncovered)
   */
  private synchronized void incrementEntries(Integer year, String key) {
    if (!entryUtilization.contains(year, key)) {
      entryUtilization.put(year, key, new AtomicInteger(0));
    }
    entryUtilization.get(year, key).incrementAndGet();
  }

  /**
   * Increases the total costs incurred by the payer by the given amount.
   * 
   * @param costToPayer the cost of the current encounter, after the patient's copay.
   */
  public void addCoveredCost(double costToPayer) {
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
   * Adds the Quality of Life Score (QOLS) of a patient of the current (past?)
   * year. Increments the total number of years covered (for averaging out
   * purposes).
   * 
   * @param qols the Quality of Life Score to be added.
   */
  public void addQols(double qols) {
    this.totalQOLS += qols;
  }

  /**
   * Returns the total amount of money recieved from patients.
   * Consists of monthly premium payments.
   */
  public double getRevenue() {
    return this.revenue;
  }

  /**
   * Returns the number of years the given customer was with this Payer.
   */
  public int getCustomerUtilization(Person person) {
    return customerUtilization.get(person.attributes.get(Person.ID)).get();
  }

  /**
   * Returns the total number of unique customers of this payer.
   */
  public int getUniqueCustomers() {
    return customerUtilization.size();
  }

  /**
   * Returns the total number of member years covered by this payer.
   */
  public int getNumYearsCovered() {
    return this.customerUtilization.values().stream().mapToInt(AtomicInteger::intValue).sum();
  }

  /**
   * Returns the number of encounters this payer paid for.
   */
  public int getEncountersCoveredCount() {
    return entryUtilization.column("covered-"
        + HealthRecord.ENCOUNTERS).values().stream().mapToInt(ai -> ai.get()).sum();
  }

  /**
   * Returns the number of encounters this payer did not cover for their customers.
   */
  public int getEncountersUncoveredCount() {
    return entryUtilization.column("uncovered-"
        + HealthRecord.ENCOUNTERS).values().stream().mapToInt(ai -> ai.get()).sum();
  }

  /**
   * Returns the number of medications this payer paid for.
   */
  public int getMedicationsCoveredCount() {
    return entryUtilization.column("covered-"
        + HealthRecord.MEDICATIONS).values().stream().mapToInt(ai -> ai.get()).sum();
  }

  /**
   * Returns the number of medications this payer did not cover for their customers.
   */
  public int getMedicationsUncoveredCount() {
    return entryUtilization.column("uncovered-"
        + HealthRecord.MEDICATIONS).values().stream().mapToInt(ai -> ai.get()).sum();
  }

  /**
   * Returns the number of procedures this payer paid for.
   */
  public int getProceduresCoveredCount() {
    return entryUtilization.column("covered-"
        + HealthRecord.PROCEDURES).values().stream().mapToInt(ai -> ai.get()).sum();
  }

  /**
   * Returns the number of procedures this payer did not cover for their customers.
   */
  public int getProceduresUncoveredCount() {
    return entryUtilization.column("uncovered-"
        + HealthRecord.PROCEDURES).values().stream().mapToInt(ai -> ai.get()).sum();
  }

  /**
   * Returns the number of immunizations this payer paid for.
   */
  public int getImmunizationsCoveredCount() {
    return entryUtilization.column("covered-"
        + HealthRecord.IMMUNIZATIONS).values().stream().mapToInt(ai -> ai.get()).sum();
  }

  /**
   * Returns the number of immunizations this payer did not cover for their customers.
   */
  public int getImmunizationsUncoveredCount() {
    return entryUtilization.column("uncovered-"
        + HealthRecord.IMMUNIZATIONS).values().stream().mapToInt(ai -> ai.get()).sum();
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
   * Returns the average of the payer's QOLS of customers over the number of years covered.
   */
  public double getQolsAverage() {
    int numYears = this.getNumYearsCovered();
    return this.totalQOLS / numYears;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 53 * hash + Objects.hashCode(this.attributes);
    hash = 53 * hash + Objects.hashCode(this.name);
    hash = 53 * hash + Objects.hashCode(this.uuid);
    hash = 53 * hash + (int) (Double.doubleToLongBits(this.deductible)
            ^ (Double.doubleToLongBits(this.deductible) >>> 32));
    hash = 53 * hash + (int) (Double.doubleToLongBits(this.defaultCopay)
            ^ (Double.doubleToLongBits(this.defaultCopay) >>> 32));
    hash = 53 * hash + (int) (Double.doubleToLongBits(this.defaultCoinsurance)
            ^ (Double.doubleToLongBits(this.defaultCoinsurance) >>> 32));
    hash = 53 * hash + (int) (Double.doubleToLongBits(this.monthlyPremium)
            ^ (Double.doubleToLongBits(this.monthlyPremium) >>> 32));
    hash = 53 * hash + Objects.hashCode(this.ownership);
    hash = 53 * hash + Objects.hashCode(this.statesCovered);
    hash = 53 * hash + Objects.hashCode(this.servicesCovered);
    hash = 53 * hash + (int) (Double.doubleToLongBits(this.revenue)
            ^ (Double.doubleToLongBits(this.revenue) >>> 32));
    hash = 53 * hash + (int) (Double.doubleToLongBits(this.costsCovered)
            ^ (Double.doubleToLongBits(this.costsCovered) >>> 32));
    hash = 53 * hash + (int) (Double.doubleToLongBits(this.costsUncovered)
            ^ (Double.doubleToLongBits(this.costsUncovered) >>> 32));
    hash = 53 * hash + (int) (Double.doubleToLongBits(this.totalQOLS)
            ^ (Double.doubleToLongBits(this.totalQOLS) >>> 32));
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final Payer other = (Payer) obj;
    if (Double.doubleToLongBits(this.deductible)
            != Double.doubleToLongBits(other.deductible)) {
      return false;
    }
    if (Double.doubleToLongBits(this.defaultCopay)
            != Double.doubleToLongBits(other.defaultCopay)) {
      return false;
    }
    if (Double.doubleToLongBits(this.defaultCoinsurance)
            != Double.doubleToLongBits(other.defaultCoinsurance)) {
      return false;
    }
    if (Double.doubleToLongBits(this.monthlyPremium)
            != Double.doubleToLongBits(other.monthlyPremium)) {
      return false;
    }
    if (Double.doubleToLongBits(this.revenue)
            != Double.doubleToLongBits(other.revenue)) {
      return false;
    }
    if (Double.doubleToLongBits(this.costsCovered)
            != Double.doubleToLongBits(other.costsCovered)) {
      return false;
    }
    if (Double.doubleToLongBits(this.costsUncovered)
            != Double.doubleToLongBits(other.costsUncovered)) {
      return false;
    }
    if (Double.doubleToLongBits(this.totalQOLS)
            != Double.doubleToLongBits(other.totalQOLS)) {
      return false;
    }
    if (!Objects.equals(this.name, other.name)) {
      return false;
    }
    if (!Objects.equals(this.uuid, other.uuid)) {
      return false;
    }
    if (!Objects.equals(this.ownership, other.ownership)) {
      return false;
    }
    if (!Objects.equals(this.attributes, other.attributes)) {
      return false;
    }
    if (!Objects.equals(this.statesCovered, other.statesCovered)) {
      return false;
    }
    if (!Objects.equals(this.servicesCovered, other.servicesCovered)) {
      return false;
    }
    return true;
  }
  
  
}