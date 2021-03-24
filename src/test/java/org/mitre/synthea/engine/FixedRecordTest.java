package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.engine.Generator.GeneratorOptions;
import org.mitre.synthea.export.FhirR4;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.input.FixedRecord;
import org.mitre.synthea.input.FixedRecordGroup;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;

public class FixedRecordTest {

  /* String Constants */
  public static final String BIRTH_YEAR = "birth_year";
  public static final String BIRTH_MONTH = "birth_month";
  public static final String BIRTH_DAY_OF_MONTH = "birth_day";
  public static final String FIRST_NAME = "first_name";
  public static final String LAST_NAME = "last_name";
  public static final String NAME = "full_name";
  public static final String GENDER = "gender";
  public static final String SEED_ID = "seed_id";
  public static final String RECORD_ID = "record_id";
  public static final String HH_ID = "hh_id";
  public static final String HH_STATUS = "hh_status";
  public static final String PHONE_CODE = "phone_code";
  public static final String PHONE_NUMBER = "phone_number";
  public static final String ADDRESS_SEQUENCE = "address_sequence";
  public static final String ADDRESS_1 = "address_1";
  public static final String ADDRESS_2 = "address_2";
  public static final String CITY = "city";
  public static final String STATE = "state";
  public static final String ZIP = "zip";
  public static final String ADDRESS_START = "address_start";
  public static final String CONTACT_FIRST_NAME = "contact_first";
  public static final String CONTACT_LAST_NAME = "contact_last";
  public static final String CONTACT_EMAIL = "email";

  /* Generator */
  private static Generator generator;

  /**
   * Configure settings to be set before tests.
   * 
   * @throws Exception on test configuration loading errors.
   */
  @BeforeClass
  public static void setup() {
    Generator.DEFAULT_STATE = Config.get("test_state.default", "California");
    Config.set("generate.only_dead_patients", "false");
    Config.set("exporter.split_records", "true");
    Config.set("generate.append_numbers_to_person_names", "false");
    Config.set("generate.only_alive_patients", "true");
    Provider.clear();
    Payer.clear();
    // Create a generator with the preset fixed demographics test file.
    GeneratorOptions go = new GeneratorOptions();
    go.fixedRecordPath = new File("./src/test/resources/fixed_demographics/households_fixed_demographics_test.json");
    go.state = "Colorado"; // Examples are based on Colorado.
    go.population = 100; // Should be overwritten by number of patients in input file.
    go.overflow = false; // Prevent deceased patients from increasing the population size.
    // go.seed = 1616465189989L;
    generator = new Generator(go);
    generator.internalStore = new LinkedList<>(); // Allows us to access patients within generator.
    // Run the simulation.
    generator.run();
  }

  /**
   * Resets any Configuration settings that may interfere with other tests.
   */
  @AfterClass
  public static void resetConfig() {
    Generator.DEFAULT_STATE = Config.get("test_state.default", "California");
    Config.set("exporter.split_records", "false");
    Config.set("generate.append_numbers_to_person_names", "true");
    Config.set("generate.only_alive_patients", "false");
  }

