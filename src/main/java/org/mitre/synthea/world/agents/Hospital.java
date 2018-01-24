package org.mitre.synthea.world.agents;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.vividsolutions.jts.geom.Point;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.UUID;

import org.mitre.synthea.helpers.Utilities;

public class Hospital extends Provider {

  // ArrayList of all hospitals imported
  private static ArrayList<Hospital> hospitalList = new ArrayList<Hospital>();

  public Hospital(LinkedTreeMap p) {
    super(p);
  }

  public static void clear() {
    hospitalList.clear();
  }

  @SuppressWarnings("unchecked")
  public static void loadHospitals() {
    String filename = "geography/healthcare_facilities.json";

    try {
      String json = Utilities.readResource(filename);
      Gson g = new Gson();
      HashMap<String, LinkedTreeMap> gson = g.fromJson(json, HashMap.class);
      for (Entry<String, LinkedTreeMap> entry : gson.entrySet()) {
        LinkedTreeMap value = entry.getValue();
        String resourceID = UUID.randomUUID().toString();
        value.put("resourceID", resourceID);
        Hospital h = new Hospital(value);
        hospitalList.add(h);
      }
    } catch (Exception e) {
      System.err.println("ERROR: unable to load json: " + filename);
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    }
  }

  public static ArrayList<Hospital> getHospitalList() {
    return hospitalList;
  }

  /**
   * Find closest hospital with ambulatory service. Will fall back to random selection if the person
   * doesn't have geographic location information.
   * 
   * @param person
   *          The person seeking ambulatory care.
   * @return The closest hospital, or a random hospital if the person lacks a geographical
   *         coordinate.
   */
  public static Hospital findClosestAmbulatory(Person person) {
    Point personLocation = (Point) person.attributes.get(Person.COORDINATE);
    List<Provider> emergencyHospitals = Provider.getServices().get(Provider.AMBULATORY);
    if (personLocation == null) {
      return randomHospital(emergencyHospitals, person.random);
    } else {
      return findClosestHospital(personLocation, emergencyHospitals);
    }
  }

  /**
   * Find closest hospital with inpatient service. Will fall back to random selection if the person
   * doesn't have geographic location information.
   * 
   * @param person
   *          The person seeking inpatient care.
   * @return The closest hospital, or a random hospital if the person lacks a geographical
   *         coordinate.
   */
  public static Hospital findClosestInpatient(Person person) {
    Point personLocation = (Point) person.attributes.get(Person.COORDINATE);
    List<Provider> emergencyHospitals = Provider.getServices().get(Provider.INPATIENT);
    if (personLocation == null) {
      return randomHospital(emergencyHospitals, person.random);
    } else {
      return findClosestHospital(personLocation, emergencyHospitals);
    }
  }

  /**
   * Find closest hospital with emergency service. Will fall back to random selection if the person
   * doesn't have geographic location information.
   * 
   * @param person
   *          The person seeking emergency care.
   * @return The closest hospital, or a random hospital if the person lacks a geographical
   *         coordinate.
   */
  public static Hospital findClosestEmergency(Person person) {
    Point personLocation = (Point) person.attributes.get(Person.COORDINATE);
    List<Provider> emergencyHospitals = Provider.getServices().get(Provider.EMERGENCY);
    if (personLocation == null) {
      return randomHospital(emergencyHospitals, person.random);
    } else {
      return findClosestHospital(personLocation, emergencyHospitals);
    }
  }
  
  private static Hospital randomHospital(List<Provider> providers, Random random) {
    int index = random.nextInt(providers.size());
    return (Hospital)providers.get(index);
  }
  
  private static Hospital findClosestHospital(Point personLocation, List<Provider> providers) {
    double personLat = personLocation.getY();
    double personLong = personLocation.getX();

    double closestDistance = Double.MAX_VALUE;
    Provider closestHospital = null;
    for (Provider p : providers) {
      Point hospitalLocation = p.getCoordinates();
      double hospitalLat = hospitalLocation.getY();
      double hospitalLong = hospitalLocation.getX();
      double sphericalDistance = haversine(personLat, personLong, hospitalLat, hospitalLong);
      if (sphericalDistance < closestDistance) {
        closestDistance = sphericalDistance;
        closestHospital = p;
      }
    }
    return (Hospital) closestHospital;
  }

  // Haversine Formula from https://rosettacode.org/wiki/Haversine_formula#Java
  public static final double R = 6372.8; // In kilometers

  public static double haversine(double lat1, double lon1, double lat2, double lon2) {
    double rdLat = Math.toRadians(lat2 - lat1);
    double rdLon = Math.toRadians(lon2 - lon1);
    lat1 = Math.toRadians(lat1);
    lat2 = Math.toRadians(lat2);

    double a = Math.pow(Math.sin(rdLat / 2), 2)
        + Math.pow(Math.sin(rdLon / 2), 2) * Math.cos(lat1) * Math.cos(lat2);
    double c = 2 * Math.asin(Math.sqrt(a));
    return R * c;
  }
}