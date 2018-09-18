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
import org.mitre.synthea.world.concepts.ClinicianSpecialty;
import org.mitre.synthea.world.geography.Demographics;
import org.mitre.synthea.world.geography.Location;

public class Provider implements QuadTreeData {

  public static final String WELLNESS = "wellness";
  public static final String AMBULATORY = "ambulatory";
  public static final String INPATIENT = "inpatient";
  public static final String EMERGENCY = "emergency";
  public static final String URGENTCARE = "urgent care";
  public static final String ENCOUNTERS = "encounters";
  public static final String PROCEDURES = "procedures";
  public static final String LABS = "labs";
  public static final String PRESCRIPTIONS = "prescriptions";

  // ArrayList of all providers imported
  private static ArrayList<Provider> providerList = new ArrayList<Provider>();
  private static QuadTree providerMap = new QuadTree(1400, 1400); // node capacity, depth
  private static Set<String> statesLoaded = new HashSet<String>();

  private static final double MAX_PROVIDER_SEARCH_DISTANCE =
      Double.parseDouble(Config.get("generate.maximum_provider_search_distance", "500"));
  
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
  public ArrayList<String> servicesProvided;
  public Map<String, ArrayList<Clinician>> clinicianMap;
  // row: year, column: type, value: count
  private Table<Integer, String, AtomicInteger> utilization;

