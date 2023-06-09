package org.mitre.synthea.export;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.Serializable;
import java.io.StringWriter;
import java.util.List;
import java.util.stream.Collectors;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomNumberGenerator;

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
   * This is a dummy class and object for FreeMarker templates that create IDs.
   */
  private static class UUIDGenerator implements Serializable {
    private RandomNumberGenerator rand;

    public UUIDGenerator(RandomNumberGenerator rand) {
      this.rand = rand;
    }

    @Override
    public String toString() {
      return rand.randUUID().toString();
    }
  }

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
    try {
      person.coverage.getPlanRecordAtTime(time);
    } catch (RuntimeException e) {
      // If requesting the current plan at export time
      // causes an exception, then we fake an insurance
      // plan for the purposes of creating the super encounter.
      person.coverage.setPlanToNoInsurance(time);
      person.coverage.setPlanToNoInsurance(Long.MAX_VALUE);
    }
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

    // The export templates fill in the record by accessing the attributes
    // of the Person, so we add a few attributes just for the purposes of export.
    person.attributes.put("UUID", new UUIDGenerator(person));
    person.attributes.put("ehr_encounters", person.record.encounters);
    person.attributes.put("ehr_conditions", superEncounter.conditions);
    person.attributes.put("ehr_allergies", superEncounter.allergies);
    person.attributes.put("ehr_procedures", superEncounter.procedures);
    person.attributes.put("ehr_immunizations", superEncounter.immunizations);
    person.attributes.put("ehr_medications", superEncounter.medications);
    person.attributes.put("ehr_careplans", superEncounter.careplans);

    List<Observation> vitalSigns = superEncounter.observations
            .stream()
            .filter(vs -> vs.category != null && vs.category.equals("vital-signs"))
            .filter(vs -> vs.value != null)
            .collect(Collectors.toList());

    person.attributes.put("ehr_vital_signs", vitalSigns);

    List<Observation> surveyResults = superEncounter.observations
            .stream()
            .filter(vs -> vs.category != null && vs.category.equals("survey"))
            .filter(vs -> vs.value != null && vs.value instanceof Double)
            .collect(Collectors.toList());

    // sadly, the correct plural of status is statuses and not stati
    person.attributes.put("ehr_functional_statuses", surveyResults);

    person.attributes.put("ehr_results", superEncounter.reports);

    Observation smokingHistory = person.record.getLatestObservation("72166-2");

    if (smokingHistory != null) {
      person.attributes.put("ehr_smoking_history", smokingHistory);
    }
    person.attributes.put("time", time);
    person.attributes.put("race_lookup", RaceAndEthnicity.LOOK_UP_CDC_RACE);
    person.attributes.put("ethnicity_lookup", RaceAndEthnicity.LOOK_UP_CDC_ETHNICITY_CODE);
    person.attributes.put("ethnicity_display_lookup",
        RaceAndEthnicity.LOOK_UP_CDC_ETHNICITY_DISPLAY);

    if (person.attributes.get(Person.PREFERREDYPROVIDER + "wellness") == null) {
      // This person does not have a preferred provider. This happens for veterans at age 20 due to
      // the provider reset and they don't have a provider until their next wellness visit. There
      // may be other cases. This ensures the preferred provider is there for the CCDA template
      Encounter encounter = person.record.lastWellnessEncounter();
      if (encounter == null) {
        // If there are absolutely no wellness encounters, then use the last encounter.
        encounter = person.record.encounters.get(person.record.encounters.size() - 1);
      }
      if (encounter != null) {
        person.attributes.put(Person.PREFERREDYPROVIDER + "wellness", encounter.provider);
      } else {
        throw new IllegalStateException(String.format("Unable to export to CCDA because "
            + "person %s %s has no preferred provider.",
            person.attributes.get(Person.FIRST_NAME),
            person.attributes.get(Person.LAST_NAME)));
      }
    }

    StringWriter writer = new StringWriter();
    try {
      Template template = TEMPLATES.getTemplate("ccda.ftl");
      template.process(person.attributes, writer);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    String ccdaXml = writer.toString();
    if (!Config.getAsBoolean("exporter.pretty_print", true)) {
      ccdaXml = ccdaXml.replace("\n", "");
    }
    return ccdaXml;
  }
}