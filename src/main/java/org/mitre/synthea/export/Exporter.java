package org.mitre.synthea.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;

import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.DeathModule;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.HealthRecord.Report;

public abstract class Exporter {
  
  /**
   * Supported FHIR versions.
   */
  public enum SupportedFhirVersion {
    DSTU2,
    STU3,
    R4
  }
   
  /**
   * Runtime configuration of the record exporter.
   */
  public static class ExporterRuntimeOptions {
    
    public int yearsOfHistory;
    private BlockingQueue<String> recordQueue;
    private SupportedFhirVersion fhirVersion;
    
    public ExporterRuntimeOptions() {
      yearsOfHistory = Integer.parseInt(Config.get("exporter.years_of_history"));
    }
    
    /**
     * Enables a blocking queue to which FHIR patient records will be written.
     * @param version specifies the version of FHIR that will be written to the queue.
     */
    public void enableQueue(SupportedFhirVersion version) {
      recordQueue = new LinkedBlockingQueue<>(1);
      fhirVersion = version;
    }
    
    public SupportedFhirVersion queuedFhirVersion() {
      return fhirVersion;
    }
    
    public boolean isQueueEnabled() {
      return recordQueue != null;
    }

    /**
     * Returns the newest generated patient record (in FHIR STU 3 JSON format) 
     * or blocks until next record becomes available.
     * Returns null if the generator does not have a record queue.
     */
    public String getNextRecord() throws InterruptedException {
      if (recordQueue == null) {
        return null;
      }
      return recordQueue.take();
    }

    /**
     * Returns true if record queue is empty or null. Otherwise returns false.
     */
    public boolean isRecordQueueEmpty() {
      return recordQueue == null || recordQueue.size() == 0;
    }
  }
  
  /**
   * Export a single patient, into all the formats supported. (Formats may be enabled or disabled by
   * configuration)
   *
   * @param person   Patient to export
   * @param stopTime Time at which the simulation stopped
   * @param options Runtime exporter options
   */
  public static void export(Person person, long stopTime, ExporterRuntimeOptions options) {
    int yearsOfHistory = Integer.parseInt(Config.get("exporter.years_of_history"));
    if (yearsOfHistory > 0) {
      person = filterForExport(person, yearsOfHistory, stopTime);
    }
    if (!person.alive(stopTime)) {
      filterAfterDeath(person);
    }
    if (person.hasMultipleRecords) {
      int i = 0;
      for (String key : person.records.keySet()) {
        person.record = person.records.get(key);
        exportRecord(person, Integer.toString(i), stopTime, options);
        i++;
      }
    } else {
      exportRecord(person, "", stopTime, options);
    }
  }
  
  /**
   * Export a single patient, into all the formats supported. (Formats may be enabled or disabled by
   * configuration)
   *
   * @param person   Patient to export
   * @param stopTime Time at which the simulation stopped
   */
  public static void export(Person person, long stopTime) {
    export(person, stopTime, new ExporterRuntimeOptions());
  }

