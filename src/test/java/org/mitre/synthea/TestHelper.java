package org.mitre.synthea;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.modules.EncounterModule;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.Costs;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.geography.Location;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public abstract class TestHelper {

  private static final int TIME_START = -700000000;
  private static final int TIME_STOP = -650000000;


  /**
   * Returns a test fixture Module by filename.
   * @param filename The filename of the test fixture Module.
   * @return A Module.
   * @throws Exception On errors.
   */
  public static Module getFixture(String filename) throws Exception {
    Path modulesFolder = Paths.get("generic");
    Path module = modulesFolder.resolve(filename);
    return Module.loadFile(module, modulesFolder);
  }

  /**
   * Helper method to disable export of all data formats and database output.
   * Ensures that unit tests do not pollute the output folders.
   */
  public static void exportOff() {
    Config.set("generate.database_type", "none"); // ensure we don't write to a file-based DB
    Config.set("exporter.use_uuid_filenames", "false");
    Config.set("exporter.fhir.use_shr_extensions", "false");
    Config.set("exporter.subfolders_by_id_substring", "false");
    Config.set("exporter.ccda.export", "false");
    Config.set("exporter.fhir_stu3.export", "false");
    Config.set("exporter.fhir_dstu2.export", "false");
    Config.set("exporter.fhir.export", "false");
    Config.set("exporter.fhir.transaction_bundle", "false");
    Config.set("exporter.text.export", "false");
    Config.set("exporter.text.per_encounter_export", "false");
    Config.set("exporter.csv.export", "false");
    Config.set("exporter.cpcds.export", "false");
    Config.set("exporter.healthsparq.cpcds.export", "false");
    Config.set("exporter.cdw.export", "false");
    Config.set("exporter.hospital.fhir_stu3.export", "false");
    Config.set("exporter.hospital.fhir_dstu2.export", "false");
    Config.set("exporter.hospital.fhir.export", "false");
    Config.set("exporter.practitioner.fhir_stu3.export", "false");
    Config.set("exporter.practitioner.fhir_dstu2.export", "false");
    Config.set("exporter.practitioner.fhir.export", "false");
    Config.set("exporter.cost_access_outcomes_report", "false");
  }

  public static long timestamp(int year, int month, int day, int hr, int min, int sec) {
    return LocalDateTime.of(year, month, day, hr, min, sec).toInstant(ZoneOffset.UTC)
        .toEpochMilli();
  }

  /**
   * Creates a mock person with chosen gender and dead/alive status.
   * @param sex gender to be of the mock person "M" or "F"
   * @param dead true for a dead person, false for alive
   * @return mock person of specified gender and dead/alive status
   */
  public static Person generateMockPerson(String sex, boolean dead) {
    Person person = new Person(1);
    person.attributes.put(Person.ID, "123");
    person.attributes.put(Person.GENDER, sex);
    person.attributes.put(Person.FIRST_NAME, "Test_First_Name");
    person.attributes.put(Person.LAST_NAME, "Test_Last_Name");
    person.attributes.put(Person.NAME_SUFFIX, "JR.");
    person.attributes.put(Person.ETHNICITY, "irish");
    person.attributes.put(Person.RACE, "white");
    person.attributes.put("county", "Test_County");
    person.attributes.put(Person.STATE, "New York");
    person.attributes.put(Person.NAME, "Test_Name");
    person.attributes.put(Person.BIRTHDATE, Long.valueOf("-900000000"));
    if (dead) {
      person.attributes.put(Person.DEATHDATE, Long.valueOf("1572010260343"));
    }
    person.attributes.put(Person.ZIP, "30905");

    return person;
  }

  /**
   * Generates a mock encounter with a mock (male) person, claim with codes, and one claim item.
   * @param payer insurance to use to calculate claim amounts
   * @return mock encounter with a person with no insurance
   */
  public static HealthRecord.Encounter generateMockEncounter(Payer payer) {
    Costs.loadCostData();
    Person person = generateMockPerson("M", false);
    person.setPayerAtAge(0, payer);
    HealthRecord healthRecord = new HealthRecord(person);
    HealthRecord.Encounter encounter = healthRecord.new Encounter(TIME_START, "Test_Encounter");
    encounter.stop = TIME_STOP;
    encounter.codes.add(0, EncounterModule.ENCOUNTER_CHECKUP);
    Claim claim = new Claim(encounter, person);
    claim.person = person;
    claim.payer = payer;
    claim.addLineItem(generateProcedure(healthRecord));
    Provider provider = new Provider();
    provider.id = "1000";
    encounter.claim = claim;
    encounter.provider = provider;
    encounter.clinician = new Clinician(1, person.random, Long.parseLong("1000"), provider);
    return encounter;
  }

  /**
   * Generates a mock procedure with a set time and type of "Test_Type."
   * @param healthRecord The health record information that will be added to and used
   * @return a new mock procedure with mock list of codes
   */
  private static HealthRecord.Procedure generateProcedure(HealthRecord healthRecord) {
    HealthRecord.Procedure procedure = healthRecord.new Procedure(TIME_START, "Test_Type");
    procedure.stop = TIME_STOP;
    procedure.codes.addAll(generateCodes());
    return procedure;
  }

  /**
   * Generates a list of codes to simulate multiple diagnosis or procedure codes.
   * @return list of test codes with displays and system types
   */
  public static List<HealthRecord.Code> generateCodes() {
    List<HealthRecord.Code> codeList = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      codeList.add(new HealthRecord.Code("Test_System" + i,
          "Test_Code" + i,
          "Test_Display" + i));
    }
    return codeList;
  }

  /**
   * Loads and give no insurance payer. Clears all other insurance for test.
   * @return Payer object for no insurance
   */
  public static Payer getNoInsurancePayer() {
    Payer.clear();
    Payer.loadNoInsurance();
    return Payer.noInsurance;
  }

  /**
   * Based on @before in PayerTest.java.  Loads test payers and selects a mock private
   * payer that has copay and covers all entries. Also clears insurance for tests.
   * @return Payer that has copays and covers a portion of the cost
   */
  public static Payer getMockPayer() {
    // Clear any Payers that may have already been statically loaded.
    Payer.clear();
    Config.set("generate.payers.insurance_companies.default_file",
        "generic/payers/test_payers.csv");
    // Load in the .csv list of Payers for MA.
    Payer.loadPayers(new Location("Massachusetts", null));

    return Payer.getPrivatePayers().get(0);
  }
}
