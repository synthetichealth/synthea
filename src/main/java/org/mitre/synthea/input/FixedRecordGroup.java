package org.mitre.synthea.input;

import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.helpers.Utilities;

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
    this.currentVariantRecord = this.getEarliestRecord();
  }

  public void addVariantRecord(FixedRecord variantRecord){
    this.variantRecords.add(variantRecord);
  }

  public void setVariantRecordYearRanges(){
    for(int i = 0; i < variantRecords.size(); i++) {
      int nextAddressStartDate = this.getNextAddressStartDate(this.variantRecords.get(i).addressStartDate);
      this.variantRecords.get(i).addressEndDate = nextAddressStartDate-1;
    }

  }

  private int getNextAddressStartDate(int currentAddressStartDate) {
    // Create a list of the address start dates after the given date.
    List<Integer> addressStartDates = new ArrayList<Integer>();
    for(int i = 0; i < variantRecords.size(); i++) {
      if(this.variantRecords.get(i).addressStartDate > currentAddressStartDate) {
        addressStartDates.add(this.variantRecords.get(i).addressStartDate);
      }
    }
    // If there are no address start dates, this is the last date. return the current year + 5.
    if(addressStartDates.size() == 0){
      return Utilities.getYear(System.currentTimeMillis()) + 5;
    }
    // Return the smallest address start date.
    return addressStartDates.stream().min((i, j) -> i.compareTo(j)).get();
  }

  /**
   * Returns the valid birthdate in the seed record
   * 
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
  public boolean updateCurrentRecord(int currentYear) {
    for(int i = 0; i < variantRecords.size(); i++) {
      FixedRecord currentRecord = variantRecords.get(i);
      // System.out.println(currentRecord.addressStartDate + "-" + currentRecord.addressEndDate);
      // Check if the the current year falls within the current record date range.
      if(currentRecord.addressStartDate <= currentYear && currentYear <= currentRecord.addressEndDate){
        if(i != this.currentVariantRecord){
          // The record has changed.
          this.currentVariantRecord = i;
          return true;
        } else {
          return false;
        }
      }
    }
    return false;
  }

  /**
   * Returns the index of the earlist FixedRecord in the RecordGroup.
   * @return Earliest FixedRecord index.
   */
  private int getEarliestRecord() {
    int currentEarliest = 0;
    for(int i = 0; i < variantRecords.size(); i++) {
      if(this.variantRecords.get(i).addressStartDate < this.variantRecords.get(currentEarliest).addressStartDate) {
        currentEarliest = i;
      }
    }
    return currentEarliest;
  }

  public FixedRecord getCurrentRecord() {
    if(this.currentVariantRecord == -1) {
      throw new RuntimeException("Current year's record must be updated and set before accessing it.");
    }
    return this.variantRecords.get(this.currentVariantRecord);
  }

  public int getRecordCount() {
    return this.variantRecords.size();
  }

}