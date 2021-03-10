package org.mitre.synthea.input;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.gson.annotations.SerializedName;

import org.mitre.synthea.world.agents.Person;

public class Household {

  @SerializedName(value = "seed_records")
  public List<FixedRecord> seedRecords;

  @SerializedName(value = "variants")
  public List<FixedRecord> variantRecords;

  // The Map of household members' FixedRecordGroups where the key is the peron's
  // household role (married_1, marride_2, child_1) and the value is their list of
  // FixedRecordGroups - which will update over time.
  private Map<String, List<FixedRecordGroup>> fixedRecordGroups = new HashMap<String, List<FixedRecordGroup>>();

  // The id of the household, corresponding to the household IDs of the input
  // records.
  public String id;
  // The Map of household members where the key is the peron's household role
  // (married_1, marride_2, child_1)
  private Map<String, Person> members;
  //
  private HouseholdAddressHistory addressHistory;

  /**
   * Household Constructor.
   * 
   * @param id the household id.
   */
  // public Household(int id) {
  // this.id = id;
  // this.members = new HashMap<String, Person>();
  // // this.addressHistory = new HouseholdAddressHistory();
  // }

  public Household() {
    this.members = new HashMap<String, Person>();
  }

  public void updateCurrentFixedRecordGroup(int year) {
    // If it is time for the new seed records and their new addresses to start, then
    // update each person with their new seed records and force a new
    // provider(health record) for them.
    String currentAddress = this.addressHistory.getAddressAt(year);
  }

  public Household initializeHousehold() {

    this.id = this.seedRecords.get(0).householdId;

    // The list of addresses this household will have.
    List<String> addresses = new ArrayList<String>();

    // Determine how many seed records per person. This determines how many time
    // splits there should be, so that each seed record spends some amount of time
    // as the primary seed record.

    // Populate the fixed record groups with the newly created initialized fixed
    // record groups for
    // each seed record.
    for (FixedRecord seedRecord : this.seedRecords) {
      if (!this.fixedRecordGroups.containsKey(seedRecord.householdRole)) {
        this.fixedRecordGroups.put(seedRecord.householdRole, new ArrayList<FixedRecordGroup>());
      }
      // Create a new FixedRecordGroup for this seed record.
      this.fixedRecordGroups.get(seedRecord.householdRole).add(new FixedRecordGroup(seedRecord));
      // Add the new addres to the address list, if it isn't already in there.
      if (!addresses.contains(seedRecord.addressLineOne + seedRecord.city)) {
        addresses.add(seedRecord.addressLineOne + seedRecord.city);
      }
    }

    // Go through the variant records and assign them to their relevant
    // FixedRecordGroups.
    for (FixedRecord variant : this.variantRecords) {
      for (FixedRecordGroup frg : this.fixedRecordGroups.get(variant.householdRole)) {
        if (frg.getSeedId().equals(variant.seedID)) {
          frg.addVariantRecord(variant);
        }
      }
    }
    // Now, every seed record for every person should have their own set of
    // corresponding variant records.

    // This tells us how many seed records per person there are and how many address
    // changes need to be accounted for.
    int fixedRecordGroupsPerPerson = this.fixedRecordGroups.values().iterator().next().size();
    String fixedRecordGroupFirstPerson = this.fixedRecordGroups.values().iterator().next().get(0).getSeedId();
    int numberOfAddresses = addresses.size();
    // A hosuehold of single people can have different addressses.
    // if (numberOfAddresses != fixedRecordGroupsPerPerson) {
    // throw new RuntimeException(
    // "The number of addresses MUST be equal to the number of fixed record groups
    // (equivalent to seed records) per person. Number of addresses: "
    // + numberOfAddresses + ", number of fixed record groups (seed records) per
    // person: " + fixedRecordGroupsPerPerson + ". Seed Record in question: " +
    // fixedRecordGroupFirstPerson);
    // }

    // Now we need the oldest person in the household so we can randomly distribute
    // the seed records and addresses over their lifespan. Then, assign an order
    // based on ADDRESS_SEQUENCE.
    int householdStartYear = 1980; // TODO - get the actual birth year
    int currentYear = 2020;

    // The map of the addreses
    Map<Integer, String> addressMap = new HashMap<Integer, String>();

    int rangeOfYears = currentYear - householdStartYear;

    List<Integer> years = new ArrayList<Integer>();

    Random r = new Random();

    // Populate the address map with a addressStartYear - address key-value pair.
    for (String address : addresses) {
      boolean validAddressYear = false;
      while (!validAddressYear) {
        int addressStartYear = r.nextInt(currentYear - householdStartYear) + householdStartYear;
        if (!addressMap.containsKey(addressStartYear)) {
          addressMap.put(addressStartYear, address);
          validAddressYear = true;
        }
      }
    }

    this.addressHistory = new HouseholdAddressHistory(addressMap);

    return this;
  }

  /**
   * Gets the current record group for the person in this houshehold with the
   * given household role.
   * 
   * @param householdRole
   * @return
   */
  public FixedRecordGroup getRecordGroupFor(String householdRole) {
    return this.fixedRecordGroups.get(householdRole).get(this.currentFixedRecordGroupIndex());
  }

  /**
   * Returns the current index of the current active fixed record groups.
   * 
   * @return
   */
  private int currentFixedRecordGroupIndex() {
    // TODO - actual implementation
    return 0;
  }

  private class HouseholdAddressHistory {

    private final Map<Integer, String> addressMap;

    public HouseholdAddressHistory(Map<Integer, String> addressMap) {
      this.addressMap = addressMap;
    }

    public String getAddressAt(int year) {
      return this.addressMap.get(year);
    }
  }

  /**
   * Gets the initial fixed record group for each seed record in the household. To
   * be used for generating people.
   * 
   * @return
   */
  public List<FixedRecordGroup> getInitialFixedRecordGroupForEachMember() {

    List<FixedRecordGroup> initialFixedRecordGroups = new ArrayList<FixedRecordGroup>();

    for (List<FixedRecordGroup> frgList : this.fixedRecordGroups.values()) {
      initialFixedRecordGroups.add(frgList.get(0));
    }

    return initialFixedRecordGroups;
  }

  /**
   * Returns whether this household contains the given person.
   * 
   * @param person
   * @return
   */
  public boolean includesPerson(Person person) {
    return this.members.values().contains(person);
  }

  /**
   * Adds the given member to the household.
   * 
   * @param person
   */
  public void addMember(Person person, String householdRole) {
    this.members.put(householdRole, person);
  }

  /**
   * Returns the member with the given household role in this household.
   * 
   * @param householdRole
   * @return
   */
  public Person getMember(String householdRole) {
    return this.members.get(householdRole);
  }

  public String getHouseholdId() {
    return this.seedRecords.get(0).householdId;
  }

  public int householdSize() {
    return this.fixedRecordGroups.values().size();
  }

}
