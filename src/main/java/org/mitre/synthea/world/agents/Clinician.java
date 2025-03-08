package org.mitre.synthea.world.agents;

import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.world.geography.quadtree.QuadTreeElement;

public class Clinician implements Serializable, QuadTreeElement {
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

  public final long identifier;
  public final String uuid;
  public String npi;
  public Map<String, Object> attributes;
  private ArrayList<String> servicesProvided;
  private Provider organization;
  private int encounters;
  private int procedures;
  public long populationSeed;

  /**
   * Create a new clinician.
   * @param clinicianSeed The seed for this clinician.
   * @param clinicianRand The random number generator to use for this clinician.
   * @param identifier The clinician's organizational unique identifier.
   * @param organization The organization this clinician belongs to. May be null.
   */
  public Clinician(long clinicianSeed, RandomNumberGenerator clinicianRand,
      long identifier, Provider organization) {
    String base = clinicianSeed + ":" + identifier + ":"
        + ((organization == null) ? identifier : organization.npi)
        + ":" + clinicianRand.randLong();
    this.uuid = UUID.nameUUIDFromBytes(base.getBytes()).toString();
    this.identifier = identifier;
    this.npi = toClinicianNPI(this.identifier);
    this.organization = organization;
    attributes = new ConcurrentHashMap<String, Object>();
    servicesProvided = new ArrayList<String>();
  }

  private static String toClinicianNPI(long id) {
    if (id > 999_999_999L) {
      throw new IllegalArgumentException(
              String.format("Supplied id (%d) is too big, max is %d", id, 999_999_999L));
    }
    return Provider.toNPI(999_999_999L - id);
  }

  /**
   * Get the Clinician's UUID.
   * @return UUID as String.
   */
  public String getResourceID() {
    return uuid;
  }

  /**
   * Get the Clinician's Organization.
   * @return Provider organization. May be null.
   */
  public Provider getOrganization() {
    return organization;
  }

  /**
   * Get the clinician's full name, with title (e.g. "Dr.")
   * @return full name as string.
   */
  public String getFullname() {
    String prefix = (String) attributes.get(NAME_PREFIX);
    String name = (String) attributes.get(NAME);
    return prefix + " " + name;
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

  /**
   * Increment the number of procedures performed by this Clinician.
   * @return The incremented number of procedures.
   */
  public synchronized int incrementProcedures() {
    return procedures++;
  }

  /**
   * Get the number of procedures performed by this Clinician.
   * @return The number of procedures.
   */
  public int getProcedureCount() {
    return procedures;
  }

  @Override
  public double getX() {
    return getLonLat().getX();
  }

  @Override
  public double getY() {
    return getLonLat().getY();
  }

  public Point2D.Double getLonLat() {
    return (Point2D.Double) attributes.get(Person.COORDINATE);
  }
}
