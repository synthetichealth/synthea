package org.mitre.synthea.world.agents;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.gson.internal.LinkedTreeMap;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class Provider {

  public static final String AMBULATORY = "ambulatory";
  public static final String INPATIENT = "inpatient";
  public static final String EMERGENCY = "emergency";
  public static final String ENCOUNTERS = "encounters";
  public static final String PROCEDURES = "procedures";
  public static final String LABS = "labs";
  public static final String PRESCRIPTIONS = "prescriptions";

  // ArrayList of all providers imported
  private static ArrayList<Provider> providerList = new ArrayList<Provider>();
  // Hash of services to Providers that provide them
  private static HashMap<String, ArrayList<Provider>> services = 
      new HashMap<String, ArrayList<Provider>>();

  public Map<String, Object> attributes;
  private Point coordinates;
  private ArrayList<String> servicesProvided;
  // row: year, column: type, value: count
  private Table<Integer, String, AtomicInteger> utilization;

  protected Provider() {
    // no-arg constructor provided for subclasses
    attributes = new LinkedTreeMap<>();
    utilization = HashBasedTable.create();
    servicesProvided = new ArrayList<String>();
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public Provider(LinkedTreeMap p) {
    this();
    attributes = (LinkedTreeMap) p.get("properties");
    String resourceID = (String) p.get("resourceID");
    attributes.put("resourceID", resourceID);

    ArrayList<Double> coorList = (ArrayList<Double>) p.get("coordinates");
    Point coor = new GeometryFactory()
        .createPoint(new Coordinate(coorList.get(0), coorList.get(1)));
    coordinates = coor;

    String[] servicesList = ((String) attributes.get("services_provided")).split(" ");
    for (String s : servicesList) {
      servicesProvided.add(s);
      // add provider to hash of services
      if (services.containsKey(s)) {
        ArrayList<Provider> l = services.get(s);
        l.add(this);
      } else {
        ArrayList<Provider> l = new ArrayList<Provider>();
        l.add(this);
        services.put(s, l);
      }
    }
  }

  public static void clear() {
    providerList.clear();
    services.clear();
  }

  public String getResourceID() {
    return attributes.get("resourceID").toString();
  }

  public Map<String, Object> getAttributes() {
    return attributes;
  }

  public Point getCoordinates() {
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

  // TODO: increment labs when there are reports
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
    if (service.equals("outpatient") || service.equals("wellness")) {
      service = AMBULATORY;
    }
    switch (service) {
      case AMBULATORY:
        return person.getAmbulatoryProvider();
      case INPATIENT:
        return person.getInpatientProvider();
      case EMERGENCY:
        return person.getEmergencyProvider();
      default:
        // if service is null or not supported by simulation, patient goes to ambulatory hospital
        return person.getAmbulatoryProvider();
    }
  }

  public static HashMap<String, ArrayList<Provider>> getServices() {
    return services;
  }
}