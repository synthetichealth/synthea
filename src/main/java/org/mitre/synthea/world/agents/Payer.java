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

import org.apache.sis.geometry.DirectPosition2D;
import org.apache.sis.index.tree.QuadTree;
import org.apache.sis.index.tree.QuadTreeData;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.behaviors.IPayerFinder;
import org.mitre.synthea.world.agents.behaviors.PayerFinderBestRates;
import org.mitre.synthea.world.agents.behaviors.PayerFinderNearest;
import org.mitre.synthea.world.agents.behaviors.PayerFinderRandom;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.geography.Location;

public class Payer implements QuadTreeData {

  // Provider Selection Behavior algorithm choices:
  // public static final String NEAREST = "nearest";

  // ArrayList of all private payers imported
  private static ArrayList<Payer> payerList = new ArrayList<Payer>();
  // ArrayList of all government payers imported (Maybe?)
  // private static ArrayList<Payer> governmentPayerList = new ArrayList<Payer>();

  private static QuadTree payerMap = generateQuadTree();
  // The U.S. States loaded
  private static Set<String> statesLoaded = new HashSet<String>();
  // Number of payers loaded (?)
  private static int loaded = 0;

  /* Algorithms for choosing an insurance company - TODO */
  // private static final double MAX_PROVIDER_SEARCH_DISTANCE =
  // Double.parseDouble(Config.get("generate.providers.maximum_search_distance",
  // "500"));
  public static final String PROVIDER_SELECTION_BEHAVIOR = "random";
  // Config.get("generate.providers.selection_behavior", "nearest").toLowerCase();
  private static IPayerFinder payerFinder = buildPayerFinder();
// Provider Selection Behavior algorithm choices:
  public static final String NEAREST = "nearest";
  public static final String RANDOM = "random";
  public static final String NETWORK = "network";
  public static final String BESTRATE = "best_rate";

  // What attributes?
  public Map<String, Object> attributes;
  public String uuid;
  public String id;
  public String name;
  private Location location;
  public String address;
  public String city;
  public String state;
  public String zip;
  private double defaultCoverage;
  private double defaultCopay;
  private double monthlyPremium;
  private double deductible;
  // public String phone;
  // public String type;
  public String ownership;
  // public int quality;
  private DirectPosition2D coordinates;
  // public ArrayList<EncounterType> servicesProvided;
  // row: year, column: type, value: count
  private Table<Integer, String, AtomicInteger> utilization;

  // TEMP NO_INSURANCE OBJECT
  public static Payer noInsurance;

  /**
   * Create a new Provider with no information.
   */
  public Payer() {
    uuid = UUID.randomUUID().toString();
    attributes = new LinkedTreeMap<>();
    utilization = HashBasedTable.create();
    coordinates = new DirectPosition2D();
  }

  // Determines the algorithm to use to find a Payer
  private static IPayerFinder buildPayerFinder() {
    IPayerFinder finder = null;
    // String behavior = Config.get("generate.providers.selection_behavior", "nearest").toLowerCase();
    switch (PROVIDER_SELECTION_BEHAVIOR) {
      case BESTRATE:
        finder = new PayerFinderBestRates();
        break;
      case RANDOM:
        finder = new PayerFinderRandom();
        break;
      case NEAREST:
        finder = new PayerFinderNearest();
        break;
      default:
        throw new RuntimeException("Not a valid Payer Selction Algorithm");
    }
    return finder;
  }

  public String getResourceID() {
    return uuid;
  }

  public Map<String, Object> getAttributes() {
    return attributes;
  }

  public DirectPosition2D getCoordinates() {
    return coordinates;
  }

  // Necessary? Taken from Provider.java
  private synchronized void increment(Integer year, String key) {
    if (!utilization.contains(year, key)) {
      utilization.put(year, key, new AtomicInteger(0));
    }

    utilization.get(year, key).incrementAndGet();
  }

  public Table<Integer, String, AtomicInteger> getUtilization() {
    return utilization;
  }

