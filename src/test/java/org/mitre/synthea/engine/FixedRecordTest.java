package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.engine.Generator.GeneratorOptions;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.input.FixedRecord;
import org.mitre.synthea.input.FixedRecordGroup;
import org.mitre.synthea.input.FixedRecordGroupManager;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;

public class FixedRecordTest {

  // The generator.
  private static Generator generator;
  private static FixedRecordGroupManager fixedRecordGroupManager;

  /**
   * Configure settings across these tests.
   * @throws Exception on test configuration loading errors.
   */
  @BeforeClass
  public static void setup() {
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Massachusetts");
    Config.set("generate.only_dead_patients", "false"); 
    Config.set("exporter.split_records", "true");
    Provider.clear();
    Payer.clear();
    // Create a generator with the preset fixed demographics test file.
    GeneratorOptions go = new GeneratorOptions();
    go.fixedRecordPath = new File(
        "src/test/resources/fixed_demographics/fixed_demographics_test.json");
    go.state = "Colorado";  // Examples are based on Colorado.
    go.population = 100;  // Should be overwritten by number of patients in input file.
    // go.enabledModules = new ArrayList<String>(); // Prevent extraneous modules from being laoded.
    // go.enabledModules.add("");
    generator = new Generator(go);
    generator.internalStore = new LinkedList<>(); // Allows us to access patients within generator.
    // List of raw RecordGroups imported directly from the input file for later comparison.
    fixedRecordGroupManager = generator.importFixedDemographicsFile();
    // Generate each patient from the fixed record input file.
   
  }

  @Test
  public void initialFixedDemographicsImportTest() {

    // Check that the initial attributes match the seed record.
    for (int i = 0; i < generator.options.population; i++) {
      FixedRecordGroup recordGroup = generator.fixedRecordGroupManager.getRecordGroup(i);
      Map<String, Object> demoAttributes = generator.pickFixedDemographics(recordGroup, new Random(i));
      FixedRecord seedRecord = recordGroup.seedRecord;
      assertEquals(demoAttributes.get(Person.FIRST_NAME), (seedRecord.firstName));
      assertEquals(demoAttributes.get(Person.LAST_NAME), (seedRecord.lastName));
      assertEquals(demoAttributes.get(Person.ADDRESS), (seedRecord.addressLineOne));
      assertEquals(demoAttributes.get(Person.BIRTHDATE), (seedRecord.getBirthDate()));
      assertEquals(demoAttributes.get(Person.GENDER), (seedRecord.gender));
      assertEquals(demoAttributes.get(Person.TELECOM), (seedRecord.getTelecom()));
      assertEquals(demoAttributes.get(Person.STATE), (seedRecord.state));
      assertEquals(demoAttributes.get(Person.CITY), (seedRecord.city));
      assertEquals(demoAttributes.get(Person.ZIP), (seedRecord.zipcode));
    }
  }

  @Test
  public void fixedDemographicsTest() {

    // Generate the people.
    for (int i = 0; i < generator.options.population; i++) {
      generator.generatePerson(i);
    }
    
    // Make sure that the correct number of people were imported from the fixed records file.
    assertEquals(4, generator.internalStore.size());
    assertEquals(4, fixedRecordGroupManager.getPopulationSize());

    // Check that each person has HealthRecords that match their fixed demographic records.
    for (int p = 0; p < generator.internalStore.size(); p++) {
      // Get the current person and pull their list of records.
      Person currentPerson = generator.internalStore.get(p);
      FixedRecordGroup recordGroup
          = (FixedRecordGroup) currentPerson.attributes.get(Person.RECORD_GROUP);
      // Make sure the person has the correct number of records.
      assertTrue(currentPerson.records.size() >= 3);
      assertTrue(recordGroup.variantRecords.size() == 3);
          
      // Cycle the person's FixedRecords to compare them to the raw imported FixedRecords.
      for (int recordNum = 0; recordNum < currentPerson.records.size(); recordNum++) {

        int recordToPull = recordNum;
        if (recordNum >= recordGroup.variantRecords.size()) {
          recordToPull = recordGroup.variantRecords.size() - 1;
        }
        FixedRecord personFixedRecord = recordGroup.variantRecords.get(recordToPull);
        FixedRecord rawFixedRecord;
        
        // If the person has more HealthRecords than FixedRecords, use the last FixedRecord.
        if (fixedRecordGroupManager.getRecordGroup(p).variantRecords.size() <= recordNum) {
          rawFixedRecord = fixedRecordGroupManager.getRecordGroup(p).variantRecords.get(recordNum - 1);
        } else {
          rawFixedRecord = fixedRecordGroupManager.getRecordGroup(p).variantRecords.get(recordNum);
        }
        
        // Compare the person's current FixedRecord with the raw imported FixedRecords.
        assertEquals(personFixedRecord.firstName, rawFixedRecord.firstName);
        assertEquals(personFixedRecord.lastName, rawFixedRecord.lastName);
        assertEquals(personFixedRecord.getBirthDate(), rawFixedRecord.getBirthDate());
        assertEquals(personFixedRecord.gender, rawFixedRecord.gender);
        assertEquals(personFixedRecord.phoneAreaCode, rawFixedRecord.phoneAreaCode);
        assertEquals(personFixedRecord.phoneNumber, rawFixedRecord.phoneNumber);
        assertEquals(personFixedRecord.addressLineOne, rawFixedRecord.addressLineOne);
        assertEquals(personFixedRecord.addressLineTwo, rawFixedRecord.addressLineTwo);
        assertEquals(personFixedRecord.city, rawFixedRecord.city);
        assertEquals(personFixedRecord.zipcode, rawFixedRecord.zipcode);
        assertEquals(personFixedRecord.parentFirstName, rawFixedRecord.parentFirstName);
        assertEquals(personFixedRecord.parentLastName, rawFixedRecord.parentLastName);
        assertEquals(personFixedRecord.parentEmail, rawFixedRecord.parentEmail);
    }
  }
  }

  @Test
  public void variantRecordCityIsInvalid() {
    // First, set the current fixed record to the 2015 variant record.
    assertTrue(fixedRecordGroupManager.getRecordGroup(0).updateCurrentRecord(2015));
    // The first person's 2015 variant record has an invalid city, return the seed city instead.
    String validCity = fixedRecordGroupManager.getRecordGroup(0).getSafeCurrentCity();
    String invalidCity = fixedRecordGroupManager.getRecordGroup(0).getCurrentRecord().city;
    assertEquals("Thornton", validCity);
    assertEquals("INVALID_CITY_NAME", invalidCity);
    assertEquals(validCity, fixedRecordGroupManager.getRecordGroup(0).getSeedCity());
    assertEquals(validCity, fixedRecordGroupManager.getRecordGroup(0).seedRecord.getSafeCity());
    // If a fixed record has an invalid city, safecity should return null.
    assertEquals(null, fixedRecordGroupManager.getRecordGroup(0).getCurrentRecord().getSafeCity());
  }
}