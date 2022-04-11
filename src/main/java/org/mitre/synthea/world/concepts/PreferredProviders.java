package org.mitre.synthea.world.concepts;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import java.io.Serializable;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;

/**
 * Class for managing the relationships between a Person and their perferred Providers.
 */
public class PreferredProviders implements Serializable {
  // The table class does not allow null values for keys
  private static String NULL_PLACEHOLDER = ClinicianSpecialty.GENERAL_PRACTICE;

  private Table<HealthRecord.EncounterType, String, Provider> relationships;

  /**
   * Create a new PreferredProviders object.
   */
  public PreferredProviders() {
    this.relationships = HashBasedTable.create();
  }

  /**
   * Checks to see if a relationship exists for a particular EncounterType and Specialty.
   * @param type the EncounterType
   * @param speciality the ClinicianSpecialty, may be null
   * @return true if there is an existing relationship
   */
  public boolean doesRelationshipExist(HealthRecord.EncounterType type, String speciality) {
    return relationships.contains(type, blankSafeSpecialty(speciality));
  }

  /**
   * Return the preferred Provider for a particular EncounterType and Specialty.
   * @param type the EncounterType
   * @param speciality the ClinicianSpecialty, may be null
   * @return the Provider if there is a preferred one or null
   */
  public Provider get(HealthRecord.EncounterType type, String speciality) {
    return relationships.get(type, blankSafeSpecialty(speciality));
  }

  /**
   * Creates a new preferred Provider for the supplied Person by using the provider finder
   * infrastructure. Will replace the existing preferred Provider if one is there.
   * @param person to find a preferred Provider for
   * @param type the EncounterType
   * @param speciality the ClinicianSpecialty, may be null
   * @param time in the simulation
   * @return the new preferred Provider
   */
  public Provider startNewRelationship(Person person, HealthRecord.EncounterType type,
                                       String speciality, long time) {
    Provider provider = Provider.findService(person, type, speciality, time);
    if (provider == null && Provider.USE_HOSPITAL_AS_DEFAULT) {
      // Default to Hospital
      provider = Provider.findService(person, HealthRecord.EncounterType.INPATIENT, speciality,
          time);
    }

    relationships.put(type, blankSafeSpecialty(speciality), provider);

    return provider;
  }

  /**
   * Gets the preferred wellness Provider.
   * @return the Provider if there is one or null
   */
  public Provider getWellnessProvider() {
    return get(HealthRecord.EncounterType.WELLNESS, null);
  }

  /**
   * Force a specific preferred Provider without going through the provider finder infrastructure.
   * @param type the EncounterType
   * @param speciality the ClinicianSpecialty, may be null
   * @param provider the Provider who will now be preferred, like it or not
   */
  public void forceRelationship(HealthRecord.EncounterType type, String speciality,
                                Provider provider) {
    relationships.put(type, blankSafeSpecialty(speciality), provider);
  }

  /**
   * Remove the preferred provider for a specific EncounterType and ClinicianSpecialty.
   * @param type the EncounterType
   * @param speciality the ClinicalSpecialty, may be null
   */
  public void resetRelationship(HealthRecord.EncounterType type, String speciality) {
    relationships.remove(type, blankSafeSpecialty(speciality));
  }

  /**
   * Remove all preferred provider relationships.
   */
  public void reset() {
    relationships.clear();
  }

  private String blankSafeSpecialty(String providedSpecialty) {
    String specialityColumnValue = providedSpecialty;
    if (specialityColumnValue == null || specialityColumnValue.isEmpty()) {
      specialityColumnValue = NULL_PLACEHOLDER;
    }
    return specialityColumnValue;
  }
}