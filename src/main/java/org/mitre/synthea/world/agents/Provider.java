package org.mitre.synthea.world.agents;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.gson.internal.LinkedTreeMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sis.geometry.DirectPosition2D;
import org.apache.sis.index.tree.QuadTree;
import org.apache.sis.index.tree.QuadTreeData;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.geography.Location;

public class Provider implements QuadTreeData {

  public static final String WELLNESS = "wellness";
  public static final String AMBULATORY = "ambulatory";
  public static final String INPATIENT = "inpatient";
  public static final String EMERGENCY = "emergency";
  public static final String ENCOUNTERS = "encounters";
  public static final String PROCEDURES = "procedures";
  public static final String LABS = "labs";
  public static final String PRESCRIPTIONS = "prescriptions";

  // ArrayList of all providers imported
  private static ArrayList<Provider> providerList = new ArrayList<Provider>();
  private static QuadTree providerMap = new QuadTree(500, 500); // node capacity, depth

  public Map<String, Object> attributes;
  public String uuid;
  public String id;
  public String name;
  public String address;
  public String city;
  public String state;
  public String zip;
  public String phone;
  public String type;
  public String ownership;
  public int quality;
  private DirectPosition2D coordinates;
  private ArrayList<String> servicesProvided;
  // row: year, column: type, value: count
  private Table<Integer, String, AtomicInteger> utilization;

  protected Provider() {
    attributes = new LinkedTreeMap<>();
    utilization = HashBasedTable.create();
    servicesProvided = new ArrayList<String>();
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

  public Integer getBedCount() {
    if (attributes.containsKey("bed_count")) {
      return Integer.parseInt(attributes.get("bed_count").toString());
    } else {
      return null;
    }
  }

  public static Provider findClosestService(Person person, String service) {
    double maxDistance = 500;
    double distance = 100;
    double step = 100;
    Provider provider = null;
    while (provider == null && distance <= maxDistance) {
      provider = findService(person.getLatLon(), service, distance);
      if (provider != null) {
        return provider;
      }
      distance += step;
    }
    return null;
  }

  /**
   * Find a service around a given point.
   * @param coord The location to search near
   * @param service e.g. Provider.AMBULATORY
   * @param searchDistance in kilometers
   * @return Service provider or null if none is available.
   */
  private static Provider findService(DirectPosition2D coord,
      String service, double searchDistance) {
    List<QuadTreeData> results = providerMap.queryByPointRadius(coord, searchDistance);

    Provider closest = null;
    Provider provider = null;
    double minDistance = Double.MAX_VALUE;
    double distance;

    for (QuadTreeData item : results) {
      provider = (Provider) item;
      if (provider.hasService(service) || service == null) {
        distance = item.getLatLon().distance(coord);
        if (distance < minDistance) {
          closest = (Provider) item;
        }
      }
    }

    return closest;
  }

  /**
   * Load into cache the list of providers for a state.
   * @param state name or abbreviation.
   */
  public static void loadProviders(String state) {
    try {
      String abbreviation = Location.getAbbreviation(state);
      String hospitalFile = Config.get("generate.providers.hospitals.default_file");
      loadProviders(state, abbreviation, hospitalFile);
    } catch (IOException e) {
      System.err.println("ERROR: unable to load providers for state: " + state);
      e.printStackTrace();
    }
  }

  /**
   * Read the providers from the given resource file, only importing the ones for the given state.
   * 
   * @param state Name of the current state, ex "Massachusetts"
   * @param abbreviation State abbreviation, ex "MA"
   * @param filename Location of the file, relative to src/main/resources
   * @throws IOException if the file cannot be read
   */
  public static void loadProviders(String state, String abbreviation, String filename)
      throws IOException {
    String resource = Utilities.readResource(filename);
    List<? extends Map<String,String>> csv = SimpleCSV.parse(resource);

    for (Map<String,String> row : csv) {
      String currState = row.get("state");

      // for now, only allow one state at a time
      if ((state == null)
          || (state != null && state.equalsIgnoreCase(currState))
          || (abbreviation != null && abbreviation.equalsIgnoreCase(currState))) {
        Provider parsed = csvLineToProvider(row);
        parsed.servicesProvided.add(Provider.AMBULATORY);
        parsed.servicesProvided.add(Provider.INPATIENT);
        parsed.servicesProvided.add(Provider.WELLNESS);
        if ("Yes".equals(row.get("emergency"))) {
          parsed.servicesProvided.add(Provider.EMERGENCY);
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

  private static Provider csvLineToProvider(Map<String,String> line) {
    Provider d = new Provider();
    d.uuid = UUID.randomUUID().toString();
    d.id = line.get("id");
    d.name = line.get("name");
    d.address = line.get("address");
    d.city = line.get("city");
    d.state = line.get("state");
    d.zip = line.get("zip");
    d.phone = line.get("phone");
    d.type = line.get("type");
    d.ownership = line.get("ownership");
    try {
      d.quality = Integer.parseInt(line.get("quality"));
    } catch (Exception e) {
      // Swallow invalid format data
    }
    double lat = Double.parseDouble(line.get("LAT"));
    double lon = Double.parseDouble(line.get("LON"));
    d.coordinates = new DirectPosition2D(lat, lon);
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