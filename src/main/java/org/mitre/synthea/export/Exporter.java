package org.mitre.synthea.export;

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
import java.util.function.Predicate;

import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.DeathModule;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.ClaimItem;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.HealthRecord.Report;

public abstract class Exporter {
  /**
   * Export a single patient, into all the formats supported. (Formats may be enabled or disabled by
   * configuration)
   * 
   * @param person Patient to export
   * @param stopTime Time at which the simulation stopped
   */
  public static void export(Person person, long stopTime) {
    int yearsOfHistory = Integer.parseInt(Config.get("exporter.years_of_history"));
    if (yearsOfHistory > 0) {
      person = filterForExport(person, yearsOfHistory, stopTime);
    }
    if (Boolean.parseBoolean(Config.get("exporter.fhir.export"))) {
      String bundleJson = FhirStu3.convertToFHIR(person, stopTime);
      File outDirectory = getOutputFolder("fhir", person);
      Path outFilePath = outDirectory.toPath().resolve(filename(person, "json"));

      try {
        Files.write(outFilePath, Collections.singleton(bundleJson), StandardOpenOption.CREATE_NEW);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    if (Boolean.parseBoolean(Config.get("exporter.fhir_dstu2.export"))) {
      String bundleJson = FhirDstu2.convertToFHIR(person, stopTime);
      File outDirectory = getOutputFolder("fhir_dstu2", person);
      Path outFilePath = outDirectory.toPath().resolve(filename(person, "json"));

      try {
        Files.write(outFilePath, Collections.singleton(bundleJson), StandardOpenOption.CREATE_NEW);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    if (Boolean.parseBoolean(Config.get("exporter.ccda.export"))) {
      String ccdaXml = CCDAExporter.export(person, stopTime);

      File outDirectory = getOutputFolder("ccda", person);
      Path outFilePath = outDirectory.toPath().resolve(filename(person, "xml"));

      try {
        Files.write(outFilePath, Collections.singleton(ccdaXml), StandardOpenOption.CREATE_NEW);
      } catch (IOException e) {
        e.printStackTrace();
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
        TextExporter.export(person, stopTime);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Run any exporters that require the full dataset to be generated prior to exporting. (Ex, an
   * aggregate statistical exporter)
   * 
   * @param generator
   *          Generator that generated the patients
   */
  public static void runPostCompletionExports(Generator generator) {
    try {
      HospitalExporter.export(generator.stop);
    } catch (Exception e) {
      e.printStackTrace();
    }
    
    try {
      HospitalDSTU2Exporter.export(generator.stop);
    } catch (Exception e) {
      e.printStackTrace();
    }

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
  }
  
  public static Person filterForExport(Person original, int yearsToKeep, long endTime) {
    // filter the patient's history to only the last __ years
    // but also include relevant history from before that. Exclude
    // any history that occurs after the specified end_time -- typically
    // this is the current time/System.currentTimeMillis().

    long cutoffDate = endTime - Utilities.convertTime("years", yearsToKeep);

    Predicate<HealthRecord.Entry> notFutureDated = e -> e.start <= endTime;
    
    // TODO: clone the patient so that we export only the last _ years 
    // but the rest still exists, just in case
    Person filtered = original; //.clone();
    //filtered.record = original.record.clone();

    final HealthRecord record = filtered.record;
    
    for (Encounter encounter : record.encounters) { 
      List<ClaimItem> claimItems = encounter.claim.items;
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

    return filtered;
  }

  /**
   * Helper function to filter entries from a list. Entries are kept if their date range falls
   * within the provided range or if `keepFunction` is provided, and returns `true` for the given
   * entry.
   * 
   * @param entries
   *          List of `Entry`s to filter
   * @param claimItems
   *          List of ClaimItems, from which any removed items should also be removed.
   * @param cutoffDate
   *          Minimum date, entries older than this may be discarded
   * @param endTime
   *          Maximum date, entries newer than this may be discarded
   * @param keepFunction
   *          Keep function, if this function returns `true` for an entry then it will be kept
   */
  private static <E extends HealthRecord.Entry> void filterEntries(List<E> entries,
      List<ClaimItem> claimItems, long cutoffDate, long endTime, Predicate<E> keepFunction) {
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
        
        claimItems.removeIf(ci -> ci.entry == entry); 
        // compare with == because we only care if it's the actual same object
      }
    }
  }
  
  private static boolean entryWithinTimeRange(HealthRecord.Entry e, long cutoffDate, long endTime) {
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

  public static File getOutputFolder(String folderName, Person person) {
    List<String> folders = new ArrayList<>();

    folders.add(folderName);

    if (person != null && Boolean.parseBoolean(Config.get("exporter.subfolders_by_id_substring"))) {
      String id = (String) person.attributes.get(Person.ID);

      folders.add(id.substring(0, 2));
      folders.add(id.substring(0, 3));
    }

    String baseDirectory = Config.get("exporter.baseDirectory");

    File f = Paths.get(baseDirectory, folders.toArray(new String[0])).toFile();
    f.mkdirs();

    return f;
  }

  public static String filename(Person person, String extension) {
    if (Boolean.parseBoolean(Config.get("exporter.use_uuid_filenames"))) {
      return person.attributes.get(Person.ID) + "." + extension;
    } else {
      // ensure unique filenames for now
      return person.attributes.get(Person.NAME) + "_" + person.attributes.get(Person.ID) + "."
          + extension;
    }
  }
}
