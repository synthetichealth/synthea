package org.mitre.synthea.input;

import com.google.gson.Gson;

import com.google.gson.reflect.TypeToken;
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

  public int getPopulationSize() {
    return this.householdsMap.values().stream().mapToInt(h -> h.householdSize()).sum();
  }

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
      fixedRecordGroupManager.householdsList
          = gson.fromJson(new FileReader(filePath), jsonType);
    } catch (FileNotFoundException e) {
      throw new RuntimeException("Couldn't open the fixed patient demographics records file", e);
    }
    fixedRecordGroupManager.initializeHouseholds();
    return fixedRecordGroupManager;
  }

  private void initializeHouseholds() {
    for (Household household : this.householdsList) {
      String householdId = household.getHouseholdId();
      this.householdsMap.put(householdId, household.initializeHousehold());
    }
  }

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
}