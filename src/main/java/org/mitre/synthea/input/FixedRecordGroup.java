package org.mitre.synthea.input;

import java.util.ArrayList;
import java.util.List;

/**
 * A grouping of FixedRecords that represents a single individual. FixedRecords
 * provide demographic information and the grouping can be used to capture
 * variation that may happen across different provider health records.
 */
public class FixedRecordGroup implements Comparable<FixedRecordGroup>{
  // The seed record of this record group from which the variants are created.
  public FixedRecord seedRecord;
  // The list of variant records for this record group.
  public List<FixedRecord> variantRecords;
  // The current variant record of this group. Updates be incrementing and is the index of the variantRecords to use.
  public int currentVariantRecord;
  // The sequence place of this fixed record group.
  private final int fixedRecordGroupSequencePlace;
  // Whether this fixed record group has been updated and used yet.
  private boolean hasBeenUpdated;

  /**
   * Create the FixedRecordGroup for a person based on a seed record.
   * 
   * @param seedRecord the seed record of the person.
   */
  public FixedRecordGroup(FixedRecord seedRecord) {
    this.seedRecord = seedRecord;
    this.variantRecords = new ArrayList<FixedRecord>();
    this.currentVariantRecord = 0;
    this.fixedRecordGroupSequencePlace = seedRecord.addressSequence;
    this.hasBeenUpdated = false;
  }

  /**
   * Adds a variant record to the record group.
   * 
   * @param variantRecord the record to be added.
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
    String safeCity = seedRecord.getCity();
    if (safeCity != null) {
      return safeCity;
    }
    throw new RuntimeException("ERROR: No valid city for " + seedRecord.firstName + " " + seedRecord.lastName
        + "'s seed record id " + seedRecord.recordId + ".");
  }

  /**
   * Returns the current year's record city. If it is an invalid city, returns the
   * seed's city.
   * 
   * @return String safe city name
   */
  public String getSafeCurrentCity() {
    String city = getCurrentRecord().getCity();
    if (city != null) {
      return city;
    }
    return this.getSeedCity();
  }

  /**
   * Returns a FixedRecord which has a recordDates range that includes the given
   * year.
   * 
   * @return FixedRecord that meets the daterange of the given year.
   */
  // public boolean updateCurrentRecord(int currentYear) {
  //   for (int i = 0; i < variantRecords.size(); i++) {
  //     FixedRecord currentRecord = variantRecords.get(i);
  //     // Check if the the current year falls within the current record date range.
  //     // if (currentRecord.addressStartDate <= currentYear && currentYear <= currentRecord.addressEndDate) {
  //       if (i != this.currentVariantRecord) {
  //         // The record has changed.
  //         this.currentVariantRecord = i;
  //         return true;
  //       } else {
  //         return false;
  //       }
  //     // }
  //   }
  //   return false;
  // }

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
   * @return
   */
  public String getSeedId() {
    return this.seedRecord.recordId;
  }

  /**
   * Gets the household role of this fixed record group.
   * @return
   */
  public String getHouseholdRole() {
    return this.seedRecord.householdRole;
  }

  public String getHouseholdId() {
    return this.seedRecord.householdId;
  }

  @Override
  public String toString(){
    return this.seedRecord.recordId;
  }

  @Override
  public int compareTo(FixedRecordGroup other) {
    return Integer.compare(this.fixedRecordGroupSequencePlace, other.fixedRecordGroupSequencePlace);
  }

  /**
   * Returns whether this fixed record group has just been updated.
   * @return
   */
  public boolean hasJustBeenUpdated() {
    if(this.hasBeenUpdated){
      this.hasBeenUpdated = false;
      return true;
    }
    return false;
  }

  /**
   * Iterates to the next variant record in the record group.
   * @return The current variant record the record group was updated to.
   */
  public FixedRecord updateCurrentVariantRecord() {
    this.currentVariantRecord++;
    if(this.currentVariantRecord >= this.variantRecords.size()) {
      this.currentVariantRecord = 0;
    }
    return this.variantRecords.get(this.currentVariantRecord);
  }
}