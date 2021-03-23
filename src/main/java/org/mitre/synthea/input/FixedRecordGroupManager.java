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
import java.util.UUID;

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

  /**
   * Constructor for Fixed Record Group Manager.
   */
  private FixedRecordGroupManager() {
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
   * @return The newly created fixed record manager.
   */
  public static FixedRecordGroupManager importFixedDemographicsFile(File filePath) {
    Gson gson = new Gson();
    Type jsonType = new TypeToken<List<Household>>() {
    }.getType();
    FixedRecordGroupManager fixedRecordGroupManager = new FixedRecordGroupManager();
    try {
      System.out.println("Loading fixed patient demographic records and households file <" + filePath + ">");
      fixedRecordGroupManager.householdsList = gson.fromJson(new FileReader(filePath), jsonType);
    } catch (FileNotFoundException e) {
      throw new RuntimeException("Couldn't open the fixed patient demographics records file", e);
    }
    long householdsSeed = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    fixedRecordGroupManager.initializeHouseholds(householdsSeed);
    System.out.println(
        "Fixed patient demographic records and households file <" + filePath + "> loaded and households initialized!");
    return fixedRecordGroupManager;
  }

  /**
   * Initializes the households for the manager based on the list of imported
   * households.
   * 
   * @param householdsSeed The seed to initialize the households with.
   */
  private void initializeHouseholds(long householdsSeed) {
    for (Household household : this.householdsList) {
      String householdId = household.seedRecords.get(0).householdId;
      this.householdsMap.put(householdId, household.initializeHousehold(householdsSeed));
    }
  }

  /**
   * Checks to update the household of this person's fixed record group based on
   * address sequences.
   * 
   * @param currentYear
   */
  public boolean checkToUpdateHouseholdAddressFor(Person person, int currentYear) {
    return this.householdsMap.get(person.attributes.get(Person.HOUSEHOLD)).updateCurrentFixedRecordGroupFor(currentYear, person);
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

  /**
   * Returns the Household object with the given id.
   * 
   * @param householdId The household id.
   * @return The Household object with the given id.
   */
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
    FixedRecordGroup frg = Generator.fixedRecordGroupManager.getRecordGroupFor(person);

    // If the person's fixed record group has just been updated, then force a new
    // encounter provider with a new variant record corresponding to the new seed
    // record.
    // if (frg.hasJustBeenUpdated()) {

      System.out.println(person.attributes.get(Person.NAME) + " frg has just been updated. _ " + frg.getHouseholdRole() + " _ " +  frg.getSeedId());

      // Overwrite the person's address information with the new current variant
      // record of the new fixed record group.
      frg.overwriteAddressWithCurrentVariantRecord(person, generator);
      // Overwrite the person's biographical information with the new current variant
      // record of the new fixed record group.
      person.attributes.putAll(frg.getCurentVariantRecordAttributes());

      /*
       * Force update the person's provider based on their new seed record. and fixed
       * record group. This is required so that a new health record is made for the
       * start date of the new primary seed record and fixed record group which
       * impacts the provider, care location, timing, and any change of address.
       */
      person.forceNewProvider(HealthRecord.EncounterType.WELLNESS, Utilities.getYear(time));
      // Create a new health record with the person's new variant record attributes.
      person.record = person.getHealthRecord(
          person.getProvider(HealthRecord.EncounterType.WELLNESS, System.currentTimeMillis()),
          System.currentTimeMillis());
      // Reset the person's attributes to the seed record ones (because that's their
      // true attributes).
      person.attributes.putAll(frg.getSeedRecordAttributes());
      frg.overwriteAddressWithSeedRecord(person, generator);
    // }
  }

  /**
   * Adds the given person with the given household role to their household.
   */
  public void addPersonToHousehold(Person person, String householdRole) {
    // Because people are sometimes re-simulated, we must make sure they
    // have not already been added to the household.
    Household personHousehold = this.householdsMap.get(person.attributes.get(Person.HOUSEHOLD));
    // if (!personHousehold.includesPerson(person)) {
    personHousehold.addMember(person, householdRole);
    // }
  }

  /**
   * Updates the given person's current variant record.
   * 
   * @param person The person whose current variant record to update.
   * @return The newly updated to variant record.
   */
  public FixedRecord updatePersonVariantRecord(Person person) {
    Household household = this.householdsMap.get(person.attributes.get(Person.HOUSEHOLD));
    return household.updatePersonVariantRecord(person);
  }

  public FixedRecordGroup getRecordGroupFor(Person person) {
    return this.householdsMap.get(person.attributes.get(Person.HOUSEHOLD)).getRecordGroupFor(person);
  }

  /**
   * Returns the number of hosueholds in the fixed record group.
   * 
   * @return The number of hosueholds in the fixed record group.
   */
  public int numberOfHouseholds() {
    return this.householdsMap.values().size();
  }

  /**
   * Returns all of the fixed record groups for the given person.
   * 
   * @param person The person to get all fixed record groups for.
   * @return All of the fixed record groups for the given person.
   */
  public List<FixedRecordGroup> getAllRecordGroupsFor(Person person) {
    return this.householdsMap.get(person.attributes.get(Person.HOUSEHOLD)).getAllRecordGroupsFor(person);
  }
}