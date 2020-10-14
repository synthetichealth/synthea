package org.mitre.synthea.input;

import java.util.List;

/**
 * A grouping of FixedRecords that represents a single individual. FixedRecords provide demographic
 * information and the grouping can be used to capture variation that may happen across different
 * provider locations.
 */
public class FixedRecordGroup {
  public List<FixedRecord> records;
  public int count;
  public int linkId;

  /**
   * Pulls the first valid birthdate from the list of FixedRecords.
   * @return long valid birthdate
   */
  public long getValidBirthdate() {
    for (int i = 0; i < this.records.size(); i++) {
      try {
        return this.records.get(i).getBirthDate();
      } catch (java.time.DateTimeException | java.lang.NullPointerException
          | java.lang.IllegalArgumentException dontcare) {
        // Do nothing if the current fixed record does not have a valid birthdate.
      }
    }
    throw new RuntimeException("No valid birthdate for: " + this.records.get(0).firstName + " "
        + this.records.get(0).lastName);
  }

  /**
   * Pulls the first valid city from the list of FixedRecords.
   * @return String safe city name
   */
  public String getSafeCity() {
    for (int i = 0; i < this.records.size(); i++) {
      String safeCity = this.records.get(i).getSafeCity();
      if (safeCity != null) {
        return safeCity;
      }
    }
    throw new RuntimeException("ERROR: No valid city for " + this.records.get(0).firstName + " "
        + this.records.get(0).lastName + ".");
  }

  /**
   * Returns a FixedRecord which has a recordDates range that includes the given year.
   * @return FixedRecord that meets the daterange of the given year
   */
  public FixedRecord checkAddressUpdate(int currentYear) {
    for(FixedRecord record : records) {
      // Pull out the 2 years from the current fixed record.
      String years[] = record.recordDates.split("-");
      // Check if the current year is between the years in the current fixed record.
      if(currentYear >= Integer.parseInt(years[0]) && currentYear <= Integer.parseInt(years[1])){
        return record;
      }
    }
    throw new RuntimeException("ERROR: Invalid input record dates for " + this.records.get(0).firstName + " " + this.records.get(0).lastName + ".");
  }
}
