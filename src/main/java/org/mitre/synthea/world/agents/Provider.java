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
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sis.geometry.DirectPosition2D;
import org.apache.sis.index.tree.QuadTree;
import org.apache.sis.index.tree.QuadTreeData;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.LifecycleModule;
import org.mitre.synthea.world.agents.behaviors.IProviderFinder;
import org.mitre.synthea.world.agents.behaviors.ProviderFinderNearest;
import org.mitre.synthea.world.agents.behaviors.ProviderFinderQuality;
import org.mitre.synthea.world.agents.behaviors.ProviderFinderRandom;
import org.mitre.synthea.world.concepts.ClinicianSpecialty;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.geography.Demographics;
import org.mitre.synthea.world.geography.Location;

public class Provider implements QuadTreeData {

  public static final String ENCOUNTERS = "encounters";
  public static final String PROCEDURES = "procedures";
  public static final String LABS = "labs";
  public static final String PRESCRIPTIONS = "prescriptions";

  // Provider Selection Behavior algorithm choices:
  public static final String NEAREST = "nearest";
  public static final String QUALITY = "quality";
  public static final String RANDOM = "random";
  public static final String NETWORK = "network";

  // ArrayList of all providers imported
  private static ArrayList<Provider> providerList = new ArrayList<Provider>();
  private static QuadTree providerMap = generateQuadTree();
  private static Set<String> statesLoaded = new HashSet<String>();
  private static int loaded = 0;

  private static final double MAX_PROVIDER_SEARCH_DISTANCE =
      Double.parseDouble(Config.get("generate.providers.maximum_search_distance", "500"));
  public static final String PROVIDER_SELECTION_BEHAVIOR =
      Config.get("generate.providers.selection_behavior", "nearest").toLowerCase();
  private static IProviderFinder providerFinder = buildProviderFinder();

  public Map<String, Object> attributes;
  public String uuid;
  public String id;
  public String name;
  private Location location;
  public String address;
  public String city;
  public String state;
  public String zip;
  public String phone;
  public String type;
  public String ownership;
  public int quality;
  private DirectPosition2D coordinates;
  public ArrayList<EncounterType> servicesProvided;
  public Map<String, ArrayList<Clinician>> clinicianMap;
  // row: year, column: type, value: count
  private Table<Integer, String, AtomicInteger> utilization;

  /**
   * Create a new Provider with no information.
   */
  public Provider() {
    uuid = UUID.randomUUID().toString();
    attributes = new LinkedTreeMap<>();
    utilization = HashBasedTable.create();
    servicesProvided = new ArrayList<EncounterType>();
    clinicianMap = new HashMap<String, ArrayList<Clinician>>();
    coordinates = new DirectPosition2D();
  }

