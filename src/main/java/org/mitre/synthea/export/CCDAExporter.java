package org.mitre.synthea.export;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.StringWriter;
import java.util.UUID;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.RaceAndEthnicity;

/**
 * Export C-CDA R2.1 files using Apache FreeMarker templates.
 */
public class CCDAExporter {

  private static final Configuration TEMPLATES = templateConfiguration();
  /**
   * This is a dummy object for FreeMarker, because the library cannot access static class methods
   * such as UUID.randomUUID()
   */
  private static final Object UUID_GEN = new Object() {
    public String toString() {
      return UUID.randomUUID().toString();
    }
  };

  private static Configuration templateConfiguration() {
    Configuration configuration = new Configuration(Configuration.VERSION_2_3_26);
    configuration.setDefaultEncoding("UTF-8");
    configuration.setLogTemplateExceptions(false);
    try {
      configuration.setSetting("object_wrapper",
          "DefaultObjectWrapper(2.3.26, forceLegacyNonListCollections=false, "
              + "iterableSupport=true, exposeFields=true)");
    } catch (TemplateException e) {
      e.printStackTrace();
    }
    configuration.setAPIBuiltinEnabled(true);
    configuration.setClassLoaderForTemplateLoading(ClassLoader.getSystemClassLoader(),
        "templates/ccda");
    return configuration;
  }

  /**
   * Export a CCDA R2.1 document for a Person at a given time.
   *
   * @param person
   *          Person to export.
   * @param time
   *          Time the record should be generated. Any content in the record AFTER this time will
   *          not be included.
   * @return String of CCDA R2.1 XML.
   */
  public static String export(Person person, long time) {
    // create a super encounter... this makes it easier to access
    // all the Allergies (for example) in the export templates,
    // instead of having to iterate through all the encounters.
    Encounter superEncounter = person.record.new Encounter(time, "super");
    for (Encounter encounter : person.record.encounters) {
      if (encounter.start <= time) {
        superEncounter.observations.addAll(encounter.observations);
        superEncounter.reports.addAll(encounter.reports);
        superEncounter.conditions.addAll(encounter.conditions);
        superEncounter.allergies.addAll(encounter.allergies);
        superEncounter.procedures.addAll(encounter.procedures);
        superEncounter.immunizations.addAll(encounter.immunizations);
        superEncounter.medications.addAll(encounter.medications);
        superEncounter.careplans.addAll(encounter.careplans);
        superEncounter.imagingStudies.addAll(encounter.imagingStudies);
      } else {
        break;
      }
    }
    Observation smoking_history = person.record.getLatestObservation("72166-2");

    // The export templates fill in the record by accessing the attributes
    // of the Person, so we add a few attributes just for the purposes of export.
    person.attributes.put("UUID", UUID_GEN);
    person.attributes.put("ehr_encounters", person.record.encounters);
    person.attributes.put("ehr_observations", superEncounter.observations);
    person.attributes.put("ehr_reports", superEncounter.reports);
    person.attributes.put("ehr_conditions", superEncounter.conditions);
    person.attributes.put("ehr_allergies", superEncounter.allergies);
    person.attributes.put("ehr_procedures", superEncounter.procedures);
    person.attributes.put("ehr_immunizations", superEncounter.immunizations);
    person.attributes.put("ehr_medications", superEncounter.medications);
    person.attributes.put("ehr_careplans", superEncounter.careplans);
    person.attributes.put("ehr_imaging_studies", superEncounter.imagingStudies);
    // person.attributes.put("ehr_social_history", social_history);
    person.attributes.put("ehr_smoking_history", smoking_history);
    // person.attributes.put("ehr_medical_equipment", );
    person.attributes.put("time", time);
    person.attributes.put("race_lookup", RaceAndEthnicity.LOOK_UP_CDC_RACE);
    person.attributes.put("ethnicity_lookup", RaceAndEthnicity.LOOK_UP_CDC_ETHNICITY_CODE);
    person.attributes.put("ethnicity_display_lookup",
        RaceAndEthnicity.LOOK_UP_CDC_ETHNICITY_DISPLAY);

    StringWriter writer = new StringWriter();
    try {
      Template template = TEMPLATES.getTemplate("ccda.ftl");
      template.process(person.attributes, writer);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return writer.toString();
  }
}
