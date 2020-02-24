package org.mitre.synthea.input;

import java.util.List;

/**
 * A grouping of FixedRecords that represents a single individual. FixedRecords provide demographic
 * information and the grouping can be used to capture variation that may happen across different
 * provider locations.
 */
public class RecordGroup {
  public List<FixedRecord> records;
  public int count;

  public long getValidBirthdate(int index) {
    FixedRecord fr = this.records.get(index);
    try {
      return fr.getBirthDate();
    } catch (java.time.DateTimeException|java.lang.NullPointerException|java.lang.IllegalArgumentException e) {
      for (int i = 0; i < this.records.size(); i++) {
        try {
          return this.records.get(i).getBirthDate();
        } catch (java.time.DateTimeException|java.lang.NullPointerException|java.lang.IllegalArgumentException dontcare) {
          // do nothing
        }
      }
    }
    throw new RuntimeException("No valid birthdate for: " + fr.firstName + " " + fr.lastName);
  }

  public String getSafeCity(int index) {
    FixedRecord fr = this.records.get(index);
    String safeCity = fr.getSafeCity();
    if (safeCity != null && !safeCity.isBlank() && !safeCity.equalsIgnoreCase("unknown")) {
      return safeCity;
    }
    for (int i = 0; i < this.records.size(); i++) {
      safeCity = this.records.get(i).getSafeCity();
      if (safeCity != null && !safeCity.isBlank() && !safeCity.equalsIgnoreCase("unknown")) {
        return safeCity;
      }
    }
    throw new RuntimeException("No valid city for: " + fr.firstName + " " + fr.lastName);
  }
}
