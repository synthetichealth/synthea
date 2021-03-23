package org.mitre.synthea.input;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

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
  // The list of years in order to correspond with each address sequence update.
  private List<Integer> addressYears;
  // The currrent address sequence.
  // private int currentAddressSequence;
  // The current addresss sequences for each person. String: HouseholdRole, Int: CurrentAddressSequence.
  private final Map<String, Integer> currentAddressSequences;
  // The Map of household members where the key is the peron's household role
  // (married_1, marride_2, child_1)
  private Map<String, Person> members;
  // Household id
  private String id;
  // Randomizer for this household.
  private Random random;

  /**
   * Constructor for a household.
   */
  public Household() {
    this.members = new HashMap<String, Person>();
    this.currentAddressSequences = new HashMap<String, Integer>();
  }

  /**
   * Updates the current fixed records of the memebers of this household based on
   * the current year.
   * 
   * @param currentYear The current year to update for.
   * @return Whether the fixed record groups were updated.
   */
  public boolean updateCurrentFixedRecordGroupFor(int currentYear, Person person) {

    String householdRole = this.getHouseholdRoleFor(person);

    // If the household has already updated for this year, then just return false.
    // if (currentYear == this.yearLastUpdated) {
    //   return false;
    // }

    // this.yearLastUpdated = currentYear;
    // If it is time for the new seed records and their new addresses to start, then
    // update each person with their new seed records and force a new
    // provider(health record) for them.
    int currentIndex = this.currentAddressSequences.get(householdRole);
    for (int i = 0; i < this.addressYears.size(); i++) {
      if (currentYear >= this.addressYears.get(i)
          && (i + 1 >= this.addressYears.size() || currentYear < this.addressYears.get(i + 1))) {
        currentIndex = i;
      }
    }
    if (currentIndex != this.currentAddressSequences.get(householdRole)) {
      System.out.println("current address sequence updated for " + person.attributes.get(Person.NAME) + " with prior sequence: " + this.currentAddressSequences.get(householdRole));
      this.currentAddressSequences.put(householdRole, currentIndex);
      System.out.println("And post sequence: " + this.currentAddressSequences.get(householdRole));
      return true;
    }
    return false;
  }

  /**
   * Initializes this household with a set seed.
   * 
   * @param seed The seed to initialize the household with.
   * @return The initialized household.
   */
  public Household initializeHousehold(long seed) {
    this.id = this.seedRecords.get(0).householdId;
    this.random = new Random(seed);
    // The list of addresses this household will have.
    List<String> addresses = new ArrayList<String>();

    // Populate and create the fixed record groups with each seed record.
    for (FixedRecord seedRecord : this.seedRecords) {
      if (!this.fixedRecordGroups.containsKey(seedRecord.householdRole)) {
        this.fixedRecordGroups.put(seedRecord.householdRole, new ArrayList<FixedRecordGroup>());
      }
      // Create a new FixedRecordGroup for this seed record.
      this.fixedRecordGroups.get(seedRecord.householdRole).add(new FixedRecordGroup(seedRecord));
      // Add the new address to the address list, if it isn't already in there.
      if (!addresses.contains(seedRecord.addressLineOne + seedRecord.city)) {
        addresses.add(seedRecord.addressLineOne + seedRecord.city);
      }
    }

    // Once the FixedRecordGroups are initialized, we need to sort each person's
    // list of FixedRecordGroups. This sorting is done by their ADDRESS_SEQUENCE.
    for (String key : this.fixedRecordGroups.keySet()) {
      this.fixedRecordGroups.put(key, this.fixedRecordGroups.get(key).stream().sorted().collect(Collectors.toList()));
    }

    // Iterate through the variant records and assign them to their relevant
    // FixedRecordGroups.
    for (FixedRecord variant : this.variantRecords) {
      for (FixedRecordGroup frg : this.fixedRecordGroups.get(variant.householdRole)) {
        if (frg.getSeedId().equals(variant.seedID)) {
          frg.addVariantRecord(variant);
        }
      }
    }

    // Set the range of address years corresponding to each address change.
    this.addressYears = this.getListOfYearsFor();

    return this;
  }

  /**
   * Now we need the oldest person in the household so we can randomly distribute
   * the seed records and addresses over their lifespan. Then, assign an order
   * based on ADDRESS_SEQUENCE. Get a list of ints with start years that will
   * correspond with new address sequences of fixed record groups for each member
   * of the household.
   */
  private List<Integer> getListOfYearsFor() {
    int householdStartYear = this.getBirthYearOfOldestMember();
    int currentYear = 2020; // TODO - should not be hardcoded, need to get current year.
    int rangeOfYears = currentYear - householdStartYear;
    List<Integer> addressYearRanges = new ArrayList<Integer>();
    int numberOfAddresses = this.fixedRecordGroups.values().iterator().next().size();
    for (int i = 0; i < numberOfAddresses; i++) {
      addressYearRanges.add(this.random().nextInt(rangeOfYears) + householdStartYear);
    }
    addressYearRanges = addressYearRanges.stream().sorted().collect(Collectors.toList());
    return addressYearRanges;
  }

  private Random random() {
    return this.random;
  }

  /**
   * Returns the earliest birth year of the members of this household.
   * 
   * @return int The earliest birth year of this household.
   */
  private int getBirthYearOfOldestMember() {
    int earliestYear = 9999; // TODO - initial earliest year should not be hardcoded.
    for (List<FixedRecordGroup> frgs : this.fixedRecordGroups.values()) {
      int thisBirthYear = frgs.get(0).getSeedBirthYear();
      if (thisBirthYear < earliestYear) {
        earliestYear = thisBirthYear;
      }
    }
    return earliestYear;
  }

  /**
   * Gets the current record group for the person in this houshehold with the
   * given household role.
   * 
   * @param householdRole
   * @return
   */
  public FixedRecordGroup getRecordGroupFor(String householdRole) {
    return this.fixedRecordGroups.get(householdRole).get(this.currentAddressSequences.get(householdRole));
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

    // Reset the current address sequence for this person.
    this.currentAddressSequences.put(householdRole, 0);

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

  /**
   * Returns the household id of this household.
   * 
   * @return
   */
  public String getHouseholdId() {
    return this.id;
  }

  /**
   * Returns the size of this household.
   * 
   * @return
   */
  public int householdSize() {
    return this.fixedRecordGroups.values().size();
  }

  /**
   * Updates the variant record for the given person.
   * 
   * @param person The person whose variant records need updating.
   */
  public FixedRecord updatePersonVariantRecord(Person person) {
    String householdRole = this.getHouseholdRoleFor(person);
    FixedRecord fr = this.getRecordGroupFor(householdRole).updateCurrentVariantRecord();
    // TODO - should be putting all the attributes in the person elsewhere so as
    // to maintain the correct values for valud cities and other malformed fixed
    // record data that the seed will have?
    person.attributes.putAll(fr.getFixedRecordAttributes());
    return fr;
  }

  /**
   * Gets the household role of the given person,
   * 
   * @param person The person to get the role for.
   * @return The role of the person.
   */
  private String getHouseholdRoleFor(Person person) {
    List<String> householdRoles = this.members.entrySet().stream().filter(entry -> person.equals(entry.getValue()))
        .map(Map.Entry::getKey).collect(Collectors.toList());
    if (householdRoles.isEmpty()) {
      throw new RuntimeException(
          "No household roles found for the given person: " + person.attributes.get(Person.NAME) + ".");
    } else if (householdRoles.size() > 1) {
      throw new RuntimeException("There are more than 1 household roles corresponding to the given person: "
          + person.attributes.get(Person.NAME) + ".");
    }
    return householdRoles.get(0);
  }

  public FixedRecordGroup getRecordGroupFor(Person person) {
    return this.getRecordGroupFor(this.getHouseholdRoleFor(person));
  }

  public List<FixedRecordGroup> getAllRecordGroupsFor(Person person) {
    return this.fixedRecordGroups.get(this.getHouseholdRoleFor(person));
  }
}
