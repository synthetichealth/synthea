package org.mitre.synthea.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.world.agents.Person;

/**
 * A grouping of FixedRecords that represents an individual's seed record and
 * potential variant records at a certain sequence of time. The seed record is
 * the ground truth during the person's time with this fixed record group while
 * the variants represent variants that can be used as biographical and
 * demographic information at a given time. This fixed record group also tracks
 * the current variant record for the person to use when creating new health
 * records from their demographics.
 */
public class FixedRecordGroup implements Comparable<FixedRecordGroup> {

  // The seed record of this record group from which the variants are created.
  private final FixedRecord seedRecord;
  // The list of variant records for this record group.
  public final List<FixedRecord> variantRecords;
  // The current variant record of this group. Updates by incrementing and is the
  // index of the variantRecords to use.
  private int currentVariantRecord;
  // The sequence place of this fixed record group.
  private final int fixedRecordGroupSequencePlace;

  /**
   * Create the FixedRecordGroup for a person based on a seed record.
   * 
   * @param seedRecord the seed record of the person for this fixed record group
   *                   sequence.
   */
  public FixedRecordGroup(FixedRecord seedRecord) {
    this.seedRecord = seedRecord;
    this.variantRecords = new ArrayList<FixedRecord>();
    this.fixedRecordGroupSequencePlace = seedRecord.addressSequence;
  }

  /**
   * Adds a variant record to the record group.
   * 
   * @param variantRecord the variant record to be added.
   */
  void addVariantRecord(FixedRecord variantRecord) {
    this.variantRecords.add(variantRecord);
  }

  /**
   * Returns the valid birthdate in the seed record.
   * 
   * @return long valid birthdate.
   */
  public long getSeedBirthdate() {
    try {
      return this.seedRecord.getBirthDate();
    } catch (java.time.DateTimeException | java.lang.NullPointerException
        | java.lang.IllegalArgumentException dontcare) {
      // Do nothing if the current fixed record does not have a valid birthdate.
    }
    throw new RuntimeException("No valid birthdate for: " + this.seedRecord.firstName + " " + this.seedRecord.lastName
        + "'s seed record id " + seedRecord.recordId + ".");
  }

  /**
   * Returns the city associated with the seed record.
   * 
   * @return String safe city name
   */
  public String getSeedCity() {
    // String safeCity = seedRecord.getValidCity(this);
    String safeCity = seedRecord.city;
    if (safeCity == null || safeCity == "") {
      throw new RuntimeException("ERROR: No valid city for " + seedRecord.firstName + " " + seedRecord.lastName
          + "'s seed record id " + seedRecord.recordId + ".");
    }
    return safeCity;
  }

  /**
   * Returns the current year's record city. If it is an invalid city, returns the
   * seed's city.
   * 
   * @return String safe city name
   */
  public String getSafeCurrentCity() {
    return this.getCurrentRecord().getValidCity(this);
  }

  /**
   * Returns the index of the earlist FixedRecord in the RecordGroup.
   * 
   * @return the index of the current record.
   */
  public FixedRecord getCurrentRecord() {
    return this.variantRecords.get(this.currentVariantRecord);
  }

  /**
   * Returns the number of variant records in the record group.
   * 
   * @return the number of variant records in the record group.
   */
  public int getRecordCount() {
    return this.variantRecords.size();
  }

  /**
   * Gets the seed id of this fixed record group.
   * 
   * @return
   */
  public String getSeedId() {
    return this.seedRecord.recordId;
  }

  /**
   * Gets the household role of this fixed record group.
   * 
   * @return
   */
  public String getHouseholdRole() {
    return this.seedRecord.householdRole;
  }

  /**
   * Returns the household id of this fixed record group.
   * 
   * @return The household id of this fixed record group.
   */
  public String getHouseholdId() {
    return this.seedRecord.householdId;
  }

  /**
   * Iterates to the next variant record in the record group.
   * 
   * @return The current variant record the record group was updated to.
   */
  public FixedRecord updateCurrentVariantRecord() {
    this.currentVariantRecord++;
    if (this.currentVariantRecord >= this.variantRecords.size()) {
      this.currentVariantRecord = 0;
    }
    return this.variantRecords.get(this.currentVariantRecord);
  }

  /**
   * Overwrites the given person's address with this fixed record group's current
   * variant record address information.
   * 
   * @param person    The person whose address to overwrite.
   * @param generator The generator to use to extract a valid city.
   * @return boolean Whether the address was changed.
   */
  public boolean overwriteAddressWithCurrentVariantRecord(Person person, Generator generator) {
    return this.getCurrentRecord().overwriteAddress(person, generator);
  }

  /**
   * Returns the current variant record attributes of this fixed record group.
   * 
   * @return The current variant record attributes of this fixed record group.
   */
  public Map<String, Object> getCurentVariantRecordAttributes() {
    return this.getCurrentRecord().getFixedRecordAttributes();
  }

  /**
   * Returns the seed record attributes of this fixed record group.
   * 
   * @return The seed record attributes of this fixed record group.
   */
  public Map<String, Object> getSeedRecordAttributes() {
    return this.seedRecord.getFixedRecordAttributes();
  }

  /**
   * Overwrites the given person's address with this fixed record group's seed
   * record address information.
   * 
   * @param person    The person whose address to overwrite.
   * @param generator The generator to use to extract a valid city.
   * @return boolean Whether the address was changed.
   */
  public boolean overwriteAddressWithSeedRecord(Person person, Generator generator) {
    return this.seedRecord.overwriteAddress(person, generator);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof FixedRecordGroup)) {
      return false;
    }
    FixedRecordGroup that = (FixedRecordGroup) o;
    return (this.seedRecord.equals(that.seedRecord));
  }

  @Override
  public int hashCode() {
    return this.seedRecord.hashCode();
  }

  @Override
  public String toString() {
    return "Fixed Record Group with Seed Id: [" + this.seedRecord.recordId + "]";
  }

  @Override
  public int compareTo(FixedRecordGroup other) {
    return Integer.compare(this.fixedRecordGroupSequencePlace, other.fixedRecordGroupSequencePlace);
  }

  /**
   * Returns the birth year of this fixed record group.
   */
  public int getSeedBirthYear() {
    return Integer.parseInt(this.seedRecord.birthYear);
  }

  /**
   * Sets the initial variant record index of this fixed record gropu using the
   * given random.
   * 
   * @param random
   */
  public void setInitialVariantRecord(Random random) {
    this.currentVariantRecord = random.nextInt(this.variantRecords.size());
  }
}