  private static IProviderFinder buildProviderFinder() {
    IProviderFinder finder = null;
    String behavior =
        Config.get("generate.providers.selection_behavior", "nearest").toLowerCase();
    switch (behavior) {
      case QUALITY:
        finder = new ProviderFinderQuality();
        break;
      case RANDOM:
      case NETWORK:
        finder = new ProviderFinderRandom();
        break;
      case NEAREST:
      default:
        finder = new ProviderFinderNearest();
        break;
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

  public boolean hasService(EncounterType service) {
    return servicesProvided.contains(service);
  }

  public void incrementEncounters(EncounterType service, int year) {
    increment(year, ENCOUNTERS);
    increment(year, ENCOUNTERS + "-" + service);
  }

  public void incrementProcedures(int year) {
    increment(year, PROCEDURES);
  }

  public void incrementLabs(int year) {
    increment(year, LABS);
  }

  public void incrementPrescriptions(int year) {
    increment(year, PRESCRIPTIONS);
  }

  private synchronized void increment(Integer year, String key) {
    if (!utilization.contains(year, key)) {
      utilization.put(year, key, new AtomicInteger(0));
    }

    utilization.get(year, key).incrementAndGet();
  }

  public Table<Integer, String, AtomicInteger> getUtilization() {
    return utilization;
  }

  /**
   * Get the bed count for this Provider facility.
   * @return The number of beds, if they exist, otherwise null.
   */
  public Integer getBedCount() {
    if (attributes.containsKey("bed_count")) {
      return Integer.parseInt(attributes.get("bed_count").toString());
    } else {
      return null;
    }
  }
  
  /**
   * Will this provider accept the given person as a patient at the given time?.
   * @param person Person to consider
   * @param time Time the person seeks care
   * @return whether or not the person can receive care by this provider
   */
  public boolean accepts(Person person, long time) {
    // for now assume every provider accepts every patient
    // UNLESS it's a VA facility and the person is not a veteran
    // eventually we may want to expand this (ex. capacity?)
    if ("VA Facility".equals(this.type) && !person.attributes.containsKey("veteran")) {
      return false;
    }
    return true;
  }

  /**
   * Find specific service provider for the given person.
   * @param person The patient who requires the service.
   * @param service The service required. For example, EncounterType.AMBULATORY.
   * @param time The date/time within the simulated world, in milliseconds.
   * @return Service provider or null if none is available.
   */
  public static Provider findService(Person person, EncounterType service, long time) {
    double maxDistance = MAX_PROVIDER_SEARCH_DISTANCE;
    double distance = 100;
    double step = 100;
    List<Provider> options = null;
    Provider provider = null;
    while (distance <= maxDistance) {
      options = findProvidersByLocation(person, distance);
      provider = providerFinder.find(options, person, service, time);
      if (provider != null) {
        return provider;
      }
      distance += step;
    }
    return null;
  }

  /**
   * Find a service around a given point.
   * @param person The patient who requires the service.
   * @param distance in kilometers
   * @return List of providers within the given distance.
   */
  private static List<Provider> findProvidersByLocation(Person person, double distance) {
    DirectPosition2D coord = person.getLatLon();
    List<QuadTreeData> results = providerMap.queryByPointRadius(coord, distance);
    List<Provider> providers = new ArrayList<Provider>();
    for (QuadTreeData item : results) {
      providers.add((Provider) item);
    }
    return providers;
  }

  /**
   * Clear the list of loaded and cached providers.
   */
  public static void clear() {
    providerList.clear();
    statesLoaded.clear();
    providerMap = generateQuadTree();
    loaded = 0;
  }

  /**
   * Generate a quad tree with sufficient capacity and depth to load
   * the biggest states.
   * @return QuadTree.
   */
  private static QuadTree generateQuadTree() {
    return new QuadTree(7500, 25); // capacity, depth
  }
  
  /**
   * Load into cache the list of providers for a state.
   * @param location the state being loaded.
   */
  public static void loadProviders(Location location, long clinicianSeed, boolean preserveClinicianUUID) {
    if (!statesLoaded.contains(location.state)
        || !statesLoaded.contains(Location.getAbbreviation(location.state))
        || !statesLoaded.contains(Location.getStateName(location.state))) {
      try {
        Set<EncounterType> servicesProvided = new HashSet<EncounterType>();
        servicesProvided.add(EncounterType.AMBULATORY);
        servicesProvided.add(EncounterType.OUTPATIENT);
        servicesProvided.add(EncounterType.INPATIENT);
      
        String hospitalFile = Config.get("generate.providers.hospitals.default_file");
        loadProviders(location, hospitalFile, servicesProvided, clinicianSeed, preserveClinicianUUID);

        String vaFile = Config.get("generate.providers.veterans.default_file");
        loadProviders(location, vaFile, servicesProvided, clinicianSeed, preserveClinicianUUID);

        servicesProvided.clear();
        servicesProvided.add(EncounterType.WELLNESS);
        String primaryCareFile = Config.get("generate.providers.primarycare.default_file");
        loadProviders(location, primaryCareFile, servicesProvided, clinicianSeed, preserveClinicianUUID);
        
        servicesProvided.clear();
        servicesProvided.add(EncounterType.URGENTCARE);
        String urgentcareFile = Config.get("generate.providers.urgentcare.default_file");
        loadProviders(location, urgentcareFile, servicesProvided, clinicianSeed, preserveClinicianUUID);
      
        statesLoaded.add(location.state);
        statesLoaded.add(Location.getAbbreviation(location.state));
        statesLoaded.add(Location.getStateName(location.state));
      } catch (IOException e) {
        System.err.println("ERROR: unable to load providers for state: " + location.state);
        e.printStackTrace();
      }
    }
  }

  /**
   * Read the providers from the given resource file, only importing the ones for the given state.
   * THIS method is for loading providers and generating clinicians with specific specialties
   * 
   * @param location the state being loaded
   * @param filename Location of the file, relative to src/main/resources
   * @param servicesProvided Set of services provided by these facilities
   * @throws IOException if the file cannot be read
   */
  public static void loadProviders(Location location, String filename,
      Set<EncounterType> servicesProvided, long clinicianSeed, Boolean preserveClinicianUUID)
      throws IOException {
    String resource = Utilities.readResource(filename);
    Iterator<? extends Map<String,String>> csv = SimpleCSV.parseLineByLine(resource);
    Random clinicianRand = new Random(clinicianSeed);
    
    while (csv.hasNext()) {
      Map<String,String> row = csv.next();
      String currState = row.get("state");
      String abbreviation = Location.getAbbreviation(location.state);

      // for now, only allow one state at a time
      if ((location.state == null)
          || (location.state != null && location.state.equalsIgnoreCase(currState))
          || (abbreviation != null && abbreviation.equalsIgnoreCase(currState))) {
    
        Provider parsed = csvLineToProvider(row);
        parsed.servicesProvided.addAll(servicesProvided);

        if ("Yes".equals(row.remove("emergency"))) {
          parsed.servicesProvided.add(EncounterType.EMERGENCY);
        }

        // add any remaining columns we didn't explicitly map to first-class fields
        // into the attributes table
        for (Map.Entry<String, String> e : row.entrySet()) {
          parsed.attributes.put(e.getKey(), e.getValue());
        }

        parsed.location = location;
        // String city = parsed.city;
        // String address = parsed.address;

        if (row.get("hasSpecialties") == null
            || row.get("hasSpecialties").equalsIgnoreCase("false")) {
          parsed.clinicianMap.put(ClinicianSpecialty.GENERAL_PRACTICE, 
              parsed.generateClinicianList(1, ClinicianSpecialty.GENERAL_PRACTICE, clinicianSeed, clinicianRand, preserveClinicianUUID));
        } else {
          for (String specialty : ClinicianSpecialty.getSpecialties()) { 
            String specialtyCount = row.get(specialty);
            if (specialtyCount != null && !specialtyCount.trim().equals("") 
                && !specialtyCount.trim().equals("0")) {
              parsed.clinicianMap.put(specialty, 
                  parsed.generateClinicianList(Integer.parseInt(row.get(specialty)), specialty, clinicianSeed, clinicianRand, preserveClinicianUUID));
            }
          }
          if (row.get(ClinicianSpecialty.GENERAL_PRACTICE).equals("0")) {
            parsed.clinicianMap.put(ClinicianSpecialty.GENERAL_PRACTICE, 
                parsed.generateClinicianList(1, ClinicianSpecialty.GENERAL_PRACTICE, clinicianSeed, clinicianRand, preserveClinicianUUID));
          }
        }

        providerList.add(parsed);
        boolean inserted = providerMap.insert(parsed);
        if (!inserted) {
          throw new RuntimeException("Provider QuadTree Full! Dropping # " + loaded + ": "
              + parsed.name + " @ " + parsed.city);
        } else {
          loaded++;
        }
      }
    }
  }

  /**
   * Generates a list of clinicians, given the number to generate and the specialty.
   * @param numClinicians - the number of clinicians to generate
   * @param specialty - which specialty clinicians to generate
   * @return
   */
  private ArrayList<Clinician> generateClinicianList(int numClinicians, String specialty, 
    long clinicianSeed, Random clinicianRand, boolean preserveClinicianUUID) {
    ArrayList<Clinician> clinicians = new ArrayList<Clinician>();
    for (int i = 0; i < numClinicians; i++) {
      Clinician clinician = null;
      clinician = generateClinician(clinicianSeed, clinicianRand, preserveClinicianUUID, Long.parseLong(loaded + "" + i), this);
      clinician.attributes.put(Clinician.SPECIALTY, specialty);
      clinicians.add(clinician);
    }
    return clinicians;
  }

  /**
   * Generate a random clinician, from the given seed.
   *
   * @param clinicianSeed
   *          Seed for the random clinician
   * @return generated Clinician
   */
  private Clinician generateClinician(long clinicianSeed, Random clinicianRand, Boolean preserveClinicianUUID, long clinicianIdentifier, Provider provider) {
    Clinician clinician = null;
    try {
      Demographics city = location.randomCity(clinicianRand);
      Map<String, Object> out = new HashMap<>();

      String race = city.pickRace(clinicianRand);
      out.put(Person.RACE, race);
      String ethnicity = city.ethnicityFromRace(race, clinicianRand);
      out.put(Person.ETHNICITY, ethnicity);
      String language = city.languageFromEthnicity(ethnicity, clinicianRand);
      out.put(Person.FIRST_LANGUAGE, language);
      String gender = city.pickGender(clinicianRand);
      if (gender.equalsIgnoreCase("male") || gender.equalsIgnoreCase("M")) {
        gender = "M";
      } else {
        gender = "F";
      }
      out.put(Person.GENDER, gender);

      clinician = new Clinician(clinicianSeed, clinicianRand, preserveClinicianUUID, clinicianIdentifier);
      clinician.attributes.putAll(out);
      clinician.attributes.put(Person.ADDRESS, provider.address);
      clinician.attributes.put(Person.CITY, provider.city);
      clinician.attributes.put(Person.STATE, provider.state);
      clinician.attributes.put(Person.ZIP, provider.zip);

      String firstName = LifecycleModule.fakeFirstName(gender, language, clinician.random);
      String lastName = LifecycleModule.fakeLastName(language, clinician.random);

      if (LifecycleModule.appendNumbersToNames) {
        firstName = LifecycleModule.addHash(firstName);
        lastName = LifecycleModule.addHash(lastName);
      }
      clinician.attributes.put(Clinician.FIRST_NAME, firstName);
      clinician.attributes.put(Clinician.LAST_NAME, lastName);
      clinician.attributes.put(Clinician.NAME, firstName + " " + lastName);
      clinician.attributes.put(Clinician.NAME_PREFIX, "Dr.");
      // Degree's beyond a bachelors degree are not currently tracked.
      clinician.attributes.put(Clinician.EDUCATION, "bs_degree");
    } catch (Throwable e) {
      e.printStackTrace();
      throw e;
    }
    return clinician;
  }

  /**
   * Randomly chooses a clinician out of a given clinician list.
   * @param specialty - the specialty to choose from
   * @param random - random to help choose clinician
   * @return A clinician with the required specialty.
   */
  public Clinician chooseClinicianList(String specialty, Random random) {
    ArrayList<Clinician> clinicians = this.clinicianMap.get(specialty);
    Clinician doc = clinicians.get(random.nextInt(clinicians.size()));
    doc.incrementEncounters();
    return doc;
  }
  
  /**
   * Given a line of parsed CSV input, convert the data into a Provider.
   * @param line - read a csv line to a provider's attributes
   * @return A provider.
   */
  private static Provider csvLineToProvider(Map<String,String> line) {
    Provider d = new Provider();
    // using remove instead of get here so that we can iterate over the remaining keys later
    d.id = line.remove("id");
    d.name = line.remove("name");
    if (d.name == null || d.name.isEmpty()) {
      d.name = d.id;
    }
    String base = d.id + d.name;
    d.uuid = UUID.nameUUIDFromBytes(base.getBytes()).toString();
    d.address = line.remove("address");
    d.city = line.remove("city");
    d.state = line.remove("state");
    d.zip = line.remove("zip");
    d.phone = line.remove("phone");
    d.type = line.remove("type");
    d.ownership = line.remove("ownership");
    try {
      d.quality = Integer.parseInt(line.remove("quality"));
    } catch (Exception e) {
      // Swallow invalid format data
      d.quality = 0;
    }
    try {
      double lat = Double.parseDouble(line.remove("LAT"));
      double lon = Double.parseDouble(line.remove("LON"));
      d.coordinates.setLocation(lon, lat);
    } catch (Exception e) {
      d.coordinates.setLocation(0.0, 0.0);
    }
    return d;
  }

  public static List<Provider> getProviderList() {
    return providerList;
  }

  /*
   * (non-Javadoc)
   * @see org.apache.sis.index.tree.QuadTreeData#getX()
   */
  @Override
  public double getX() {
    return coordinates.getX();
  }

  /*
   * (non-Javadoc)
   * @see org.apache.sis.index.tree.QuadTreeData#getY()
   */
  @Override
  public double getY() {
    return coordinates.getY();
  }

  /*
   * (non-Javadoc)
   * @see org.apache.sis.index.tree.QuadTreeData#getLatLon()
   */
  @Override
  public DirectPosition2D getLatLon() {
    return coordinates;
  }

  /*
   * (non-Javadoc)
   * @see org.apache.sis.index.tree.QuadTreeData#getFileName()
   */
  @Override
  public String getFileName() {
    return null;
  }

}