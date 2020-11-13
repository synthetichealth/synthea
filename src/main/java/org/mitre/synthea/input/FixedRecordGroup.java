package org.mitre.synthea.input;

import java.util.ArrayList;
import java.util.List;

/**
 * A grouping of FixedRecords that represents a single individual. FixedRecords provide demographic
 * information and the grouping can be used to capture variation that may happen across different
 * provider locations.
 */
public class FixedRecordGroup {
  public FixedRecord seedRecord;
  public List<FixedRecord> variantRecords;
  public int currentVariantRecord;
  public int linkId;

  public FixedRecordGroup(FixedRecord seedRecord){
    this.seedRecord = seedRecord;
    this.variantRecords = new ArrayList<FixedRecord>();
    this.currentVariantRecord = -1;
  }

  public void addVariantRecord(FixedRecord variantRecord){
    this.variantRecords.add(variantRecord);
  }

  /**
   * Returns the valid birthdate in the seed record
   * @return long valid birthdate
   */
  public long getSeedBirthdate() {
    try {
      return this.seedRecord.getBirthDate();
    } catch (java.time.DateTimeException | java.lang.NullPointerException
        | java.lang.IllegalArgumentException dontcare) {
      // Do nothing if the current fixed record does not have a valid birthdate.
    }
    throw new RuntimeException("No valid birthdate for: " + this.seedRecord.firstName + " "
        + this.seedRecord.lastName);
  }

  /**
   * Returns the city associated with the seed record.
   * @return String safe city name
   */
  public String getSeedCity() {
    String safeCity = seedRecord.getSafeCity();
    if (safeCity != null) {
      return safeCity;
    }
    throw new RuntimeException("ERROR: No valid city for " + seedRecord.firstName + " "
        + seedRecord.lastName + ".");
  }

  /**
   * Returns the current year's record city. If it is an invalid city, returns the seed's city.
   * @return String safe city name
   */
  public String getSafeCurrentCity() {
    String city = getCurrentRecord().getSafeCity();
    if (city != null) {
      return city;
    }
    return this.getSeedCity();
  }

  /**
   * Returns a FixedRecord which has a recordDates range that includes the given year.
   * @return FixedRecord that meets the daterange of the given year.
   */
  // public FixedRecord getCurrentFixedRecord(int currentYear) {
  //   FixedRecord currentRecordCandidate = variantRecords.get(0);
  //   for(FixedRecord currentRecord : variantRecords) {
  //     // Check if the start date of the current record is the highest yet that predates the given year.
  //     if(currentRecord.addressStartDate >= currentYear && currentRecord.addressStartDate <= currentRecordCandidate.addressStartDate){
  //       currentRecordCandidate = currentRecord;
  //     }
  //   }
  //   return currentRecordCandidate;
  // }

  public boolean updateCurrentRecord(int currentYear) {
    int currentRecordCandidate = 0;
    for(int i = 0; i < variantRecords.size(); i++) {
      FixedRecord currentRecord = variantRecords.get(i);
      // Check if the start date of the current record is the highest yet that predates the given year.
      if(currentRecord.addressStartDate >= currentYear && currentRecord.addressStartDate <= variantRecords.get(currentRecordCandidate).addressStartDate){
        currentRecordCandidate = i;
      }
    }
    if(currentRecordCandidate != this.currentVariantRecord) {
      // The current record has changed, update it and return true.
      this.currentVariantRecord = currentRecordCandidate;
      return true;
    }
    else {
      // No update made, return false.
      return false;
    }
  }

  public FixedRecord getCurrentRecord(){
    if(this.currentVariantRecord == -1) {
      throw new RuntimeException("Current year's record must be updated and set before accessing it.");
    }
    return this.variantRecords.get(this.currentVariantRecord);
  }

  public int getRecordCount() {
    return this.variantRecords.size();
  }

}