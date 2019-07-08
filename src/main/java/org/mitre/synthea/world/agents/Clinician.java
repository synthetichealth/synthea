package org.mitre.synthea.world.agents;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.sis.geometry.DirectPosition2D;
import org.apache.sis.index.tree.QuadTreeData;

public class Clinician implements Serializable, QuadTreeData {
  private static final long serialVersionUID = 1370111157423846567L;

  public static final String WELLNESS = "wellness";
  public static final String AMBULATORY = "ambulatory";
  public static final String INPATIENT = "inpatient";
  public static final String EMERGENCY = "emergency";
  public static final String URGENTCARE = "urgent care";
  
  public static final String FIRST_NAME = "first_name";
  public static final String LAST_NAME = "last_name";
  public static final String NAME_PREFIX = "name_prefix";
  public static final String NAME_SUFFIX = "name_suffix";
  public static final String NAME = "name";
  public static final String FIRST_LANGUAGE = "first_language";
  public static final String GENDER = "gender";
  public static final String EDUCATION = "education";
  public static final String SPECIALTY = "specialty";
  
  public static final String ADDRESS = "address";
  public static final String CITY = "city";
  public static final String STATE = "state";
  public static final String ZIP = "zip";
  public static final String LOCATION = "location";
  
  
  public final Random random;
  public final long identifier;
  public final String uuid;
  public Map<String, Object> attributes;
  private ArrayList<String> servicesProvided;
  private int encounters;
  public long populationSeed;
  
  public Clinician(long clinicianSeed, Random clinicianRand, long identifier) {
    
    this.uuid =  new UUID(clinicianSeed, identifier).toString();    
    this.random = clinicianRand;
    this.identifier = identifier;
    attributes = new ConcurrentHashMap<String, Object>();
    servicesProvided = new ArrayList<String>();
  }

  public String getResourceID() {
    return uuid;
  }

  public double rand() {
    return random.nextDouble();
  }
  
  public Map<String, Object> getAttributes() {
    return attributes;
  }
  
  public boolean hasService(String service) {
    return servicesProvided.contains(service);
  }

  /**
   * Increment the number of encounters performed by this Clinician.
   * @return The incremented number of encounters.
   */
  public synchronized int incrementEncounters() {
    return encounters++;
  }

  /**
   * Get the number of encounters performed by this Clinician.
   * @return The number of encounters.
   */
  public int getEncounterCount() {
    return encounters;
  }
  
  public int randInt() {
    return random.nextInt();
  }

  public int randInt(int bound) {
    return random.nextInt(bound);
  }
  
  @Override
  public double getX() {
    // TODO Auto-generated method stub
    return 0;
  }
  
  @Override
  public double getY() {
    // TODO Auto-generated method stub
    return 0;
  }
  
  @Override
  public DirectPosition2D getLatLon() {
    // TODO Auto-generated method stub
    return null;
  }
  
  @Override
  public String getFileName() {
    // TODO Auto-generated method stub
    return null;
  }
}
