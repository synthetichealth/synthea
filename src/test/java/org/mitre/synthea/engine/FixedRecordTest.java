package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.engine.Generator.GeneratorOptions;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.input.FixedRecord;
import org.mitre.synthea.input.RecordGroup;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;

public class FixedRecordTest {

  // The generator.
  private static Generator generator;

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
    go.fixedRecordPath = new File("fixed_demographics_test.json");  // The file path of the test fixed demographics records.
    go.state = "Colorado";  // Examples are based on Colorado.
    go.population = 100;  // Setting to 100, but should be overwritten by number of patients in input file.
    generator = new Generator(go);
    generator.internalStore = new LinkedList<>(); // Allows us to access patients within generator.
  }

  @Test
  public void fixedDemographicsImportTest() {

      // List of RecordGroups imported directly from the input file.
      List<RecordGroup> rawRecordGroups = generator.importFixedPatientDemographicsFile();
      
      // Generate each patient from the fixed record input file.
      for (int i = 0; i < generator.options.population; i++) {
        generator.generatePerson(i);
      }

      // Make sure that the correct number of people were imported from the fixed records.
      assertEquals(4, generator.internalStore.size());
      assertEquals(generator.internalStore.size(), rawRecordGroups.size());

      // Check that each person matches their fixed demographic records.
      for (int p = 0; p < generator.internalStore.size(); p++) {
        // Get the current person.
        Person currentPerson = generator.internalStore.get(p);
        // Pull the person's group of FixedRecords.
        RecordGroup recordGroup = (RecordGroup) currentPerson.attributes.get(Person.RECORD_GROUP);
        // Make sure the person has the correct number of records, for this input file each person has 3 records.
        assertTrue(currentPerson.records.size() >= 3);
        assertTrue(recordGroup.records.size() == 3);
        // Track the number of fixed records that match the person's attributes exactly.
        int fixedRecordMatches = 0;
        // Cycle through the person's HealthRecords to compare the associated FixedRecord with the raw imported FixedRecords.
        for (int r = 0; r < currentPerson.records.size(); r++) {        

          int recordToPull = r;
          if (recordToPull >= recordGroup.count) {
            recordToPull = recordGroup.count - 1;
          }
          FixedRecord personFixedRecord = recordGroup.records.get(recordToPull);
          FixedRecord rawFixedRecord;
          
          // If the person has more HealthRecords than FixedRecords, use the last FixedRecord in the list to test against.
          if (rawRecordGroups.get(p).records.size() <= r) {
            rawFixedRecord = rawRecordGroups.get(p).records.get(r-1);
          } else {
            rawFixedRecord = rawRecordGroups.get(p).records.get(r);
          }
          
          // Compare the person's current FixedRecord with the raw imported FixedRecords.
          assertEquals(personFixedRecord.firstName, rawFixedRecord.firstName);
          assertEquals(personFixedRecord.lastName, rawFixedRecord.lastName);
          assertEquals(personFixedRecord.getBirthDate(true), rawFixedRecord.getBirthDate(true));
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

          // At least one FixedRecord should match the person's attributes exactly as the "gold standard" record.
          if (
            (currentPerson.attributes.get(Person.FIRST_NAME).equals(rawFixedRecord.firstName)) &&
            (currentPerson.attributes.get(Person.LAST_NAME).equals(rawFixedRecord.lastName)) &&
            //(currentPerson.attributes.get(Person.ADDRESS).equals(rawFixedRecord.addressLineOne)) &&
            (currentPerson.attributes.get(Person.BIRTHDATE).equals(rawFixedRecord.getBirthDate(true))) &&
            (currentPerson.attributes.get(Person.GENDER).equals(rawFixedRecord.gender)) &&
            (currentPerson.attributes.get(Person.CITY).equals(rawFixedRecord.city)) &&
            (currentPerson.attributes.get(Person.ZIP).equals(rawFixedRecord.zipcode))
            ) {
              fixedRecordMatches++;
          }
        }
        // At least one FixedRecord should match the person's attributes exactly as the "gold standard" record.
        assertTrue(fixedRecordMatches >= 1);
      }
  }
  
}