  // Potentially unneeded... person will choose their insurance externally../ May
  // need this for when a payer starts making decisions about who to insure. May
  // be useful for choosing different patients based on differnt policies etc.
  // Every insurer will have a different set of guidelines for accepting a
  // customer, where to keep these? In the table? How?
  /**
   * Will this payer accept the given person at the given time?.
   * 
   * @param person Person to consider
   * @param time   Time the person seeks care
   * @return whether or not the person can receive care by this provider
   */
  public boolean accepts(Person person, long time) {
    // for now assume every payer accepts every patient
    // EXCEPT for medicaire & medicaid (Don't have all correct requirements)
    if (this.name.equals("Medicare") && person.ageInYears(time) < 65) {
      return false;
    } else if (this.name.equals("Medicaid") && !person.attributes.containsKey("blind")) {
      return false;
    } else if (this.name.equals("Dual Eligible") && !person.attributes.containsKey("blind") && person.ageInYears(time) < 65) {
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
   * @return the payer selection algorithm
   */
  public static IPayerFinder getPayerFinder(){
    return payerFinder;
  }

  /**
   * Clear the list of loaded and cached payers.
   */
  public static void clear() {
    payerList.clear();
    // governmentPayerList.clear();
    statesLoaded.clear();
    payerMap = generateQuadTree();
    loaded = 0;
  }

  // WHAT DOES THIS QUADTREE DO???
  /**
   * Generate a quad tree with sufficient capacity and depth to load the biggest
   * states.
   * 
   * @return QuadTree.
   */
  private static QuadTree generateQuadTree() {
    return new QuadTree(7500, 25); // capacity, depth
  }

  /**
   * Load into cache the list of payers for a state.
   * 
   * @param location the state being loaded.
   */
  public static void loadPayers(Location location) {
    if (!statesLoaded.contains(location.state) || !statesLoaded.contains(Location.getAbbreviation(location.state))
        || !statesLoaded.contains(Location.getStateName(location.state))) {
      try {
        String insuranceCompanyFile = Config.get("generate.payers.insurance_companies.default_file");
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
   * given state. THIS method is for loading providers and generating clinicians
   * with specific specialties
   * 
   * @param location         the state being loaded
   * @param fileName         Location of the file, relative to src/main/resources
   * @param servicesProvided Set of services provided by these facilities
   * @throws IOException if the file cannot be read
   */
  public static void loadPayers(Location location, String fileName) throws IOException {

    // TEMP no insurance payer
    noInsurance = new Payer();
    noInsurance.defaultCoverage = 0.0;
    noInsurance.name = "NO_INSURANCE";

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

        parsedPayer.location = location;

        payerList.add(parsedPayer);
        boolean inserted = payerMap.insert(parsedPayer);
        if (!inserted) {
          throw new RuntimeException(
              "Payer QuadTree Full! Dropping # " + loaded + ": " + parsedPayer.name + " @ " + parsedPayer.city);
        } else {
          loaded++;
        }
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
    newPayer.address = line.remove("address");
    newPayer.city = line.remove("city");
    newPayer.state = line.remove("state");
    newPayer.zip = line.remove("zip");
    newPayer.defaultCoverage = Double.parseDouble(line.remove("default_coverage"));
    newPayer.defaultCopay = Double.parseDouble(line.remove("default_copay"));
    newPayer.monthlyPremium = Double.parseDouble(line.remove("monthly_premium"));
    // newPayer.phone = line.remove("phone");
    // newPayer.type = line.remove("type");
    newPayer.ownership = line.remove("ownership");

    try {
      double lat = Double.parseDouble(line.remove("LAT"));
      double lon = Double.parseDouble(line.remove("LON"));
      newPayer.coordinates.setLocation(lon, lat);
    } catch (Exception e) {
      newPayer.coordinates.setLocation(0.0, 0.0);
    }
    return newPayer;
  }

  public static List<Payer> getPayerList() {
    return payerList;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.sis.index.tree.QuadTreeData#getX()
   */
  @Override
  public double getX() {
    return coordinates.getX();
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.sis.index.tree.QuadTreeData#getY()
   */
  @Override
  public double getY() {
    return coordinates.getY();
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.sis.index.tree.QuadTreeData#getLatLon()
   */
  @Override
  public DirectPosition2D getLatLon() {
    return coordinates;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.sis.index.tree.QuadTreeData#getFileName()
   */
  @Override
  public String getFileName() {
    return null;
  }

  public double getCoverage() {
    return this.defaultCoverage;
  }

  // For now, insurance will cover all services
  public boolean coversService(EncounterType service) {
    return true;
  }
}