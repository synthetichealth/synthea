package org.mitre.synthea.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;

import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.DeathModule;
import org.mitre.synthea.writer.AWSS3Writer;
import org.mitre.synthea.writer.FileSystemWriter;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.HealthRecord.Report;

public abstract class Exporter {
  /**
   * Export a single patient, into all the formats supported. (Formats may be
   * enabled or disabled by configuration)
   *
   * @param person   Patient to export
   * @param stopTime Time at which the simulation stopped
   */
  public static void export(Person person, long stopTime) {
    int yearsOfHistory = Integer.parseInt(Config.get("exporter.years_of_history"));
    if (yearsOfHistory > 0) {
      person = filterForExport(person, yearsOfHistory, stopTime);
    }
    if (person.hasMultipleRecords) {
      int i = 0;
      for (String key : person.records.keySet()) {
        person.record = person.records.get(key);
        exportRecord(person, Integer.toString(i), stopTime);
        i++;
      }
    } else {
      exportRecord(person, "", stopTime);
    }
  }

  /**
   * Export a single patient record, into all the formats supported. (Formats may
   * be enabled or disabled by configuration)
   *
   * @param person   Patient to export, with Patient.record being set.
   * @param fileTag  An identifier to tag the file with.
   * @param stopTime Time at which the simulation stopped
   */
  private static void exportRecord(Person person, String fileTag, long stopTime) {

    if (Boolean.parseBoolean(Config.get("exporter.fhir_stu3.export"))) {
      String folderName = "fhir_stu3";

      if (Boolean.parseBoolean(Config.get("exporter.fhir.bulk_data"))) {
        org.hl7.fhir.dstu3.model.Bundle bundle = FhirStu3.convertToFHIR(person, stopTime);
        IParser parser = FhirContext.forDstu3().newJsonParser().setPrettyPrint(false);
        Set<String> fileNames = new HashSet<>();
        for (org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent entry : bundle.getEntry()) {
          String entryJson = parser.encodeResourceToString(entry.getResource());
          String fileName = entry.getResource().getResourceType().toString() + ".ndjson";
          fileNames.add(fileName);
          if (Boolean.parseBoolean(Config.get("exporter.upload_directly_to_aws_s3"))) {
            AWSS3Writer.appendToFile(folderName, fileName, entryJson);
          } else {
            File outDirectory = FileSystemWriter.getOutputFolder(folderName, person);
            FileSystemWriter.appendToFile(outDirectory, fileName, entryJson);
          }
        }
        if (Boolean.parseBoolean(Config.get("exporter.upload_directly_to_aws_s3"))) {
          cleanupTempFile(folderName, fileNames);
        }
      } else {
        String bundleJson = FhirStu3.convertToFHIRJson(person, stopTime);
        String fileName = filename(person, fileTag, "json");
        if (Boolean.parseBoolean(Config.get("exporter.upload_directly_to_aws_s3"))) {
          AWSS3Writer.writeNewFile(folderName, fileName, bundleJson);
        } else {
          File outDirectory = FileSystemWriter.getOutputFolder(folderName, person);
          FileSystemWriter.writeNewFile(outDirectory, fileName, bundleJson);
        }
      }
    }
    if (Boolean.parseBoolean(Config.get("exporter.fhir_dstu2.export"))) {
      String folderName = "fhir_dstu2";
      if (Boolean.parseBoolean(Config.get("exporter.fhir.bulk_data"))) {
        ca.uhn.fhir.model.dstu2.resource.Bundle bundle = FhirDstu2.convertToFHIR(person, stopTime);
        IParser parser = FhirContext.forDstu2().newJsonParser().setPrettyPrint(false);
        Set<String> fileNames = new HashSet<>();
        for (ca.uhn.fhir.model.dstu2.resource.Bundle.Entry entry : bundle.getEntry()) {
          String entryJson = parser.encodeResourceToString(entry.getResource());
          String fileName = entry.getResource().getResourceName() + ".ndjson";
          fileNames.add(fileName);
          if (Boolean.parseBoolean(Config.get("exporter.upload_directly_to_aws_s3"))) {
            AWSS3Writer.appendToFile(folderName, fileName, entryJson);
          } else {
            File outDirectory = FileSystemWriter.getOutputFolder(folderName, person);
            FileSystemWriter.appendToFile(outDirectory, fileName, entryJson);
          }
        }
        if (Boolean.parseBoolean(Config.get("exporter.upload_directly_to_aws_s3"))) {
          cleanupTempFile(folderName, fileNames);
        }
      } else {
        String bundleJson = FhirDstu2.convertToFHIRJson(person, stopTime);
        String fileName = filename(person, fileTag, "json");
        if (Boolean.parseBoolean(Config.get("exporter.upload_directly_to_aws_s3"))) {
          AWSS3Writer.writeNewFile(folderName, fileName, bundleJson);
        } else {
          File outDirectory = FileSystemWriter.getOutputFolder(folderName, person);
          FileSystemWriter.writeNewFile(outDirectory, fileName, bundleJson);
        }
      }
    }
    if (Boolean.parseBoolean(Config.get("exporter.fhir.export"))) {
      String folderName = "fhir";
      if (Boolean.parseBoolean(Config.get("exporter.fhir.bulk_data"))) {
        org.hl7.fhir.r4.model.Bundle bundle = FhirR4.convertToFHIR(person, stopTime);
        IParser parser = FhirContext.forR4().newJsonParser().setPrettyPrint(false);
        Set<String> fileNames = new HashSet<>();
        for (org.hl7.fhir.r4.model.Bundle.BundleEntryComponent entry : bundle.getEntry()) {
          String entryJson = parser.encodeResourceToString(entry.getResource());
          String fileName = entry.getResource().getResourceType().toString() + ".ndjson";
          fileNames.add(fileName);
          File outDirectory = FileSystemWriter.getOutputFolder(folderName, person);
          FileSystemWriter.appendToFile(outDirectory, fileName, entryJson);
        }
        if (Boolean.parseBoolean(Config.get("exporter.upload_directly_to_aws_s3"))) {
          cleanupTempFile(folderName, fileNames);
        }
      } else {
        String bundleJson = FhirR4.convertToFHIRJson(person, stopTime);
        String fileName = filename(person, fileTag, "json");
        if (Boolean.parseBoolean(Config.get("exporter.upload_directly_to_aws_s3"))) {
          AWSS3Writer.writeNewFile(folderName, fileName, bundleJson);
        } else {
          File outDirectory = FileSystemWriter.getOutputFolder(folderName, person);
          FileSystemWriter.writeNewFile(outDirectory, fileName, bundleJson);
        }
      }
    }
    if (Boolean.parseBoolean(Config.get("exporter.ccda.export"))) {
      String ccdaXml = CCDAExporter.export(person, stopTime);
      String fileName = filename(person, fileTag, "xml");
      String folderName = "ccda";
      if (Boolean.parseBoolean(Config.get("exporter.upload_directly_to_aws_s3"))) {
        AWSS3Writer.writeNewFile(folderName, fileName, ccdaXml);
      } else {
        File outDirectory = FileSystemWriter.getOutputFolder(folderName, person);
        FileSystemWriter.writeNewFile(outDirectory, fileName, ccdaXml);
      }
    }
    if (Boolean.parseBoolean(Config.get("exporter.csv.export"))) {
      try {
        CSVExporter.getInstance().export(person, stopTime);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    if (Boolean.parseBoolean(Config.get("exporter.text.export"))) {
      try {
        TextExporter.exportAll(person, fileTag, stopTime);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    if (Boolean.parseBoolean(Config.get("exporter.text.per_encounter_export"))) {
      try {
        TextExporter.exportEncounter(person, stopTime);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    if (Boolean.parseBoolean(Config.get("exporter.cdw.export"))) {
      try {
        CDWExporter.getInstance().export(person, stopTime);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private static void cleanupTempFile(String folderName, Set<String> fileNames) {
    for (String fileName : fileNames) {
      try {
        Path path = Paths.get(fileName);
        String content = new String(Files.readAllBytes(path));
        AWSS3Writer.writeNewFile(folderName, fileName, content);
        Files.delete(path);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Run any exporters that require the full dataset to be generated prior to
   * exporting. (E.g., an aggregate statistical exporter)
   *
   * @param generator Generator that generated the patients
   */
  public static void runPostCompletionExports(Generator generator) {
    String bulk = Config.get("exporter.fhir.bulk_data");
    Config.set("exporter.fhir.bulk_data", "false");
    try {
      HospitalExporterR4.export(generator.stop);
    } catch (Exception e) {
      e.printStackTrace();
    }

    try {
      FhirPractitionerExporterR4.export(generator.stop);
    } catch (Exception e) {
      e.printStackTrace();
    }

    try {
      HospitalExporterStu3.export(generator.stop);
    } catch (Exception e) {
      e.printStackTrace();
    }

    try {
      FhirPractitionerExporterStu3.export(generator.stop);
    } catch (Exception e) {
      e.printStackTrace();
    }

    try {
      HospitalExporterDstu2.export(generator.stop);
    } catch (Exception e) {
      e.printStackTrace();
    }

    try {
      FhirPractitionerExporterDstu2.export(generator.stop);
    } catch (Exception e) {
      e.printStackTrace();
    }
    Config.set("exporter.fhir.bulk_data", bulk);

    if (Boolean.parseBoolean(Config.get("exporter.cost_access_outcomes_report"))) {
      ReportExporter.export(generator);
    }

    if (Boolean.parseBoolean(Config.get("exporter.prevalence_report"))) {
      try {
        PrevalenceReport.export(generator);
      } catch (Exception e) {
        System.err.println("Prevalence report generation failed!");
        e.printStackTrace();
      }
    }

    if (Boolean.parseBoolean(Config.get("exporter.custom_report"))) {
      try {
        CustomSqlReport.export(generator);
      } catch (Exception e) {
        System.err.println("Custom report generation failed!");
        e.printStackTrace();
      }
    }

    if (Boolean.parseBoolean(Config.get("exporter.cdw.export"))) {
      CDWExporter.getInstance().writeFactTables();
    }

    if (Boolean.parseBoolean(Config.get("exporter.csv.export"))) {
      try {
        CSVExporter.getInstance().exportOrganizationsAndProviders();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Filter the patient's history to only the last __ years but also include
   * relevant history from before that. Exclude any history that occurs after the
   * specified end_time -- typically this is the current
   * time/System.currentTimeMillis().
   *
   * @param original    The Person to filter.
   * @param yearsToKeep The last __ years to keep.
   * @param endTime     The time the history ends.
   * @return Modified Person with history expunged.
   */
  public static Person filterForExport(Person original, int yearsToKeep, long endTime) {
    // TODO: clone the patient so that we export only the last _ years
    // but the rest still exists, just in case
    Person filtered = original; // .clone();
    // filtered.record = original.record.clone();

    if (filtered.hasMultipleRecords) {
      for (String key : filtered.records.keySet()) {
        HealthRecord record = filtered.records.get(key);
        filterForExport(record, yearsToKeep, endTime);
      }
    } else {
      filtered.record = filterForExport(filtered.record, yearsToKeep, endTime);
    }

    return filtered;
  }

  /**
   * Filter the health record to only the last __ years but also include relevant
   * history from before that. Exclude any history that occurs after the specified
   * end_time -- typically this is the current time/System.currentTimeMillis().
   *
   * @param record      The record to filter.
   * @param yearsToKeep The last __ years to keep.
   * @param endTime     The time the history ends.
   * @return Modified record with history expunged.
   */
  private static HealthRecord filterForExport(HealthRecord record, int yearsToKeep, long endTime) {

    long cutoffDate = endTime - Utilities.convertTime("years", yearsToKeep);
    Predicate<HealthRecord.Entry> notFutureDated = e -> e.start <= endTime;

    for (Encounter encounter : record.encounters) {
      List<HealthRecord.Entry> claimItems = encounter.claim.items;
      // keep conditions if still active, regardless of start date
      Predicate<HealthRecord.Entry> conditionActive = c -> record.conditionActive(c.type);
      // or if the condition was active at any point since the cutoff date
      Predicate<HealthRecord.Entry> activeWithinCutoff = c -> c.stop != 0L && c.stop > cutoffDate;
      Predicate<HealthRecord.Entry> keepCondition = conditionActive.or(activeWithinCutoff);
      filterEntries(encounter.conditions, claimItems, cutoffDate, endTime, keepCondition);

      // allergies are essentially the same as conditions
      filterEntries(encounter.allergies, claimItems, cutoffDate, endTime, keepCondition);

      // some of the "future death" logic could potentially add a future-dated death
      // certificate
      Predicate<Observation> isCauseOfDeath = o -> DeathModule.CAUSE_OF_DEATH_CODE.code.equals(o.type);
      // keep cause of death unless it's future dated
      Predicate<Observation> keepObservation = isCauseOfDeath.and(notFutureDated);
      filterEntries(encounter.observations, claimItems, cutoffDate, endTime, keepObservation);

      // keep all death certificates, unless they are future-dated
      Predicate<Report> isDeathCertificate = r -> DeathModule.DEATH_CERTIFICATE.code.equals(r.type);
      Predicate<Report> keepReport = isDeathCertificate.and(notFutureDated);
      filterEntries(encounter.reports, claimItems, cutoffDate, endTime, keepReport);

      filterEntries(encounter.procedures, claimItems, cutoffDate, endTime, null);

      // keep medications if still active, regardless of start date
      filterEntries(encounter.medications, claimItems, cutoffDate, endTime, med -> record.medicationActive(med.type));

      filterEntries(encounter.immunizations, claimItems, cutoffDate, endTime, null);

      // keep careplans if they are still active, regardless of start date
      filterEntries(encounter.careplans, claimItems, cutoffDate, endTime, cp -> record.careplanActive(cp.type));
    }

    // if ANY of these are not empty, the encounter is not empty
    Predicate<Encounter> encounterNotEmpty = e -> !e.conditions.isEmpty() || !e.allergies.isEmpty()
        || !e.observations.isEmpty() || !e.reports.isEmpty() || !e.procedures.isEmpty() || !e.medications.isEmpty()
        || !e.immunizations.isEmpty() || !e.careplans.isEmpty();

    Predicate<Encounter> isDeathCertification = e -> !e.codes.isEmpty()
        && DeathModule.DEATH_CERTIFICATION.equals(e.codes.get(0));
    Predicate<Encounter> keepEncounter = encounterNotEmpty.or(isDeathCertification.and(notFutureDated));

    // finally filter out any empty encounters
    filterEntries(record.encounters, Collections.emptyList(), cutoffDate, endTime, keepEncounter);

    return record;
  }

  /**
   * Helper function to filter entries from a list. Entries are kept if their date
   * range falls within the provided range or if `keepFunction` is provided, and
   * returns `true` for the given entry.
   *
   * @param entries      List of `Entry`s to filter
   * @param claimItems   List of ClaimItems, from which any removed items should
   *                     also be removed.
   * @param cutoffDate   Minimum date, entries older than this may be discarded
   * @param endTime      Maximum date, entries newer than this may be discarded
   * @param keepFunction Keep function, if this function returns `true` for an
   *                     entry then it will be kept
   */
  private static <E extends HealthRecord.Entry> void filterEntries(List<E> entries, List<HealthRecord.Entry> claimItems,
      long cutoffDate, long endTime, Predicate<E> keepFunction) {

    Iterator<E> iterator = entries.iterator();
    // iterator allows us to use the remove() method
    while (iterator.hasNext()) {
      E entry = iterator.next();
      // if the entry is not within the keep time range,
      // and the special keep function (if provided) doesn't say keep it
      // remove it from the list
      if (!entryWithinTimeRange(entry, cutoffDate, endTime) && (keepFunction == null || !keepFunction.test(entry))) {
        iterator.remove();

        claimItems.removeIf(ci -> ci == entry);
        // compare with == because we only care if it's the actual same object
      }
    }
  }

  private static boolean entryWithinTimeRange(HealthRecord.Entry e, long cutoffDate, long endTime) {

    if (e.start > cutoffDate && e.start <= endTime) {
      return true; // trivial case, when we're within the last __ years
    }

    // if the entry has a stop time, check if the effective date range overlapped
    // the last __ years
    if (e.stop != 0L && e.stop > cutoffDate) {

      if (e.stop > endTime) {
        // If any entries have an end date in the future but are within the cutoffDate,
        // remove the end date but keep the entry (since it's still active).
        e.stop = 0L;
      }

      return true;
    }

    return false;
  }

  /**
   * Get the fileName to used to export the patient record. See the configuration
   * setting "exporter.use_uuid_fileNames".
   *
   * @param person    The person being exported.
   * @param tag       A tag to add to the fileName before the extension.
   * @param extension The file extension to use.
   * @return The fileName only (not a path).
   */
  public static String filename(Person person, String tag, String extension) {
    if (Boolean.parseBoolean(Config.get("exporter.use_uuid_fileNames"))) {
      return person.attributes.get(Person.ID) + tag + "." + extension;
    } else {
      // ensure unique fileNames for now
      return person.attributes.get(Person.NAME).toString().replace(' ', '_') + "_" + person.attributes.get(Person.ID)
          + tag + "." + extension;
    }
  }
}
