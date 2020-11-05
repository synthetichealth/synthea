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
  public int linkId;
  public int year;

  public FixedRecordGroup(FixedRecord seedRecord){
    year = 0;
    this.seedRecord = seedRecord;
    this.variantRecords = new ArrayList<FixedRecord>();
  }

  public void addVariantRecord(FixedRecord variantRecord){
    this.variantRecords.add(variantRecord);
  }

  /**
   * Returns the valid birthdate in the seed record
   * @return long valid birthdate
   */
  public long getValidBirthdate() {
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
   * Pulls the first valid city from the list of FixedRecords.
   * @return String safe city name
   */
  public String getSafeCity() {
    String safeCity = seedRecord.getSafeCity();
    if (safeCity != null) {
      return safeCity;
    }
    throw new RuntimeException("ERROR: No valid city for " + seedRecord.firstName + " "
        + seedRecord.lastName + ".");
  }

  /**
   * Returns a FixedRecord which has a recordDates range that includes the given year.
   * @return FixedRecord that meets the daterange of the given year. Returns null if the current year's record has already been accessed.
   */
  public FixedRecord getCurrentFixedRecord(int currentYear) {
    if(year < currentYear){
      year = currentYear;
      for(FixedRecord record : variantRecords) {
        // Check if the current year is between the years in the current fixed record.
        if(record.checkRecordDates(currentYear)){
          return record;
        }
      }
    throw new RuntimeException("ERROR: Invalid input record dates for " + this.seedRecord.firstName + " " + this.seedRecord.lastName + ".");
    } else {
      return null;
    }
  }

  public int getRecordCount() {
    return this.variantRecords.size();
  }

}