package org.mitre.synthea.input;

import com.google.gson.Gson;

import com.google.gson.reflect.TypeToken;

import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.lang.reflect.Type;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A grouping of FixedRecords that represents a single individual. FixedRecords
 * provide demographic information and the grouping can be used to capture
 * variation that may happen across different provider locations.
 */
public class FixedRecordGroupManager {

  // Imported households.
  public List<Household> householdsList;

  // Map to track households by their ID. String is the household id, Household is
  // the houshold object
  private Map<String, Household> householdsMap;

  public FixedRecordGroupManager() {
    this.householdsMap = new HashMap<String, Household>();
  }

  /**
   * Returns the population size of the imported households and fixed records.
   * 
   * @return int The population size.
   */
  public int getPopulationSize() {
    return this.householdsMap.values().stream().mapToInt(h -> h.householdSize()).sum();
  }

  /**
   * Gets the record group for the given household id and household role.
   * 
   * @param householdId   The Id of the household to check in.
   * @param householdRole The role of the person in the household to check for.
   * @return FixedRecordGroup The FixedRecordGroup from the given inputs.
   */
  public FixedRecordGroup getRecordGroup(String householdId, String householdRole) {
    return this.householdsMap.get(householdId).getRecordGroupFor(householdRole);
  }

  /**
   * Imports the fixed demographics records file when using fixed patient
   * demographics.
   * 
   * @return the fixed record manager.
   */
  public static FixedRecordGroupManager importFixedDemographicsFile(File filePath) {
    Gson gson = new Gson();
    Type jsonType = new TypeToken<List<Household>>() {
    }.getType();
    FixedRecordGroupManager fixedRecordGroupManager = new FixedRecordGroupManager();
    try {
      System.out.println("Loading fixed patient demographic records and households file: " + filePath);
      fixedRecordGroupManager.householdsList = gson.fromJson(new FileReader(filePath), jsonType);
    } catch (FileNotFoundException e) {
      throw new RuntimeException("Couldn't open the fixed patient demographics records file", e);
    }
    fixedRecordGroupManager.initializeHouseholds();
    return fixedRecordGroupManager;
  }

  /**
   * Initializes the households for the manager based on the list of imported
   * households.
   */
  private void initializeHouseholds() {
    for (Household household : this.householdsList) {
      String householdId = household.getHouseholdId();
      this.householdsMap.put(householdId, household.initializeHousehold());
    }
  }

  /**
   * Checks to update each household's memebers' current fixed record groups.
   * 
   * @param currentYear
   */
  public void checkToUpdateHouseholdAddresses(int currentYear) {
    this.householdsMap.values().forEach(h -> h.updateCurrentFixedRecordGroup(currentYear));
  }

  /**
   * Returns the record group at the given index.
   * 
   * @param index The index of the record group to find.
   * @return FixedRecordGroup The FixedRecordGroup at that index.
   */
  public FixedRecordGroup getNextRecordGroup(int index) {
    List<FixedRecordGroup> fullRecordGroupList = new ArrayList<FixedRecordGroup>();

    for (Household hh : this.householdsMap.values()) {
      for (FixedRecordGroup frg : hh.getInitialFixedRecordGroupForEachMember()) {
        fullRecordGroupList.add(frg);
      }
    }
    return fullRecordGroupList.get(index);
  }

  public Household getHousehold(String householdId) {
    return this.householdsMap.get(householdId);
  }

  /**
   * Updates the person's address information from their Fixed Record that matches
   * the current year.
   * 
   * @param person    The person to use.
   * @param time      The time to update the records at.
   * @param generator The generator used to extract the new address location.
   */
  public void updateFixedDemographicRecord(Person person, long time, Generator generator) {
    // Check if the person's fixed record has been updated, meaning that their
    // health record, provider, and address should also update.
    FixedRecordGroup frg = (FixedRecordGroup) person.attributes.get(Person.RECORD_GROUP);

    // if (frg.updateCurrentRecord(Utilities.getYear(time))) {
    if (frg.hasJustBeenUpdated()) {
      // Pull the newly updated fixedRecord.
      // FixedRecord fr = frg.getCurrentRecord();
      FixedRecord fr = frg.seedRecord;
      fr.overwriteAddress(person, generator);
      person.attributes.putAll(fr.getFixedRecordAttributes());
      /*
       * Force update the person's provider based on their new record. This is
       * required so that a new health record is made for the start date of the fixed
       * record which impacts the provider, care location, timing, and any change of
       * address.
       */
      person.forceNewProvider(HealthRecord.EncounterType.WELLNESS, Utilities.getYear(time));
      person.record = person.getHealthRecord(
          person.getProvider(HealthRecord.EncounterType.WELLNESS, System.currentTimeMillis()),
          System.currentTimeMillis());
    }
  }

  public void addPersonToHousehold(Person person, String householdRole) {
    // Because people are sometimes re-simulated, we must make sure they
    // have not already been added to the household.
    Household personHousehold = this.householdsMap.get(person.attributes.get(Person.HOUSEHOLD));
    if (!personHousehold.includesPerson(person)) {
      personHousehold.addMember(person, householdRole);
    }
  }
}

// OLD METHODS

/**
 * Creates the record groups based on the imported records.
 */
// public void createRecordGroups() {
// this.recordGroups = new HashMap<Integer, FixedRecordGroup>();
// // Initialize with the seed records.
// for (FixedRecord seedRecord : seedRecords) {
// this.recordGroups.put(Integer.parseInt(seedRecord.recordId),
// new FixedRecordGroup(seedRecord));
// }
// // Populate the seeded record groups with variant records.
// for (FixedRecord variantRecord : variantRecords) {
// if (!this.recordGroups.containsKey(Integer.parseInt(variantRecord.seedID))) {
// throw new RuntimeException("ERROR: Variant record seed ID " +
// variantRecord.seedID
// + " does not exist in seed records.");
// }
// this.recordGroups.get(Integer.parseInt(variantRecord.seedID))
// .addVariantRecord(variantRecord);
// }
// // Set the date ranges of the fixed records.
// for (FixedRecordGroup recordGroup : this.recordGroups.values()) {
// recordGroup.setVariantRecordYearRanges();
// }
// }