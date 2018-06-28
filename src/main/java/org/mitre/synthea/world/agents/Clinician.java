package org.mitre.synthea.world.agents;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.common.collect.HashBasedTable; 
import com.google.common.collect.Table;          
import com.google.gson.internal.LinkedTreeMap;   

import org.apache.sis.geometry.DirectPosition2D;
import org.apache.sis.index.tree.QuadTree;
import org.apache.sis.index.tree.QuadTreeData;
import org.mitre.synthea.engine.EventList;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.VitalSign;





public class Clinician implements Serializable, QuadTreeData {

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
  public final long seed;
  public Map<String, Object> attributes;
  private ArrayList<String> servicesProvided;
  public long populationSeed;
  
  public Clinician(long seed) {
    this.seed = seed; // keep track of seed so it can be exported later
    random = new Random(seed);
    attributes = new ConcurrentHashMap<String, Object>();
    servicesProvided = new ArrayList<String>();
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
