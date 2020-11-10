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
  public String getCurrentCity(int currentYear) {
    String city = getCurrentFixedRecord(currentYear).getSafeCity();
    if (city != null) {
      return city;
    }
    return this.getSeedCity();
  }

  /**
   * Returns a FixedRecord which has a recordDates range that includes the given year.
   * @return FixedRecord that meets the daterange of the given year.
   */
  public FixedRecord getCurrentFixedRecord(int currentYear) {
    FixedRecord currentRecordCandidate = variantRecords.get(0);
    for(FixedRecord record : variantRecords) {
      // Check if the start date of the current record is the highest yet that predates the given year.
      if(record.addressStartDate >= currentYear && record.addressStartDate <= currentRecordCandidate.addressStartDate){
        currentRecordCandidate = record;
      }
    }
    return currentRecordCandidate;
  }

  public int getRecordCount() {
    return this.variantRecords.size();
  }

}