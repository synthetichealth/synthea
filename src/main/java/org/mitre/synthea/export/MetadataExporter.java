package org.mitre.synthea.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Provider;

public class MetadataExporter {

  /**
   * Export metadata about the current run of Synthea.
   * Metadata includes various pieces of information about how the population was generated,
   * such as version, config settings, and runtime args.
   * @param generator Generator that was used to generate the population
   * @throws IOException if an error occurs writing to the output directory
   */
  public static void exportMetadata(Generator generator) throws IOException {
    // use a linked hashmap in an attempt to preserve insertion order.
    // ie, most important things at the top/start
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("runID", generator.id);

    Generator.GeneratorOptions opts = generator.options;

    // - population seed
    long seed = opts.seed;
    metadata.put("seed", seed);

    // clinician seed
    long clinicianSeed = opts.clinicianSeed;
    metadata.put("clinicianSeed", clinicianSeed);

    // reference time is expected to be entered on the command line as YYYYMMDD
    // note that Y = "week year" and y = "year" per the formatting guidelines
    // and D = "day in year" and d = "day in month", so what we actually want is yyyyMMdd
    // see: https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html
    SimpleDateFormat yyyymmdd = new SimpleDateFormat("yyyyMMdd");
    String referenceTime = yyyymmdd.format(new Date(generator.referenceTime));
    metadata.put("referenceTime", referenceTime);

    // - git commit hash of the current running version
    String version = Utilities.SYNTHEA_VERSION;
    metadata.put("version", version);

    // - number of patients, providers, payers generated
    // (in case "csv append mode" is on, and someone wants to link patients <--> run.
    // I think just these 3 should be enough to identify which run a line from any csv belongs to)
    int patientCount = generator.totalGeneratedPopulation.get();
    metadata.put("patientCount", patientCount);

    int providerCount = Provider.getProviderList().size();
    metadata.put("providerCount", providerCount);

    int payerCount = Payer.getAllPayers().size();
    metadata.put("payerCount", payerCount);

    // Java version,
    String javaVersion = System.getProperty("java.version"); // something like "12" or "1.8.0_201"
    metadata.put("javaVersion", javaVersion);

    // Actual Date/Time of execution.
    String runStartTime = ExportHelper.iso8601Timestamp(opts.runStartTime);
    metadata.put("runStartTime", runStartTime);

    // Run time
    long runTimeInSeconds = (System.currentTimeMillis() - opts.runStartTime) / 1000;
    metadata.put("runTimeInSeconds", runTimeInSeconds);

    // selected run settings
    String[] configSettings = { "exporter.years_of_history", };

    for (String configSetting : configSettings) {
      metadata.put(configSetting, Config.get(configSetting));
    }

    // note that nulls don't get exported, so if gender, age, city, etc, aren't specified
    // then they won't even be in the output file
    String gender = opts.gender;
    metadata.put("gender", gender);

    boolean ageSpecified = opts.ageSpecified;
    int minAge = opts.minAge;
    int maxAge = opts.maxAge;
    String age = ageSpecified ? minAge + "-" + maxAge : null;
    metadata.put("age", age);

    String city = opts.city;
    metadata.put("city", city);

    String state = opts.state;
    metadata.put("state", state);

    String modules = opts.enabledModules == null ? "*" : String.join(";", opts.enabledModules);
    metadata.put("modules", modules);

    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    String json = gson.toJson(metadata);

    StringBuilder filenameBuilder = new StringBuilder();
    filenameBuilder.append(runStartTime);
    filenameBuilder.append('_');

    // patient count above is the # actually generated.
    // put in the filename the number they requested
    int patientRequestCount = opts.population;
    filenameBuilder.append(patientRequestCount);
    filenameBuilder.append('_');
    filenameBuilder.append(state);
    if (city != null) {
      filenameBuilder.append('_');
      filenameBuilder.append(city);
    }
    filenameBuilder.append('_');
    filenameBuilder.append(generator.id);

    // make sure everything is filename-safe, replace "non-word characters" with _
    String filename = filenameBuilder.toString().replaceAll("\\W+", "_");
    File outputDirectory = Exporter.getOutputFolder("metadata", null);
    outputDirectory.mkdirs();
    Path outputFile = outputDirectory.toPath().resolve(filename + ".json");

    Files.write(outputFile, Collections.singleton(json), StandardOpenOption.CREATE_NEW);
  }
}
