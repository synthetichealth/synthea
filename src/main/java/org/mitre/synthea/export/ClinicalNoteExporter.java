package org.mitre.synthea.export;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;

import org.mitre.synthea.modules.LifecycleModule;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;
import org.mitre.synthea.world.concepts.RaceAndEthnicity;

/**
 * Export Clinical Notes using Apache FreeMarker templates.
 */
public class ClinicalNoteExporter {

  private static final Configuration TEMPLATES = templateConfiguration();

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
        "templates/notes");
    return configuration;
  }

  /**
   * Export all the encounter notes for a Person in a single
   * document, with the most recent encounter on top.
   *
   * @param person Person to write notes about.
   * @return A set of consolidated clinical notes as plain text.
   */
  public static String export(Person person) {
    String consolidatedNotes = "";
    for (int i = person.record.encounters.size() - 1; i >= 0; i--) {
      Encounter encounter = person.record.encounters.get(i);
      consolidatedNotes += export(person, encounter) + "\n\n";
    }
    return consolidatedNotes;
  }

  /**
   * Export a clinical note for a Person at a given Encounter.
   *
   * @param person Person to write a note about.
   * @param encounter Encounter to write a note about.
   * @return Clinical note as a plain text string.
   */
  public static String export(Person person, Encounter encounter) {
    // The export templates fill in the record by accessing the attributes
    // of the Person, so we add a few attributes just for the purposes of export.
    Set<String> activeAllergies = new HashSet<String>();
    Set<String> activeConditions = new HashSet<String>();
    Set<String> activeMedications = new HashSet<String>();
    Set<String> activeProcedures = new HashSet<String>();

    // need to loop through record until THIS encounter
    // to get previous data, since "present" is what is present
    // at time of export and NOT what is present at this
    // encounter.
    long encounterTime = encounter.start;
    for (Encounter pastEncounter : person.record.encounters) {
      if (pastEncounter == encounter || pastEncounter.stop >= encounterTime) {
        break;
      }
      for (Entry allergy : pastEncounter.allergies) {
        if (allergy.stop != 0L || allergy.stop > encounterTime) {
          activeAllergies.add(allergy.codes.get(0).display);
        }
      }
      for (Entry condition : pastEncounter.conditions) {
        if (condition.stop != 0L || condition.stop > encounterTime) {
          activeConditions.add(condition.codes.get(0).display);
        }
      }
      for (Medication medication : pastEncounter.medications) {
        if (medication.stop != 0L || medication.stop > encounterTime) {
          activeMedications.add(medication.codes.get(0).display);
        }
      }
      for (Procedure procedure : pastEncounter.procedures) {
        if (procedure.stop != 0L || procedure.stop > encounterTime) {
          activeProcedures.add(procedure.codes.get(0).display);
        }
      }
    }

    person.attributes.put("ehr_insurance", person.getPayerAtTime(encounter.start).getName());
    person.attributes.put("ehr_ageInYears", person.ageInYears(encounter.start));
    person.attributes.put("ehr_ageInMonths", person.ageInMonths(encounter.start));
    person.attributes.put("ehr_symptoms", person.getSymptoms());
    person.attributes.put("ehr_activeAllergies", activeAllergies);
    person.attributes.put("ehr_activeConditions", activeConditions);
    if (activeConditions.contains("Normal pregnancy")) {
      person.attributes.put("pregnant", true);
    } else {
      person.attributes.remove("pregnant");
    }
    person.attributes.put("ehr_activeMedications", activeMedications);
    person.attributes.put("ehr_activeProcedures", activeProcedures);
    person.attributes.put("ehr_conditions", encounter.conditions);
    person.attributes.put("ehr_allergies", encounter.allergies);
    person.attributes.put("ehr_procedures", encounter.procedures);
    person.attributes.put("ehr_immunizations", encounter.immunizations);
    person.attributes.put("ehr_medications", encounter.medications);
    person.attributes.put("ehr_careplans", encounter.careplans);
    person.attributes.put("ehr_imaging_studies", encounter.imagingStudies);
    person.attributes.put("time", encounter.start);
    if (person.attributes.containsKey(LifecycleModule.QUIT_SMOKING_AGE)) {
      person.attributes.put("quit_smoking_age", 
          person.attributes.get(LifecycleModule.QUIT_SMOKING_AGE));      
    }
    person.attributes.put("race_lookup", RaceAndEthnicity.LOOK_UP_CDC_RACE);
    person.attributes.put("ethnicity_lookup", RaceAndEthnicity.LOOK_UP_CDC_ETHNICITY_CODE);
    person.attributes.put("ethnicity_display_lookup",
        RaceAndEthnicity.LOOK_UP_CDC_ETHNICITY_DISPLAY);

    StringWriter writer = new StringWriter();
    try {
      Template template = TEMPLATES.getTemplate("note.ftl");
      template.process(person.attributes, writer);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return writer.toString();
  }
}
