package org.mitre.synthea.world.agents;

import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.world.geography.quadtree.QuadTreeElement;

/**
 * Represents a clinician, including their attributes, services provided, and
 * organizational details.
 */
public class Clinician implements Serializable, QuadTreeElement {
  /** Serial version UID for serialization. */
  private static final long serialVersionUID = 1370111157423846567L;

  /** Identifier for wellness services. */
  public static final String WELLNESS = "wellness";
  /** Identifier for ambulatory services. */
  public static final String AMBULATORY = "ambulatory";
  /** Identifier for inpatient services. */
  public static final String INPATIENT = "inpatient";
  /** Identifier for emergency services. */
  public static final String EMERGENCY = "emergency";
  /** Identifier for urgent care services. */
  public static final String URGENTCARE = "urgent care";

  /** Key for first name attribute. */
  public static final String FIRST_NAME = "first_name";
  /** Key for last name attribute. */
  public static final String LAST_NAME = "last_name";
  /** Key for name prefix attribute. */
  public static final String NAME_PREFIX = "name_prefix";
  /** Key for name suffix attribute. */
  public static final String NAME_SUFFIX = "name_suffix";
  /** Key for name attribute. */
  public static final String NAME = "name";
  /** Key for first language attribute. */
  public static final String FIRST_LANGUAGE = "first_language";
  /** Key for gender attribute. */
  public static final String GENDER = "gender";
  /** Key for education attribute. */
  public static final String EDUCATION = "education";
  /** Key for specialty attribute. */
  public static final String SPECIALTY = "specialty";

  /** Key for address attribute. */
  public static final String ADDRESS = "address";
  /** Key for city attribute. */
  public static final String CITY = "city";
  /** Key for state attribute. */
  public static final String STATE = "state";
  /** Key for ZIP code attribute. */
  public static final String ZIP = "zip";
  /** Key for location attribute. */
  public static final String LOCATION = "location";

  /** Unique identifier for the clinician. */
  public final long identifier;
  /** Universally unique identifier (UUID) for the clinician. */
  public final String uuid;
  /** National Provider Identifier (NPI) for the clinician. */
  public String npi;
  /** Attributes of the clinician, such as name, gender, and specialty. */
  public Map<String, Object> attributes;
  /** List of services provided by the clinician. */
  private ArrayList<String> servicesProvided;
  /** Organization to which the clinician belongs. */
  private Provider organization;
  /** Number of encounters performed by the clinician. */
  private int encounters;
  /** Number of procedures performed by the clinician. */
  private int procedures;
  /** Seed for generating random values for the clinician. */
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

  /**
   * Convert a clinician ID to an NPI.
   * @param id The clinician ID.
   * @return The NPI as a string.
   * @throws IllegalArgumentException if the ID is too large.
   */
  private static String toClinicianNPI(long id) {
    if (id > 999_999_999L) {
      throw new IllegalArgumentException(
              String.format("Supplied id (%d) is too big, max is %d", id, 999_999_999L));
    }
    return Provider.toNPI(999_999_999L - id);
  }

  /**
   * Get the clinician's UUID.
   * @return UUID as a string.
   */
  public String getResourceID() {
    return uuid;
  }

  /**
   * Get the clinician's organization.
   * @return The provider organization. May be null.
   */
  public Provider getOrganization() {
    return organization;
  }

  /**
   * Get the clinician's full name, including title (e.g., "Dr.").
   * @return The full name as a string.
   */
  public String getFullname() {
    String prefix = (String) attributes.get(NAME_PREFIX);
    String name = (String) attributes.get(NAME);
    return prefix + " " + name;
  }

  /**
   * Get the clinician's attributes.
   * @return A map of attributes.
   */
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  /**
   * Check if the clinician provides a specific service.
   * @param service The service to check.
   * @return True if the service is provided, false otherwise.
   */
  public boolean hasService(String service) {
    return servicesProvided.contains(service);
  }

  /**
   * Increment the number of encounters performed by this clinician.
   * @return The incremented number of encounters.
   */
  public synchronized int incrementEncounters() {
    return encounters++;
  }

  /**
   * Get the number of encounters performed by this clinician.
   * @return The number of encounters.
   */
  public int getEncounterCount() {
    return encounters;
  }

  /**
   * Increment the number of procedures performed by this clinician.
   * @return The incremented number of procedures.
   */
  public synchronized int incrementProcedures() {
    return procedures++;
  }

  /**
   * Get the number of procedures performed by this clinician.
   * @return The number of procedures.
   */
  public int getProcedureCount() {
    return procedures;
  }

  /**
   * Get the X coordinate of the clinician's location.
   * @return The X coordinate.
   */
  @Override
  public double getX() {
    return getLonLat().getX();
  }

  /**
   * Get the Y coordinate of the clinician's location.
   * @return The Y coordinate.
   */
  @Override
  public double getY() {
    return getLonLat().getY();
  }

  /**
   * Get the clinician's geographic location as a point.
   * @return The location as a Point2D.Double.
   */
  public Point2D.Double getLonLat() {
    return (Point2D.Double) attributes.get(Person.COORDINATE);
  }
}
