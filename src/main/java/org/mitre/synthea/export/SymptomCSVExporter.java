package org.mitre.synthea.export;

import static org.mitre.synthea.export.ExportHelper.dateFromTimestamp;
import static org.mitre.synthea.export.ExportHelper.iso8601Timestamp;

import com.google.common.collect.Table;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.QualityOfLifeModule;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.ImagingStudy;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;

/**
 * Researchers have requested a simple table-based format that could easily be
 * imported into any database for analysis. Unlike other formats which export a
 * single record per patient, this format generates 1 total file, and adds
 * lines to each based on the clinical events for each patient. The generated
 * file contains the list of symptoms associated to a pathology a person 
 * suffered from.
 */
public class SymptomCSVExporter {
  /**
   * Writer for symptoms.csv.
   */
  private OutputStreamWriter symptoms; 
  
  /**
   * Charset for specifying the character set of the output files.
   */
  private Charset charset = Charset.forName(Config.get("exporter.encoding"));

  /**
   * System-dependent string for a line break. (\n on Mac, *nix, \r\n on Windows)
   */
  private static final String NEWLINE = System.lineSeparator();

  /**
   * Constructor for the CSVExporter - initialize the 9 specified files and store
   * the writers in fields.
   */
  private SymptomCSVExporter() {
    try {
      File output = Exporter.getOutputFolder("symptoms/csv", null);
      output.mkdirs();
      Path outputDirectory = output.toPath();

      if (Boolean.parseBoolean(Config.get("exporter.symptoms.csv.folder_per_run"))) {
        // we want a folder per run, so name it based on the timestamp
        // TODO: do we want to consider names based on the current generation options?
        String timestamp = ExportHelper.iso8601Timestamp(System.currentTimeMillis());
        String subfolderName = timestamp.replaceAll("\\W+", "_"); // make sure it's filename-safe
        outputDirectory = outputDirectory.resolve(subfolderName);
        outputDirectory.toFile().mkdirs();
      }

      File symptomsFile = outputDirectory.resolve("symptoms.csv").toFile();
      boolean append = Boolean.parseBoolean(Config.get("exporter.symptoms.csv.append_mode"));
      append = append && symptomsFile.exists();

      symptoms = new OutputStreamWriter(new FileOutputStream(symptomsFile, append), charset);

      if (!append) {
        writeCSVHeaders();
      }
    } catch (IOException e) {
      // wrap the exception in a runtime exception.
      // the singleton pattern below doesn't work if the constructor can throw
      // and if these do throw ioexceptions there's nothing we can do anyway
      throw new RuntimeException(e);
    }
  }

  /**
   * Write the headers to each of the CSV files.
   * @throws IOException if any IO error occurs
   */
  private void writeCSVHeaders() throws IOException {
    symptoms.write("PATIENT,GENDER,RACE,ETHNICITY,AGE,PATHOLOGY,NUM_SYMPTOMS,SYMPTOMS");
    symptoms.write(NEWLINE);
  }

  /**
   * Thread safe singleton pattern adopted from
   * https://stackoverflow.com/questions/7048198/thread-safe-singletons-in-java
   */
  private static class SingletonHolder {
    /**
     * Singleton instance of the CSVExporter.
     */
    private static final SymptomCSVExporter instance = new SymptomCSVExporter();
  }

  /**
   * Get the current instance of the SymptomCSVExporter.
   * 
   * @return the current instance of the SymptomCSVExporter.
   */
  public static SymptomCSVExporter getInstance() {
    return SingletonHolder.instance;
  }

  

  /**
   * Add a single Person's condition symptoms info to the CSV records.
   * 
   * @param person Person to write record data for
   * @param time   Time the simulation ended
   * @throws IOException if any IO error occurs
   */
  public void export(Person person, long time) throws IOException {
    recordSymptom(person, time);
    symptoms.flush();
  }

  /**
   * Write a single Patient line, to symptoms.csv.
   *
   * @param person Person to write data for
   * @param endTime   Time the simulation ended
   * @return the patient's ID, to be referenced as a "foreign key" if necessary
   * @throws IOException if any IO error occurs
   */
  private String recordSymptom(Person person, long endTime) throws IOException {
    // PATIENT,GENDER,RACE,ETHNICITY,AGE,PATHOLOGY,NUM_SYMPTOMS,SYMPTOMS,
    String personID = (String) person.attributes.get(Person.ID);

    // check if we've already exported this patient demographic data yet,
    // otherwise the "split record" feature could add a duplicate entry.
    if (person.attributes.containsKey("exported_symptoms_to_csv")) {
      return personID;
    } else {
      person.attributes.put("exported_symptoms_to_csv", personID);
    }
    
    String gender = clean((String) person.attributes.getOrDefault(Person.GENDER, ""));
    String race = clean((String) person.attributes.getOrDefault(Person.RACE, ""));
    String ethnic = clean((String) person.attributes.getOrDefault(Person.ETHNICITY, ""));
    StringBuilder demoData = new StringBuilder();
    demoData.append(personID).append(',');
    demoData.append(gender).append(',');
    demoData.append(race).append(',');
    demoData.append(ethnic);
    
    Map<Long, Map<String, Map<String, Integer>>> infos = person.getConditionSymptoms();
    List<Long> list = new LinkedList<Long>(infos.keySet());
    Collections.sort(list);
    
    int yearsOfHistory = Integer.parseInt(Config.get("exporter.years_of_history"));
    
    for (Long time: list) {
      int symptomExporterMode = Integer.parseInt(Config.get("exporter.symptoms.mode"));
      boolean toBeExported = true;
      if (symptomExporterMode == 0) {        
        long cutoffDate = endTime - Utilities.convertTime("years", yearsOfHistory);
        toBeExported = time >= cutoffDate;
      }     
      if (!toBeExported) {
        continue;
      }
      Integer ageYear = person.ageInYears(time); 
      for (String condition: infos.get(time).keySet()) {
        StringBuilder s = new StringBuilder();
        s.append(demoData.toString()).append(',');
        s.append(ageYear.toString()).append(',');
        s.append(clean(condition)).append(',');
        s.append(clean(String.valueOf(infos.get(time).get(condition).size())));
        
        StringBuilder symptomStr = new StringBuilder();
        for (String symptom: infos.get(time).get(condition).keySet()) {
          String value = String.valueOf(infos.get(time).get(condition).get(symptom));
          symptomStr.append(';').append(clean(symptom)).append(':').append(value);
        }
        String symptomData = symptomStr.toString();
        if (symptomData.length() > 0) {
          symptomData = symptomData.substring(1);
        }
        s.append(',').append(symptomData).append(NEWLINE);
        write(s.toString(), symptoms);
      }
    }  

    return personID;
  }

  

  /**
   * Replaces commas and line breaks in the source string with a single space.
   * Null is replaced with the empty string.
   */
  private static String clean(String src) {
    if (src == null) {
      return "";
    } else {
      return src.replaceAll("\\r\\n|\\r|\\n|,", " ").trim();
    }
  }

  /**
   * Helper method to write a line to a File. Extracted to a separate method here
   * to make it a little easier to replace implementations.
   *
   * @param line   The line to write
   * @param writer The place to write it
   * @throws IOException if an I/O error occurs
   */
  private static void write(String line, OutputStreamWriter writer) throws IOException {
    synchronized (writer) {
      writer.write(line);
    }
  }
}