  /**
   * Export a single patient record, into all the formats supported.
   * (Formats may be enabled or disabled by configuration)
   *
   * @param person   Patient to export, with Patient.record being set.
   * @param fileTag  An identifier to tag the file with.
   * @param stopTime Time at which the simulation stopped
   * @param options Generator's record queue (may be null)
   */
  private static void exportRecord(Person person, String fileTag, long stopTime,
          ExporterRuntimeOptions options) {

    if (Boolean.parseBoolean(Config.get("exporter.fhir_stu3.export"))) {
      File outDirectory = getOutputFolder("fhir_stu3", person);
      if (Boolean.parseBoolean(Config.get("exporter.fhir.bulk_data"))) {
        org.hl7.fhir.dstu3.model.Bundle bundle = FhirStu3.convertToFHIR(person, stopTime);
        IParser parser = FhirContext.forDstu3().newJsonParser().setPrettyPrint(false);
        for (org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent entry : bundle.getEntry()) {
          String filename = entry.getResource().getResourceType().toString() + ".ndjson";
          Path outFilePath = outDirectory.toPath().resolve(filename);
          String entryJson = parser.encodeResourceToString(entry.getResource());
          appendToFile(outFilePath, entryJson);
        }
      } else {
        String bundleJson = FhirStu3.convertToFHIRJson(person, stopTime);
        Path outFilePath = outDirectory.toPath().resolve(filename(person, fileTag, "json"));
        writeNewFile(outFilePath, bundleJson);
      }
    }
    if (Boolean.parseBoolean(Config.get("exporter.fhir_dstu2.export"))) {
      File outDirectory = getOutputFolder("fhir_dstu2", person);
      if (Boolean.parseBoolean(Config.get("exporter.fhir.bulk_data"))) {
        ca.uhn.fhir.model.dstu2.resource.Bundle bundle = FhirDstu2.convertToFHIR(person, stopTime);
        IParser parser = FhirContext.forDstu2().newJsonParser().setPrettyPrint(false);
        for (ca.uhn.fhir.model.dstu2.resource.Bundle.Entry entry : bundle.getEntry()) {
          String filename = entry.getResource().getResourceName() + ".ndjson";
          Path outFilePath = outDirectory.toPath().resolve(filename);
          String entryJson = parser.encodeResourceToString(entry.getResource());
          appendToFile(outFilePath, entryJson);
        }
      } else {
        String bundleJson = FhirDstu2.convertToFHIRJson(person, stopTime);
        Path outFilePath = outDirectory.toPath().resolve(filename(person, fileTag, "json"));
        writeNewFile(outFilePath, bundleJson);
      }
    }
    if (Boolean.parseBoolean(Config.get("exporter.fhir.export"))) {
      File outDirectory = getOutputFolder("fhir", person);
      if (Boolean.parseBoolean(Config.get("exporter.fhir.bulk_data"))) {
        org.hl7.fhir.r4.model.Bundle bundle = FhirR4.convertToFHIR(person, stopTime);
        IParser parser = FhirContext.forR4().newJsonParser().setPrettyPrint(false);
        for (org.hl7.fhir.r4.model.Bundle.BundleEntryComponent entry : bundle.getEntry()) {
          String filename = entry.getResource().getResourceType().toString() +"_"+ entry.hashCode() + ".ndjson";
          Path outFilePath = outDirectory.toPath().resolve(filename);
          String entryJson = parser.encodeResourceToString(entry.getResource());
          appendToFile(outFilePath, entryJson);
        }
      } else {
        String bundleJson = FhirR4.convertToFHIRJson(person, stopTime);
        Path outFilePath = outDirectory.toPath().resolve(filename(person, fileTag, "json"));
        writeNewFile(outFilePath, bundleJson);
      }
    }
    if (Boolean.parseBoolean(Config.get("exporter.ccda.export"))) {
      String ccdaXml = CCDAExporter.export(person, stopTime);
      File outDirectory = getOutputFolder("ccda", person);
      Path outFilePath = outDirectory.toPath().resolve(filename(person, fileTag, "xml"));
      writeNewFile(outFilePath, ccdaXml);
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
    if (Boolean.parseBoolean(Config.get("exporter.clinical_note.export"))) {
      File outDirectory = getOutputFolder("notes", person);
      Path outFilePath = outDirectory.toPath().resolve(filename(person, fileTag, "txt"));
      String consolidatedNotes = ClinicalNoteExporter.export(person);
      writeNewFile(outFilePath, consolidatedNotes);
    }
    if (options.isQueueEnabled()) {
      try {
        switch (options.queuedFhirVersion()) {
          case DSTU2:
            options.recordQueue.put(FhirDstu2.convertToFHIRJson(person, stopTime));
            break;
          case STU3:
            options.recordQueue.put(FhirStu3.convertToFHIRJson(person, stopTime));
            break;
          default:
            options.recordQueue.put(FhirR4.convertToFHIRJson(person, stopTime));
            break;
        }
      } catch (InterruptedException ie) {
        // ignore
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Write a new file with the given contents.
   * @param file Path to the new file.
   * @param contents The contents of the file.
   */
  private static void writeNewFile(Path file, String contents) {
    try {
      Files.write(file, Collections.singleton(contents), StandardOpenOption.CREATE_NEW);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Append contents to the end of a file.
   * @param file Path to the new file.
   * @param contents The contents of the file.
   */
  private static synchronized void appendToFile(Path file, String contents) {
    try {
      if (Files.notExists(file)) {
        Files.createFile(file);
      }
    } catch (Exception e) {
      // Ignore... multi-threaded race condition to create a file that didn't exist,
      // but does now because one of the other exporter threads beat us to it.
    }
    try {
      Files.write(file, Collections.singleton(contents), StandardOpenOption.APPEND);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Run any exporters that require the full dataset to be generated prior to exporting.
   * (E.g., an aggregate statistical exporter)
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
        CSVExporter.getInstance().exportPayers();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Filter the patient's history to only the last __ years
   * but also include relevant history from before that. Exclude
   * any history that occurs after the specified end_time -- typically
   * this is the current time/System.currentTimeMillis().
   *
   * @param original    The Person to filter.
   * @param yearsToKeep The last __ years to keep.
   * @param endTime     The time the history ends.
   * @return Modified Person with history expunged.
   */
  public static Person filterForExport(Person original, int yearsToKeep, long endTime) {
    // TODO: clone the patient so that we export only the last _ years
    // but the rest still exists, just in case
    Person filtered = original; //.clone();
    //filtered.record = original.record.clone();

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
   * Filter the health record to only the last __ years
   * but also include relevant history from before that. Exclude
   * any history that occurs after the specified end_time -- typically
   * this is the current time/System.currentTimeMillis().
   *
   * @param record    The record to filter.
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

      // some of the "future death" logic could potentially add a future-dated death certificate
      Predicate<Observation> isCauseOfDeath =
          o -> DeathModule.CAUSE_OF_DEATH_CODE.code.equals(o.type);
      // keep cause of death unless it's future dated
      Predicate<Observation> keepObservation = isCauseOfDeath.and(notFutureDated);
      filterEntries(encounter.observations, claimItems, cutoffDate, endTime, keepObservation);

      // keep all death certificates, unless they are future-dated
      Predicate<Report> isDeathCertificate = r -> DeathModule.DEATH_CERTIFICATE.code.equals(r.type);
      Predicate<Report> keepReport = isDeathCertificate.and(notFutureDated);
      filterEntries(encounter.reports, claimItems, cutoffDate, endTime, keepReport);

      filterEntries(encounter.procedures, claimItems, cutoffDate, endTime, null);

      // keep medications if still active, regardless of start date
      filterEntries(encounter.medications, claimItems, cutoffDate, endTime,
          med -> record.medicationActive(med.type));

      filterEntries(encounter.immunizations, claimItems, cutoffDate, endTime, null);

      // keep careplans if they are still active, regardless of start date
      filterEntries(encounter.careplans, claimItems, cutoffDate, endTime,
          cp -> record.careplanActive(cp.type));
    }

    // if ANY of these are not empty, the encounter is not empty
    Predicate<Encounter> encounterNotEmpty = e ->
        !e.conditions.isEmpty() || !e.allergies.isEmpty()
            || !e.observations.isEmpty() || !e.reports.isEmpty()
            || !e.procedures.isEmpty() || !e.medications.isEmpty()
            || !e.immunizations.isEmpty() || !e.careplans.isEmpty();

    Predicate<Encounter> isDeathCertification =
        e -> !e.codes.isEmpty() && DeathModule.DEATH_CERTIFICATION.equals(e.codes.get(0));
    Predicate<Encounter> keepEncounter =
        encounterNotEmpty.or(isDeathCertification.and(notFutureDated));

    // finally filter out any empty encounters
    filterEntries(record.encounters, Collections.emptyList(), cutoffDate, endTime, keepEncounter);

    return record;
  }

  /**
   * Helper function to filter entries from a list. Entries are kept if their date range falls
   * within the provided range or if `keepFunction` is provided, and returns `true` for the given
   * entry.
   *
   * @param entries      List of `Entry`s to filter
   * @param claimItems   List of ClaimItems, from which any removed items should also be removed.
   * @param cutoffDate   Minimum date, entries older than this may be discarded
   * @param endTime      Maximum date, entries newer than this may be discarded
   * @param keepFunction Keep function, if this function returns `true` for an entry then it will
   *                     be kept
   */
  private static <E extends HealthRecord.Entry> void filterEntries(List<E> entries,
      List<HealthRecord.Entry> claimItems, long cutoffDate,
      long endTime, Predicate<E> keepFunction) {

    Iterator<E> iterator = entries.iterator();
    // iterator allows us to use the remove() method
    while (iterator.hasNext()) {
      E entry = iterator.next();
      // if the entry is not within the keep time range,
      // and the special keep function (if provided) doesn't say keep it
      // remove it from the list
      if (!entryWithinTimeRange(entry, cutoffDate, endTime)
          && (keepFunction == null || !keepFunction.test(entry))) {
        iterator.remove();

        claimItems.removeIf(ci -> ci == entry);
        // compare with == because we only care if it's the actual same object
      }
    }
  }

  private static boolean entryWithinTimeRange(
      HealthRecord.Entry e, long cutoffDate, long endTime) {

    if (e.start > cutoffDate && e.start <= endTime) {
      return true; // trivial case, when we're within the last __ years
    }

    // if the entry has a stop time, check if the effective date range overlapped the last __ years
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
   * There is a tiny chance that in the last time step, one module ran to the very end of
   * the time step, and the next killed the person half-way through. In this case,
   * it is possible that an encounter (from the first module) occurred post death.
   * We must filter it out here.
   *
   * @param person The dead person.
   */
  private static void filterAfterDeath(Person person) {
    long deathTime = (long) person.attributes.get(Person.DEATHDATE);
    if (person.hasMultipleRecords) {
      for (HealthRecord record : person.records.values()) {
        Iterator<Encounter> iter = record.encounters.iterator();
        while (iter.hasNext()) {
          Encounter encounter = iter.next();
          if (encounter.start > deathTime) {
            iter.remove();
          }
        }
      }
    } else {
      Iterator<Encounter> iter = person.record.encounters.iterator();
      while (iter.hasNext()) {
        Encounter encounter = iter.next();
        if (encounter.start > deathTime) {
          iter.remove();
        }
      }
    }
  }

  /**
   * Get the folder where the patient record should be stored.
   * See the configuration settings "exporter.subfolders_by_id_substring" and
   * "exporter.baseDirectory".
   *
   * @param folderName The base folder to use.
   * @param person     The person being exported.
   * @return Either the base folder provided, or a subdirectory, depending on configuration
   *     settings.
   */
  public static File getOutputFolder(String folderName, Person person) {
    List<String> folders = new ArrayList<>();

    folders.add(folderName);

    if (person != null
        && Boolean.parseBoolean(Config.get("exporter.subfolders_by_id_substring"))) {
      String id = (String) person.attributes.get(Person.ID);

      folders.add(id.substring(0, 2));
      folders.add(id.substring(0, 3));
    }

    String baseDirectory = Config.get("exporter.baseDirectory");

    File f = Paths.get(baseDirectory, folders.toArray(new String[0])).toFile();
    f.mkdirs();

    return f;
  }

  /**
   * Get the filename to used to export the patient record.
   * See the configuration setting "exporter.use_uuid_filenames".
   *
   * @param person    The person being exported.
   * @param tag       A tag to add to the filename before the extension.
   * @param extension The file extension to use.
   * @return The filename only (not a path).
   */
  public static String filename(Person person, String tag, String extension) {
    if (Boolean.parseBoolean(Config.get("exporter.use_uuid_filenames"))) {
      return person.attributes.get(Person.ID) + tag + "." + extension;
    } else {
      // ensure unique filenames for now
      return person.attributes.get(Person.NAME).toString().replace(' ', '_') + "_"
          + person.attributes.get(Person.ID) + tag + "." + extension;
    }
  }
}
