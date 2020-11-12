package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;

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
    for (int i = 0; i < generator.options.population; i++) {
      generator.generatePerson(i);
    }    
  }

  @Test
  public void fixedDemographicsImportTest() {
    
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
      // The seed fixed record should match the person's initial attributes exactly.
      FixedRecord seedRecord = recordGroup.seedRecord;
      assertEquals(currentPerson.attributes.get(Person.FIRST_NAME), (seedRecord.firstName));
      assertEquals(currentPerson.attributes.get(Person.LAST_NAME), (seedRecord.lastName));
      assertEquals(currentPerson.attributes.get(Person.ADDRESS), (seedRecord.addressLineOne));
      assertEquals(currentPerson.attributes.get(Person.BIRTHDATE), (seedRecord.getBirthDate()));
      assertEquals(currentPerson.attributes.get(Person.GENDER), (seedRecord.gender));
      assertEquals(currentPerson.attributes.get(Person.TELECOM), (seedRecord.getTelecom()));
      assertEquals(currentPerson.attributes.get(Person.STATE), (seedRecord.state));
      assertEquals(currentPerson.attributes.get(Person.CITY), (seedRecord.city));
      assertEquals(currentPerson.attributes.get(Person.ZIP), (seedRecord.zipcode));
          
      // Cycle the person's FixedRecords to compare them to the raw imported FixedRecords.
      for (int r = 0; r < currentPerson.records.size(); r++) {

        int recordToPull = r;
        if (recordToPull >= recordGroup.variantRecords.size()) {
          recordToPull = recordGroup.variantRecords.size() - 1;
        }
        FixedRecord personFixedRecord = recordGroup.variantRecords.get(recordToPull);
        FixedRecord rawFixedRecord;
        
        // If the person has more HealthRecords than FixedRecords, use the last FixedRecord.
        if (fixedRecordGroupManager.getRecordGroup(p).variantRecords.size() <= r) {
          rawFixedRecord = fixedRecordGroupManager.getRecordGroup(p).variantRecords.get(r - 1);
        } else {
          rawFixedRecord = fixedRecordGroupManager.getRecordGroup(p).variantRecords.get(r);
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
    // The first person's 2015 variant record has an invalid city, return the seed city instead.
    String validCity = fixedRecordGroupManager.getRecordGroup(0).getCurrentCity(2015);
    String invalidCity = fixedRecordGroupManager.getRecordGroup(0).getCurrentFixedRecord(2015).city;
    assertEquals("Thornton", validCity);
    assertEquals("INVALID_CITY_NAME", invalidCity);
    assertEquals(validCity, fixedRecordGroupManager.getRecordGroup(0).getSeedCity());
    assertEquals(validCity, fixedRecordGroupManager.getRecordGroup(0).seedRecord.getSafeCity());
    // If a fixed record has an invalid city, safecity should return null.
    assertEquals(null, fixedRecordGroupManager.getRecordGroup(0).getCurrentFixedRecord(2015).getSafeCity());
  }
}