  @Test
  public void checkFixedDemographicsImport() {

    // Household with Lara Kayla Henderson and Chistopher Patrick Ahmann
    org.mitre.synthea.input.Household household = Generator.fixedRecordGroupManager.getHousehold("3879063");

    // Lara is the single_1 role and is the oldest member of the household, thus she
    // should
    // have an instance of every seed record, fixed record group, and address
    // change. In this case, there is only one.
    Person laraKayla = household.getMember("single_1");

    FixedRecordGroup laraOnlyRecordGroup = Generator.fixedRecordGroupManager.getCurrentRecordGroupFor(laraKayla);

    // Lara's final record group should be equivilant to her final one (which
    // happens to be here first and only one).
    assertEquals(laraOnlyRecordGroup.toString(), "Fixed Record Group with Seed Id: [19001]");

    // Hard-coded checks for both of lara's record groups.
    Map<String, String> testAttributes = Stream.of(new String[][] { { RECORD_ID, "19001" }, { HH_ID, "3879063" },
        { HH_STATUS, "single_1" }, { FIRST_NAME, "Lara Kayla" }, { LAST_NAME, "Henderson" },
        { NAME, "Lara Kayla Henderson" }, { BIRTH_YEAR, "1980" }, { BIRTH_MONTH, "03" }, { BIRTH_DAY_OF_MONTH, "11" },
        { GENDER, "F" }, { PHONE_CODE, "303" }, { PHONE_NUMBER, "6377789" }, { ADDRESS_1, "11071 Lone Pnes" },
        { ADDRESS_2, "" }, { CITY, "Littleton" }, { STATE, "Colorado" }, { ZIP, "80125-9291" },
        { ADDRESS_SEQUENCE, "0" }, { CONTACT_EMAIL, "lkh1@something.com" } })
        .collect(Collectors.toMap(data -> data[0], data -> data[1]));
    testRecordAttributes(laraOnlyRecordGroup.getSeedRecordAttributes(), testAttributes);
    // Check that the rest of the population's initial attributes match their seed
    // record.
    for (int i = 0; i < generator.options.population; i++) {
      FixedRecordGroup recordGroup = Generator.fixedRecordGroupManager.getNextRecordGroup(i);
      Map<String, Object> seedAttributes = recordGroup.getSeedRecordAttributes();
      Map<String, Object> pickedAttributes = generator.pickFixedDemographics(recordGroup, new Random(i));
      assertEquals(pickedAttributes.get(Person.FIRST_NAME), seedAttributes.get(Person.FIRST_NAME));
      assertEquals(pickedAttributes.get(Person.LAST_NAME), seedAttributes.get(Person.LAST_NAME));
      assertEquals(pickedAttributes.get(Person.NAME), seedAttributes.get(Person.NAME));
      assertEquals(pickedAttributes.get(Person.BIRTHDATE), seedAttributes.get(Person.BIRTHDATE));
      assertEquals(pickedAttributes.get(Person.GENDER), seedAttributes.get(Person.GENDER));
      assertEquals(pickedAttributes.get(Person.TELECOM), seedAttributes.get(Person.TELECOM));
      assertEquals(pickedAttributes.get(Person.ADDRESS), seedAttributes.get(Person.ADDRESS));
      assertEquals(pickedAttributes.get(Person.STATE), seedAttributes.get(Person.STATE));
      assertEquals(pickedAttributes.get(Person.CITY), seedAttributes.get(Person.CITY));
      assertEquals(pickedAttributes.get(Person.ZIP), seedAttributes.get(Person.ZIP));
    }
  }