  protected Provider() {
    attributes = new LinkedTreeMap<>();
    utilization = HashBasedTable.create();
    servicesProvided = new ArrayList<String>();
    clinicianMap = new HashMap<String, ArrayList<Clinician>>();
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

  public boolean hasService(String service) {
    return servicesProvided.contains(service);
  }

  public void incrementEncounters(String encounterType, int year) {
    increment(year, ENCOUNTERS);
    increment(year, ENCOUNTERS + "-" + encounterType);
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
   * Find specific service closest to the person, with a maximum distance of 500 kilometers.
   * @param person The patient who requires the service.
   * @param service The service required. For example, Provider.AMBULATORY.
   * @param time The date/time within the simulated world, in milliseconds.
   * @return Service provider or null if none is available.
   */
  public static Provider findClosestService(Person person, String service, long time) {
    double maxDistance = MAX_PROVIDER_SEARCH_DISTANCE;
    double distance = 100;
    double step = 100;
    Provider provider = null;
    while (distance <= maxDistance) {
      provider = findService(person, service, distance, time);
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
   * @param service e.g. Provider.AMBULATORY
   * @param searchDistance in kilometers
   * @param time The date/time within the simulated world, in milliseconds.
   * @return Service provider or null if none is available.
   */
  private static Provider findService(Person person,
      String service, double searchDistance, long time) {
    DirectPosition2D coord = person.getLatLon();
    List<QuadTreeData> results = providerMap.queryByPointRadius(coord, searchDistance);

    Provider closest = null;
    Provider provider = null;
    double minDistance = Double.MAX_VALUE;
    double distance;

    for (QuadTreeData item : results) {
      provider = (Provider) item;
      if (provider.accepts(person, time)
          && (provider.hasService(service) || service == null)) {
        distance = item.getLatLon().distance(coord);
        if (distance < minDistance) {
          closest = (Provider) item;
          minDistance = distance;
        }
      }
    }

    return closest;
  }

  /**
   * Clear the list of loaded and cached providers.
   */
  public static void clear() {
    providerList.clear();
    statesLoaded.clear();
    providerMap = new QuadTree(1400, 1400); // node capacity, depth
  }
  
  /**
   * Load into cache the list of providers for a state.
   * @param location the state being loaded.
   */
  public static void loadProviders(Location location) {
    if (!statesLoaded.contains(location.state)
        || !statesLoaded.contains(Location.getAbbreviation(location.state))
        || !statesLoaded.contains(Location.getStateName(location.state))) {
      try {
        Set<String> servicesProvided = new HashSet<String>();
        servicesProvided.add(Provider.AMBULATORY);
        servicesProvided.add(Provider.INPATIENT);
      
        String hospitalFile = Config.get("generate.providers.hospitals.default_file");
        loadProviders(location, hospitalFile, servicesProvided);

        String vaFile = Config.get("generate.providers.veterans.default_file");
        loadProviders(location, vaFile, servicesProvided);

        servicesProvided.clear();
        servicesProvided.add(Provider.WELLNESS);
        String primaryCareFile = Config.get("generate.providers.primarycare.default_file");
        loadProviders(location, primaryCareFile, servicesProvided);
        
        servicesProvided.clear();
        servicesProvided.add(Provider.URGENTCARE);
        String urgentcareFile = Config.get("generate.providers.urgentcare.default_file");
        loadProviders(location, urgentcareFile, servicesProvided);
      
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
      Set<String> servicesProvided)
      throws IOException {
    String resource = Utilities.readResource(filename);
    Iterator<? extends Map<String,String>> csv = SimpleCSV.parseLineByLine(resource);
    
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
          parsed.servicesProvided.add(Provider.EMERGENCY);
        }

        // add any remaining columns we didn't explicitly map to first-class fields
        // into the attributes table
        for (Map.Entry<String, String> e : row.entrySet()) {
          parsed.attributes.put(e.getKey(), e.getValue());
        }

        parsed.location = location;
        // String city = parsed.city;
        // String address = parsed.address;

        if (row.get("hasSpecialties") == null || row.get("hasSpecialties").equalsIgnoreCase("false")) {
          parsed.clinicianMap.put(ClinicianSpecialty.GENERAL_PRACTICE, 
              parsed.generateClinicianList(1, ClinicianSpecialty.GENERAL_PRACTICE));
        } else {
          for (String specialty : ClinicianSpecialty.getSpecialties()) { 
            String specialtyCount = row.get(specialty);
            if (specialtyCount != null && !specialtyCount.trim().equals("") 
                && !specialtyCount.trim().equals("0")) {
              parsed.clinicianMap.put(specialty, 
                  parsed.generateClinicianList(Integer.parseInt(row.get(specialty)), specialty));
            }
          }
          if (row.get(ClinicianSpecialty.GENERAL_PRACTICE).equals("0")) {
            parsed.clinicianMap.put(ClinicianSpecialty.GENERAL_PRACTICE, 
                parsed.generateClinicianList(1, ClinicianSpecialty.GENERAL_PRACTICE));
          }
        }

        providerList.add(parsed);
        boolean inserted = providerMap.insert(parsed);
        if (!inserted) {
          System.err.println("Provider QuadTree Full! Dropping "
              + parsed.name + " @ " + parsed.city);
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
  private ArrayList<Clinician> generateClinicianList(int numClinicians, String specialty) {
    ArrayList<Clinician> clinicians = new ArrayList<Clinician>();
    for (int i = 0; i < numClinicians; i++) {
      Clinician clinician = null;
      clinician = generateClinician(i, this);
      clinician.attributes.put(Clinician.SPECIALTY, specialty);
      clinicians.add(clinician);
    }
    return clinicians;
  }
  
  /**
   * Generate a completely random Clinician.
   * The seed used to generate the person is randomized as well.
   *
   * @param index Target index in the whole set of people to generate
   * @return generated Person
   */
  private Clinician generateClinician(int index, Provider provider) {
    // System.currentTimeMillis is not unique enough
    long clinicianSeed = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    return generateClinician(index, clinicianSeed, provider);
  }

  /**
   * Generate a random clinician, from the given seed.
   *
   * @param index
   *          Target index in the whole set of people to generate
   * @param clinicianSeed
   *          Seed for the random clinician
   * @return generated Clinician
   */
  private Clinician generateClinician(int index, long clinicianSeed, Provider provider) {
    Clinician clinician = null;
    try {
      Random randomForDemographics = new Random(clinicianSeed);
      Demographics city = location.randomCity(randomForDemographics);
      Map<String, Object> out = new HashMap<>();

      String race = city.pickRace(randomForDemographics);
      out.put(Person.RACE, race);
      String ethnicity = city.ethnicityFromRace(race, randomForDemographics);
      out.put(Person.ETHNICITY, ethnicity);
      String language = city.languageFromEthnicity(ethnicity, randomForDemographics);
      out.put(Person.FIRST_LANGUAGE, language);
      String gender = city.pickGender(randomForDemographics);
      if (gender.equalsIgnoreCase("male") || gender.equalsIgnoreCase("M")) {
        gender = "M";
      } else {
        gender = "F";
      }
      out.put(Person.GENDER, gender);

      clinician = new Clinician(clinicianSeed);
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
    return clinicians.get(random.nextInt(clinicians.size()));
  }
  
  /**
   * Given a line of parsed CSV input, convert the data into a Provider.
   * @param line - read a csv line to a provider's attributes
   * @return A provider.
   */
  private static Provider csvLineToProvider(Map<String,String> line) {
    Provider d = new Provider();
    d.uuid = UUID.randomUUID().toString();
    // using remove instead of get here so that we can iterate over the remaining keys later
    d.id = line.remove("id");
    d.name = line.remove("name");
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
    }
    try {
      double lat = Double.parseDouble(line.remove("LAT"));
      double lon = Double.parseDouble(line.remove("LON"));
      d.coordinates = new DirectPosition2D(lon, lat);
    } catch (Exception e) {
      double lat = 0.0;
      double lon = 0.0;
      d.coordinates = new DirectPosition2D(lon, lat);
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