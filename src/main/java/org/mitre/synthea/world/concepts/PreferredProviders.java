package org.mitre.synthea.world.concepts;

import java.io.Serializable;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;

public class PreferredProviders implements Serializable {
  // The table class does not allow null values for keys
  private static String NULL_PLACEHOLDER = "*";

  private Table<HealthRecord.EncounterType, String, Provider> relationships;

  public PreferredProviders() {
    this.relationships = HashBasedTable.create();
  }

  public boolean doesRelationshipExist(HealthRecord.EncounterType type, String speciality) {
    String specialityColumnValue = speciality;
    if(specialityColumnValue == null) {
      specialityColumnValue = NULL_PLACEHOLDER;
    }
    return relationships.contains(type, specialityColumnValue);
  }

  public Provider get(HealthRecord.EncounterType type, String speciality) {
    String specialityColumnValue = speciality;
    if(specialityColumnValue == null) {
      specialityColumnValue = NULL_PLACEHOLDER;
    }
    return relationships.get(type, specialityColumnValue);
  }

  public Provider startNewRelationship(Person person, HealthRecord.EncounterType type,
                                       String speciality, long time) {
    Provider provider = Provider.findService(person, type, speciality, time);
    if (provider == null && Provider.USE_HOSPITAL_AS_DEFAULT) {
      // Default to Hospital
      provider = Provider.findService(person, HealthRecord.EncounterType.INPATIENT, speciality, time);
    }

    String specialityColumnValue = speciality;
    if(specialityColumnValue == null) {
      specialityColumnValue = NULL_PLACEHOLDER;
    }

    relationships.put(type, specialityColumnValue, provider);

    return provider;
  }

  public Provider getWellnessProvider() {
    return get(HealthRecord.EncounterType.WELLNESS, null);
  }

  public void forceRelationship(HealthRecord.EncounterType type, String speciality,
                                Provider provider) {
    String specialityColumnValue = speciality;
    if(specialityColumnValue == null) {
      specialityColumnValue = NULL_PLACEHOLDER;
    }
    relationships.put(type, specialityColumnValue, provider);
  }

  public void resetRelationship(HealthRecord.EncounterType type, String speciality) {
    String specialityColumnValue = speciality;
    if(specialityColumnValue == null) {
      specialityColumnValue = NULL_PLACEHOLDER;
    }
    relationships.remove(type, specialityColumnValue);
  }

  public void reset() {
    relationships.clear();
  }
}