  @Test
  public void checkFixedDemographicsFhirExport() {
    // Check each patient from the fixed record input file.
    for (int personIndex = 0; personIndex < generator.internalStore.size(); personIndex++) {
      Person currentPerson = generator.internalStore.get(personIndex);
      // Check that each of the patients' exported FHIR resource attributes match a
      // variant record in the input file.
      for (HealthRecord healthRecord : currentPerson.records.values()) {

        // Convert the current record to exported FHIR.
        currentPerson.record = healthRecord;
        String fhirJson = FhirR4.convertToFHIRJson(currentPerson, System.currentTimeMillis());
        FhirContext ctx = FhirContext.forR4();
        IParser parser = ctx.newJsonParser().setPrettyPrint(true);
        Bundle bundle = parser.parseResource(Bundle.class, fhirJson);

        // Match the current record with the FixedRecord that matches its record id.
        FixedRecord currentFixedRecord = getRecordMatch(currentPerson);

        // First element of bundle is the patient resource.
        Patient patient = ((Patient) bundle.getEntry().get(0).getResource());
        // Birthdate parsing
        long millis = patient.getBirthDate().getTime();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        // Phone number parsing
        String[] phoneNumber = patient.getTelecomFirstRep().getValue().split("-");
        String phoneCode = phoneNumber.length > 0 ? phoneNumber[0] : "";
        String phoneExtension = phoneNumber.length > 1 ? phoneNumber[1] : "";
        Map<String, String> fhirExportAttributes = new HashMap<String, String>();
        // = Stream.of(new String[][] {
        fhirExportAttributes.put(RECORD_ID, patient.getIdentifier().get(patient.getIdentifier().size() - 3).getValue());
        fhirExportAttributes.put(FIRST_NAME, patient.getNameFirstRep().getGivenAsSingleString());
        fhirExportAttributes.put(LAST_NAME, patient.getNameFirstRep().getFamily());
        fhirExportAttributes.put(NAME, patient.getNameFirstRep().getNameAsSingleString().replace("Mr. ", "")
            .replace("Ms. ", "").replace("Mrs. ", "").replace(" PhD", "").replace(" JD", "").replace(" MD", ""));
        fhirExportAttributes.put(BIRTH_YEAR, Integer.toString(c.get(Calendar.YEAR)));
        fhirExportAttributes.put(BIRTH_MONTH, Integer.toString(c.get(Calendar.MONTH) + 1));
        fhirExportAttributes.put(BIRTH_DAY_OF_MONTH, Integer.toString(c.get(Calendar.DAY_OF_MONTH)));
        fhirExportAttributes.put(GENDER, patient.getGender().getDisplay().substring(0, 1));
        fhirExportAttributes.put(PHONE_CODE, phoneCode);
        fhirExportAttributes.put(PHONE_NUMBER, phoneExtension);
        fhirExportAttributes.put(ADDRESS_1, patient.getAddressFirstRep().getLine().get(0).toString());
        fhirExportAttributes.put(CITY, patient.getAddressFirstRep().getCity());
        fhirExportAttributes.put(STATE, patient.getAddressFirstRep().getState());
        fhirExportAttributes.put(CONTACT_EMAIL, patient.getContact().get(0).getTelecom().get(0).getValue());
        fhirExportAttributes.put(ZIP, patient.getAddressFirstRep().getPostalCode());
        // Only Children have a contact person.
        if (patient.getContact().get(0).getName() != null) {
          fhirExportAttributes.put(CONTACT_LAST_NAME, patient.getContact().get(0).getName().getFamily());
          fhirExportAttributes.put(CONTACT_FIRST_NAME, patient.getContact().get(0).getName().getGivenAsSingleString());
        }
        assertNotNull("Person's variant records should have a seed id; seed records do not.",
            currentFixedRecord.seedID);
        testRecordAttributes(currentFixedRecord.getFixedRecordAttributes(), fhirExportAttributes);
      }
    }
  }

