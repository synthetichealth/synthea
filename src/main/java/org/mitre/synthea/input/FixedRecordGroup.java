package org.mitre.synthea.input;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.text.WordUtils;
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
   * Returns the city associated with the seed record in a valid format.
   * 
   * @return String safe city name
   */
  public String getSeedCity() {
    String safeCity = seedRecord.city;
    if (safeCity == null || safeCity == "") {
      throw new RuntimeException("ERROR: No valid seed city for " + seedRecord.firstName + " " + seedRecord.lastName
          + " with seed record id " + seedRecord.recordId + ".");
    }
    safeCity = WordUtils.capitalize(safeCity.toLowerCase());
    return safeCity;
  }

  /**
   * Returns the state associated with the seed record in a valid format.
   * 
   * @return String safe city name
   */
  public String getSeedState() {
    String rawState = this.seedRecord.state;
    if (STATE_MAP.containsKey(rawState)) {
      return STATE_MAP.get(rawState);
    }
    return rawState;
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
    if (this.variantRecords.size() < 1) {
      throw new RuntimeException("Trying to set the initial variant record with " + this.variantRecords.size()
          + " variant records. Seed ID: " + this.seedRecord.recordId + ".");
    }
    this.currentVariantRecord = random.nextInt(this.variantRecords.size());
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
   * Map of the conversion from state abbreviations to full names.
   */
  public static final Map<String, String> STATE_MAP;
  static {
    STATE_MAP = new HashMap<String, String>();
    STATE_MAP.put("AL", "Alabama");
    STATE_MAP.put("AK", "Alaska");
    STATE_MAP.put("AB", "Alberta");
    STATE_MAP.put("AZ", "Arizona");
    STATE_MAP.put("AR", "Arkansas");
    STATE_MAP.put("BC", "British Columbia");
    STATE_MAP.put("CA", "California");
    STATE_MAP.put("CO", "Colorado");
    STATE_MAP.put("CT", "Connecticut");
    STATE_MAP.put("DE", "Delaware");
    STATE_MAP.put("DC", "District Of Columbia");
    STATE_MAP.put("FL", "Florida");
    STATE_MAP.put("GA", "Georgia");
    STATE_MAP.put("GU", "Guam");
    STATE_MAP.put("HI", "Hawaii");
    STATE_MAP.put("ID", "Idaho");
    STATE_MAP.put("IL", "Illinois");
    STATE_MAP.put("IN", "Indiana");
    STATE_MAP.put("IA", "Iowa");
    STATE_MAP.put("KS", "Kansas");
    STATE_MAP.put("KY", "Kentucky");
    STATE_MAP.put("LA", "Louisiana");
    STATE_MAP.put("ME", "Maine");
    STATE_MAP.put("MB", "Manitoba");
    STATE_MAP.put("MD", "Maryland");
    STATE_MAP.put("MA", "Massachusetts");
    STATE_MAP.put("MI", "Michigan");
    STATE_MAP.put("MN", "Minnesota");
    STATE_MAP.put("MS", "Mississippi");
    STATE_MAP.put("MO", "Missouri");
    STATE_MAP.put("MT", "Montana");
    STATE_MAP.put("NE", "Nebraska");
    STATE_MAP.put("NV", "Nevada");
    STATE_MAP.put("NB", "New Brunswick");
    STATE_MAP.put("NH", "New Hampshire");
    STATE_MAP.put("NJ", "New Jersey");
    STATE_MAP.put("NM", "New Mexico");
    STATE_MAP.put("NY", "New York");
    STATE_MAP.put("NF", "Newfoundland");
    STATE_MAP.put("NC", "North Carolina");
    STATE_MAP.put("ND", "North Dakota");
    STATE_MAP.put("NT", "Northwest Territories");
    STATE_MAP.put("NS", "Nova Scotia");
    STATE_MAP.put("NU", "Nunavut");
    STATE_MAP.put("OH", "Ohio");
    STATE_MAP.put("OK", "Oklahoma");
    STATE_MAP.put("ON", "Ontario");
    STATE_MAP.put("OR", "Oregon");
    STATE_MAP.put("PA", "Pennsylvania");
    STATE_MAP.put("PE", "Prince Edward Island");
    STATE_MAP.put("PR", "Puerto Rico");
    STATE_MAP.put("QC", "Quebec");
    STATE_MAP.put("RI", "Rhode Island");
    STATE_MAP.put("SK", "Saskatchewan");
    STATE_MAP.put("SC", "South Carolina");
    STATE_MAP.put("SD", "South Dakota");
    STATE_MAP.put("TN", "Tennessee");
    STATE_MAP.put("TX", "Texas");
    STATE_MAP.put("UT", "Utah");
    STATE_MAP.put("VT", "Vermont");
    STATE_MAP.put("VI", "Virgin Islands");
    STATE_MAP.put("VA", "Virginia");
    STATE_MAP.put("WA", "Washington");
    STATE_MAP.put("WV", "West Virginia");
    STATE_MAP.put("WI", "Wisconsin");
    STATE_MAP.put("WY", "Wyoming");
    STATE_MAP.put("YT", "Yukon Territory");
  }

}