package org.mitre.synthea.input;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.mitre.synthea.world.agents.Person;

/**
 * A class the desribes and maintains a household, its members, and its seed
 * records, variant records, and record groups.
 */
public class Household {

  @SerializedName(value = "seed_records")
  public List<FixedRecord> seedRecords;

  @SerializedName(value = "variants")
  public List<FixedRecord> variantRecords;

  // The Map of household members' FixedRecordGroups where the key is the person's
  // household role and the value is their list of FixedRecordGroups - which will
  // update over time.
  private Map<String, List<FixedRecordGroup>> fixedRecordGroups
      = new HashMap<String, List<FixedRecordGroup>>();
  // The list of years in order to correspond with each address sequence update.
  private List<Integer> addressYears;
  // The current addresss sequences for each person. String: HouseholdRole, Int:
  // CurrentAddressSequence.
  private final Map<String, Integer> currentAddressSequences;
  // The Map of household members where the key is the person's household role,
  // the value is the person themselves.
  private Map<String, Person> members;
  // Household id
  private String id;
  // Randomizer for this household.
  private Random random;

  /**
   * Constructor for a household.
   */
  public Household() {
    this.members = new ConcurrentHashMap<String, Person>();
    this.currentAddressSequences = new HashMap<String, Integer>();
  }

  /**
   * Updates the current fixed records of the given person of this household based
   * on the current year.
   * 
   * @param currentYear The current year to update for.
   * @return Whether the fixed record group for this person was updated.
   */
  public boolean updateCurrentFixedRecordGroupFor(int currentYear, Person person) {

    String householdRole = this.getHouseholdRoleFor(person);
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
      // The fixed record group was updated, set the new record group sequence and
      // return true.
      this.currentAddressSequences.put(householdRole, currentIndex);
      return true;
    }
    // If we reach here, no fixed record goudp were updated so return false.
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
      this.fixedRecordGroups.put(key, this.fixedRecordGroups.get(key)
          .stream().sorted().collect(Collectors.toList()));
    }

    // Iterate through the variant records and assign them to their relevant
    // FixedRecordGroups.
    System.out.println("Variant records: " + this.variantRecords);
    for (FixedRecord variant : this.variantRecords) {
      System.out.println("Check 1: " + variant + variant.householdRole);
      for (FixedRecordGroup frg : this.fixedRecordGroups.get(variant.householdRole)) {
        System.out.println("Check 2: " + variant.householdRole + variant.seedID);
        if (frg.getSeedId().equals(variant.seedID)) {
          System.out.println("Check 3: " + variant);
          frg.addVariantRecord(variant);
        }
      }
    }

    System.out.println("DONE ADDING VARIANTS");

    // Iterate through each fixed record group and set their random initial variant
    // records.
    for (List<FixedRecordGroup> frgs : this.fixedRecordGroups.values()) {
      for (FixedRecordGroup frg : frgs) {
        frg.setInitialVariantRecord(this.random);
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
    // There will be a random number of addresses from 1 - number of seeds.
    // int numberOfAddresses = this.random.nextInt(this.fixedRecordGroups.values().iterator().next().size()) + 1;
    int numberOfAddresses = this.fixedRecordGroups.values().iterator().next().size();
    for (int i = 0; i < numberOfAddresses; i++) {
      int newYear = this.random().nextInt(rangeOfYears) + householdStartYear + 1;
      while (addressYearRanges.contains(newYear)) {
        newYear = this.random().nextInt(rangeOfYears) + householdStartYear + 1;
      }
      addressYearRanges.add(newYear);
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
    int earliestYear = 99999;
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
   * @param householdRole The household role to get the current record group for.
   * @return
   */
  public FixedRecordGroup getCurrentRecordGroupFor(String householdRole) {
    return this.fixedRecordGroups.get(householdRole)
        .get(this.currentAddressSequences.get(householdRole));
  }

  /**
   * Returns the current fixed record group of the given person.
   * 
   * @param person The person to get the current record group for.
   * @return
   */
  public FixedRecordGroup getCurrentRecordGroupFor(Person person) {
    return this.getCurrentRecordGroupFor(this.getHouseholdRoleFor(person));
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
   * @param person the person to check for.
   * @return
   */
  public boolean includesPerson(Person person) {
    return this.members.values().contains(person);
  }

  /**
   * Adds the given member to the household.
   * 
   * @param person the person to add.
   */
  public void addMember(Person person, String householdRole) {
    this.members.put(householdRole, person);
    // Reset the current address sequence for this person.
    this.currentAddressSequences.put(householdRole, 0);
  }

  /**
   * Returns the member with the given household role in this household.
   * 
   * @param householdRole The person whose household role to get.
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
    FixedRecord fr = this.getCurrentRecordGroupFor(householdRole).updateCurrentVariantRecord();
    person.attributes.putAll(fr.getFixedRecordAttributes());
    return fr;
  }

  /**
   * Gets the household role of the given person.
   * 
   * @param person The person to get the role for.
   * @return The role of the person.
   */
  private String getHouseholdRoleFor(Person person) {
    List<String> householdRoles = this.members.entrySet().stream()
        .filter(entry -> person.equals(entry.getValue()))
        .map(Map.Entry::getKey).collect(Collectors.toList());
    if (householdRoles.isEmpty()) {
      throw new RuntimeException(
          "No household roles found for the given person: "
          + person.attributes.get(Person.NAME) + ".");
    } else if (householdRoles.size() > 1) {
      throw new RuntimeException("There are more than 1 household roles corresponding to the "
          + "given person: " + person.attributes.get(Person.NAME) + ".");
    }
    return householdRoles.get(0);
  }

  /**
   * Returns all of the fixed record groups associated with the given person.
   * 
   * @param person  The person to get all record groups for.
   * @return  The list of the person's record groups.
   */
  public List<FixedRecordGroup> getAllRecordGroupsFor(Person person) {
    return this.fixedRecordGroups.get(this.getHouseholdRoleFor(person));
  }
}