  @Test
  public void checkFixedDemographicsVariantAttributes() {
    // Test that the correct number of people were imported from the fixed records
    // file.
    assertEquals(14, Generator.fixedRecordGroupManager.getPopulationSize());
    assertEquals(14, generator.internalStore.size());

    /* Test that the correct number of records were imported for each person. */
    // Lara Kayla Henderson
    Person laraKayla = Generator.fixedRecordGroupManager.getHousehold("3879063").getMember("single_1");
    FixedRecordGroup laraKaylaRecordGroup = Generator.fixedRecordGroupManager.getCurrentRecordGroupFor(laraKayla);
    assertEquals(laraKaylaRecordGroup, Generator.fixedRecordGroupManager.getRecordGroup("3879063", "single_1"));
    // Make sure the person has the correct number of variant fixed records.
    assertEquals(laraKaylaRecordGroup.variantRecords.size(), 2);
    assertTrue(laraKaylaRecordGroup.variantRecords.stream().anyMatch(record -> record.recordId.equals("19003")));
    assertTrue(laraKaylaRecordGroup.variantRecords.stream().anyMatch(record -> record.recordId.equals("19002")));
    // Make sure the person has at least one correct variant record.
    assertTrue("Records: " + laraKayla.records.size(), laraKayla.records.size() >= 1);
    assertTrue(laraKayla.records.values().stream()
        .anyMatch(record -> record.demographicsAtRecordCreation.get(Person.IDENTIFIER_RECORD_ID).equals("19003")
            || record.demographicsAtRecordCreation.get(Person.IDENTIFIER_RECORD_ID).equals("19002")));
    // Test Lara Kayla's VariantRecord 1.
    Map<String, String> testAttributes = Stream.of(new String[][] { { SEED_ID, "1" }, { RECORD_ID, "19002" },
        { HH_ID, "3879063" }, { HH_STATUS, "single_1" }, { FIRST_NAME, "Lara Kay La" }, { LAST_NAME, "Henderson" },
        { NAME, "Lara Kay La Henderson" }, { BIRTH_YEAR, "1980" }, { BIRTH_MONTH, "3" }, { BIRTH_DAY_OF_MONTH, "11" },
        { GENDER, "F" }, { PHONE_CODE, "303" }, { PHONE_NUMBER, "6377789" }, { ADDRESS_1, "11071 Lone Pnes" },
        { CITY, "Littleton" }, { STATE, "Colorado" }, { ZIP, "80125-9291" }, { ADDRESS_SEQUENCE, "0" },
        { CONTACT_EMAIL, "lkh2@something.com" } }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
    assertNotNull("Person's variant records should have a seed id; seed records do not.",
        laraKaylaRecordGroup.variantRecords.get(0).seedID);
    testRecordAttributes(laraKaylaRecordGroup.variantRecords.get(0).getFixedRecordAttributes(), testAttributes);
    // Test Jane Doe's VariantRecord 2.
    testAttributes = Stream.of(new String[][] { { SEED_ID, "1" }, { RECORD_ID, "19003" }, { HH_ID, "3879063" },
        { HH_STATUS, "single_1" }, { FIRST_NAME, "Lara Kayla E|ise" }, { LAST_NAME, "LNU" },
        { NAME, "Lara Kayla E|ise LNU" }, { BIRTH_YEAR, "1980" }, { BIRTH_MONTH, "03" }, { BIRTH_DAY_OF_MONTH, "11" },
        { GENDER, "F" }, { PHONE_CODE, "303" }, { PHONE_NUMBER, "6377789" }, { ADDRESS_1, "11071 Long Pines" },
        { CITY, "Littleton" }, { STATE, "Colorado" }, { ZIP, "80125-9291" }, { ADDRESS_SEQUENCE, "0" },
        { CONTACT_EMAIL, "lkh3@something.com" } }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
    assertNotNull("Person's variant records should have a seed id; seed records do not.",
        laraKaylaRecordGroup.variantRecords.get(1).seedID);
    testRecordAttributes(laraKaylaRecordGroup.variantRecords.get(1).getFixedRecordAttributes(), testAttributes);

    // Christopher Patrick
    Person chrisPat = Generator.fixedRecordGroupManager.getHousehold("3879063").getMember("single_2");
    FixedRecordGroup chrisPatRecordGroup = Generator.fixedRecordGroupManager.getCurrentRecordGroupFor(chrisPat);
    assertEquals(chrisPatRecordGroup, Generator.fixedRecordGroupManager.getRecordGroup("3879063", "single_2"));
    // Make sure the person has the correct number of records.
    assertEquals(chrisPatRecordGroup.variantRecords.size(), 2);
    assertTrue("Records: " + chrisPat.records.size(), chrisPat.records.size() >= 1);

    // Rita Noble
    Person ritaNoble = Generator.fixedRecordGroupManager.getHousehold("59").getMember("married_1");
    FixedRecordGroup ritaNobleRecordGroup = Generator.fixedRecordGroupManager.getCurrentRecordGroupFor(ritaNoble);
    assertEquals(ritaNobleRecordGroup, Generator.fixedRecordGroupManager.getRecordGroup("59", "married_1"));
    // Make sure the person has the correct number of records.
    assertEquals(ritaNobleRecordGroup.variantRecords.size(), 63);
    assertTrue("Records: " + ritaNoble.records.size(), ritaNoble.records.size() >= 1);

    // Justin Noble
    Person justinNoble = Generator.fixedRecordGroupManager.getHousehold("59").getMember("married_2");
    FixedRecordGroup justinNobleRecordGroup = Generator.fixedRecordGroupManager.getCurrentRecordGroupFor(justinNoble);
    assertEquals(justinNobleRecordGroup, Generator.fixedRecordGroupManager.getRecordGroup("59", "married_2"));
    // Make sure the person has the correct number of records.
    assertEquals(justinNobleRecordGroup.variantRecords.size(), 62);
    assertTrue("Records: " + justinNoble.records.size(), justinNoble.records.size() >= 1);
  }

  /**
   * Gets a variant fixed record match for the person's current health record.
   * 
   * @param person The person to get the record match for.
   * @return The matching fixed record.
   */
  private FixedRecord getRecordMatch(Person person) {
    List<FixedRecordGroup> allRecordGroups = Generator.fixedRecordGroupManager.getAllRecordGroupsFor(person);
    for (FixedRecordGroup recordGroup : allRecordGroups) {
      String currentRecordId = (String) person.record.demographicsAtRecordCreation.get(Person.IDENTIFIER_RECORD_ID);
      for (FixedRecord currentRecord : recordGroup.variantRecords) {
        try {
          if (currentRecord.recordId.equals(currentRecordId)) {
            return currentRecord;
          }
        } catch (Exception e) {
          fail("There was an exception for [currentRecord: " + currentRecord + "], [With recordId: "
              + currentRecord.recordId + "], [And currentRecordId: " + currentRecordId + "]\nException:"
              + e.getStackTrace().toString());
        }
      }
    }
    // If we reach here, there was no matching record id.
    fail("Did not find a record match for " + person.attributes.get(Person.NAME) + " with record id "
        + person.record.demographicsAtRecordCreation.get(Person.IDENTIFIER_RECORD_ID) + ". Person has "
        + Generator.fixedRecordGroupManager.getCurrentRecordGroupFor(person).variantRecords.size()
        + " imported fixed records in their fixed record group with record ids "
        + Generator.fixedRecordGroupManager
            .getAllRecordGroupsFor(person).stream().map(frg -> frg.variantRecords).collect(Collectors.toList())
        + ". Person's health records have ids "
        + person.records.values().stream()
            .map(record -> record.demographicsAtRecordCreation.get(Person.IDENTIFIER_RECORD_ID))
            .collect(Collectors.toList()));
    return null;
  }

  @Test
  public void checkSeedHistory() {
    // The variant records from the first time period should be based on the
    // variants from the first fixed record group and seed record.

    // Rita Noble
    Person ritaNoble = Generator.fixedRecordGroupManager.getHousehold("59").getMember("married_1");
    List<Integer> ritaSeedIds = new ArrayList<Integer>();
    // assertEquals(19489278, ritaNoble.attributes.get(Person.IDENTIFIER_RECORD_ID));
    ritaSeedIds.add(19489272); // Rita Sequence 1
    ritaSeedIds.add(19489274); // Rita Sequence 2
    ritaSeedIds.add(19489276); // Rita Sequence 3
    ritaSeedIds.add(19489278); // Rita Sequence 4
    this.testThatSeedsArePresent(ritaNoble, ritaSeedIds);

    // Justin Noble - since the other household member (rita noble) is the same age,
    // both should have all 4 of their seed ids present.
    Person justinNoble = Generator.fixedRecordGroupManager.getHousehold("59").getMember("married_2");
    // assertEquals(19489279, justinNoble.attributes.get(Person.IDENTIFIER_RECORD_ID));
    List<Integer> justinSeedIds = new ArrayList<Integer>();
    justinSeedIds.add(19489273); // Justin Sequence 1
    justinSeedIds.add(19489275); // Justin Sequence 2
    justinSeedIds.add(19489277); // Justin Sequence 3
    justinSeedIds.add(19489279); // Justin Sequence 4
    this.testThatSeedsArePresent(justinNoble, justinSeedIds);

  }

  /**
   * Checks that the given list of seed Ids are present in the given person's
   * exported fhir health records.
   * 
   * @param person  The person to check fhir records of.
   * @param seedIds The seed ids to check for.
   */
  private void testThatSeedsArePresent(Person person, List<Integer> seedIds) {
    // Pull all the exported health records and match them to their variant records.
    List<Bundle> exportedHealthRecords = new ArrayList<Bundle>();
    List<FixedRecord> variantRecords = new ArrayList<FixedRecord>();

    Map<Integer, List<Bundle>> sequenceHealthRecordPairs = new HashMap<Integer, List<Bundle>>();

    for (HealthRecord healthRecord : person.records.values()) {
      // Convert the current record to exported FHIR.
      person.record = healthRecord;
      String fhirJson = FhirR4.convertToFHIRJson(person, System.currentTimeMillis());
      FhirContext ctx = FhirContext.forR4();
      IParser parser = ctx.newJsonParser().setPrettyPrint(true);
      Bundle bundle = parser.parseResource(Bundle.class, fhirJson);

      // Match the current record with the FixedRecord that matches its record id.
      FixedRecord vr = getRecordMatch(person);
      variantRecords.add(vr);
      exportedHealthRecords.add(bundle);

      // Add to bundle-pair matching.
      int variantSequence = vr.addressSequence;
      if (!sequenceHealthRecordPairs.containsKey(variantSequence)) {
        sequenceHealthRecordPairs.put(variantSequence, new ArrayList<Bundle>());
      }
      sequenceHealthRecordPairs.get(variantSequence).add(bundle);

      Patient patient = ((Patient) bundle.getEntry().get(0).getResource());

      System.out.println("FHIR Patient date-time-value: " + patient.getNameFirstRep().getGivenAsSingleString() + " - "
          + patient.dateTimeValue());
    }

    // Make sure that there is at least one variant record for each of the fixed
    // record groups, given as the input seedIds.
    for (int id : seedIds) {
      int seedIdMatches = 0;
      for (FixedRecord vr : variantRecords) {
        if (id == Integer.valueOf(vr.seedID)) {
          seedIdMatches++;
        }
      }
      System.out.println("Seed Matches: " + seedIdMatches);
      assertTrue("Seed id " + id + " does not have any matches in " + person.attributes.get(Person.NAME)
          + "'s' health records.", seedIdMatches > 0);
    }

    // Make sure that each variant is from a correctly chronological fixed record
    // group.
    for (int currentSequence : sequenceHealthRecordPairs.keySet().stream().sorted().collect(Collectors.toList())) {
      // System.out.println((
      // sequenceHealthRecordPairs.get(currentSequence).get(0).getEntry().get(1).getResource().getPeriod().getStart()));
    }
  }

  /**
   * Tests that the given fixed record has the same attributes as the given test
   * attributes map.
   * 
   * @param fixedRecord    The fixed record to test against.
   * @param testAttributes The attributes to test for.
   */
  private void testRecordAttributes(Map<String, Object> recordAttributes, Map<String, String> testAttributes) {
    assertEquals(recordAttributes.get(Person.IDENTIFIER_RECORD_ID), testAttributes.get(RECORD_ID));
    assertEquals(
        "Expected: <" + recordAttributes.get(Person.NAME) + "> but was: <" + testAttributes.get(NAME)
            + ">. Fixed record id is: " + recordAttributes.get(Person.IDENTIFIER_RECORD_ID) + ".",
        recordAttributes.get(Person.NAME), testAttributes.get(NAME));
    assertEquals(recordAttributes.get(Person.FIRST_NAME), testAttributes.get(FIRST_NAME));
    assertEquals(recordAttributes.get(Person.LAST_NAME), testAttributes.get(LAST_NAME));
    long testBirthDate = LocalDateTime
        .of(Integer.parseInt(testAttributes.get(BIRTH_YEAR)), Integer.parseInt(testAttributes.get(BIRTH_MONTH)),
            Integer.parseInt(testAttributes.get(BIRTH_DAY_OF_MONTH)), 12, 0)
        .toInstant(ZoneOffset.UTC).toEpochMilli();
    assertEquals(recordAttributes.get(Person.BIRTHDATE), testBirthDate);
    assertEquals(recordAttributes.get(Person.GENDER), testAttributes.get(GENDER));
    assertEquals(recordAttributes.get(Person.TELECOM),
        testAttributes.get(PHONE_CODE) + "-" + testAttributes.get(PHONE_NUMBER));
    assertEquals(recordAttributes.get(Person.ADDRESS), testAttributes.get(ADDRESS_1));
    // assertEquals(fixedRecord.addressLineTwo, testAttributes.get(ADDRESS_2));
    assertEquals(
        "Failure for person " + recordAttributes.get(Person.NAME) + " and record id "
            + recordAttributes.get(Person.IDENTIFIER_RECORD_ID) + ".",
        recordAttributes.get(Person.CITY), testAttributes.get(CITY));
    assertEquals(recordAttributes.get(Person.ZIP), testAttributes.get(ZIP));
    // assertEquals(fixedRecord.contactEmail, testAttributes.get(CONTACT_EMAIL));
    if (recordAttributes.get(Person.CONTACT_GIVEN_NAME) != null) {
      // Only children have a contact person (in the fixed record test file).
      assertEquals(recordAttributes.get(Person.CONTACT_GIVEN_NAME), testAttributes.get(CONTACT_FIRST_NAME));
      assertEquals(recordAttributes.get(Person.CONTACT_FAMILY_NAME), testAttributes.get(CONTACT_LAST_NAME));
    }
  }

  @Test
  public void checkHouseholds() {
    // Check that the households and their members were created properly.
    assertEquals(11, Generator.fixedRecordGroupManager.numberOfHouseholds());

    // Pull the 2 members of household 59.
    Person ritaNoble = Generator.fixedRecordGroupManager.getHousehold("59").getMember("married_1");
    Person justinNoble = Generator.fixedRecordGroupManager.getHousehold("59").getMember("married_2");

    // This only accounts for the final record group that each person has, not any
    // of their initial 3.
    assertEquals(Generator.fixedRecordGroupManager.getCurrentRecordGroupFor(ritaNoble).toString(),
        "Fixed Record Group with Seed Id: [19489278]");
    assertEquals(Generator.fixedRecordGroupManager.getCurrentRecordGroupFor(justinNoble).toString(),
        "Fixed Record Group with Seed Id: [19489279]");

    // Check that the record groups are correct by checking the seed id.
    assertEquals("19489278",
        ((FixedRecordGroup) Generator.fixedRecordGroupManager.getCurrentRecordGroupFor(ritaNoble)).getSeedId());
    assertEquals("19489279",
        ((FixedRecordGroup) Generator.fixedRecordGroupManager.getCurrentRecordGroupFor(justinNoble)).getSeedId());
  }

  @Test
  public void checkForcedProviderChange() {
    // List of prior providers that cannnot match a future one.
    List<String> providerIds = new ArrayList<String>();
    // Pull out Jane Doe to run provider tests on.
    Person person = Generator.fixedRecordGroupManager.getHousehold("59").getMember("married_1");
    // Pull out all existing Provider UUIDs.
    providerIds
        .addAll(person.records.values().stream().map(record -> record.provider.uuid).collect(Collectors.toList()));
    // Force a new provider.
    person.forceNewProvider(HealthRecord.EncounterType.WELLNESS, Utilities.getYear(0L));
    person.record = person.getHealthRecord(
        person.getProvider(HealthRecord.EncounterType.WELLNESS, System.currentTimeMillis()),
        System.currentTimeMillis());
    // Check that the new provider ID is not in the list of prior IDs.
    String firstUuid = person.record.provider.uuid;
    assertFalse(providerIds.stream().anyMatch(uuid -> uuid.equals(firstUuid)));
    // Force a new provider.
    person.forceNewProvider(HealthRecord.EncounterType.WELLNESS, Utilities.getYear(0L));
    person.record = person.getHealthRecord(
        person.getProvider(HealthRecord.EncounterType.WELLNESS, System.currentTimeMillis()),
        System.currentTimeMillis());
    // Check that the new provider ID is not in the list of prior IDs.
    String secondUuid = person.record.provider.uuid;
    assertFalse(providerIds.stream().anyMatch(uuid -> uuid.equals(secondUuid)));
    // Force a new provider.
    person.forceNewProvider(HealthRecord.EncounterType.WELLNESS, Utilities.getYear(0L));
    person.record = person.getHealthRecord(
        person.getProvider(HealthRecord.EncounterType.WELLNESS, System.currentTimeMillis()),
        System.currentTimeMillis());
    // Check that the new provider ID is not in the list of prior IDs.
    String thirdUuid = person.record.provider.uuid;
    assertFalse(providerIds.stream().anyMatch(uuid -> uuid.equals(thirdUuid)));
  }
}