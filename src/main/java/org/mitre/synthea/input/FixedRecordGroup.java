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
   * @return
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
   * @return
   */
  public String getSafeCity() {
    for (int i = 0; i < this.records.size(); i++) {
      String safeCity = this.records.get(i).getSafeCity();
      if (safeCity != null) {
        return safeCity;
      }
    }
    throw new RuntimeException("No valid city for: " + this.records.get(0).firstName + " "
        + this.records.get(0).lastName);
  }
}
