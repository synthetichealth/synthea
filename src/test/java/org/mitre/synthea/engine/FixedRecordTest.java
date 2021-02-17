package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.engine.Generator.GeneratorOptions;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.input.FixedRecord;
import org.mitre.synthea.input.FixedRecordGroup;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;

public class FixedRecordTest {

  // The generator.
  private Generator generator;

  /**
   * Configure settings across these tests.
   * @throws Exception on test configuration loading errors.
   */
  @Before
  public void setup() {
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
    generator = new Generator(go);
    generator.internalStore = new LinkedList<>(); // Allows us to access patients within generator.
  }

  @Test
  public void fixedDemographicsImportTest() {

    // List of raw RecordGroups imported directly from the input file for later comparison.
    List<FixedRecordGroup> rawRecordGroups = generator.importFixedPatientDemographicsFile();
    
    // Generate each patient from the fixed record input file.
    for (int i = 0; i < generator.options.population; i++) {
      generator.generatePerson(i);
    }

    // Make sure that the correct number of people were imported from the fixed records.
    assertEquals(4, generator.internalStore.size());
    assertEquals(generator.internalStore.size(), rawRecordGroups.size());

    // Check that each person has HealthRecords that match their fixed demographic records.
    for (int p = 0; p < generator.internalStore.size(); p++) {
      // Get the current person and pull their list of records.
      Person currentPerson = generator.internalStore.get(p);
      FixedRecordGroup recordGroup
          = (FixedRecordGroup) currentPerson.attributes.get(Person.RECORD_GROUP);
      // Make sure the person has the correct number of records.
      assertTrue(currentPerson.records.size() >= 3);
      assertTrue(recordGroup.records.size() == 3);
      // Track the number of fixed records that match the person's attributes exactly.
      int fixedRecordMatches = 0;
      // Cycle the person's FixedRecords to compare them to the raw imported FixedRecords.
      for (int r = 0; r < currentPerson.records.size(); r++) {        

        int recordToPull = Math.min(r, recordGroup.count - 1);
        FixedRecord personFixedRecord = recordGroup.records.get(recordToPull);

        recordToPull = Math.min(r, rawRecordGroups.get(p).records.size() - 1);
        FixedRecord rawFixedRecord = rawRecordGroups.get(p).records.get(recordToPull);
        
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

        // One FixedRecord should match the person's attributes exactly as the "gold standard".
        if (
            (currentPerson.attributes.get(Person.FIRST_NAME).equals(rawFixedRecord.firstName))
            && (currentPerson.attributes.get(Person.LAST_NAME).equals(rawFixedRecord.lastName))
            && (currentPerson.attributes.get(Person.ADDRESS).equals(rawFixedRecord.addressLineOne))
            && (currentPerson.attributes.get(Person.BIRTHDATE)
                .equals(rawFixedRecord.getBirthDate()))
            && (currentPerson.attributes.get(Person.GENDER).equals(rawFixedRecord.gender))
            && (currentPerson.attributes.get(Person.TELECOM).equals(rawFixedRecord.getTelecom()))
            && (currentPerson.attributes.get(Person.STATE).equals(rawFixedRecord.state))
            && (currentPerson.attributes.get(Person.CITY).equals(rawFixedRecord.city))
            && (currentPerson.attributes.get(Person.ZIP).equals(rawFixedRecord.zipcode))
            && (currentPerson.attributes.get(Person.IDENTIFIER_RECORD_ID)
                .equals(rawFixedRecord.recordId))
            && (currentPerson.attributes.get(Person.IDENTIFIER_SITE).equals(rawFixedRecord.site))
            ) {
          fixedRecordMatches++;
        }
      }
      // One FixedRecord should match the person's attributes exactly as a "gold standard" record.
      assertTrue(fixedRecordMatches >= 1);
    }
  }
}