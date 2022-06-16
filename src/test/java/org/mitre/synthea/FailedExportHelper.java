package org.mitre.synthea;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.mitre.synthea.export.JSONExporter;
import org.mitre.synthea.world.agents.Person;

public class FailedExportHelper {
  public static String directory = "failed_exports";

  /**
   * Check to see if the failed export directory is there. If not, create it.
   */
  public static void checkExportDirectory() {
    File exportDir = new File(directory);
    if (! exportDir.exists()) {
      exportDir.mkdir();
    }
  }

  /**
   * Dump as much info as possible on the failed export case. This will try to write out the actual
   * failing export, the validation errors and a JSON file containing the Person.
   * @param exporterName "CDA", "FHIRR4", etc.
   * @param attemptedExport The actual exported document, if it exists
   * @param validatonErrors Any validation errors
   * @param person the person
   * @throws IOException when bad things happen
   */
  public static void dumpInfo(String exporterName, String attemptedExport,
                              List<String> validatonErrors,
                              Person person) throws IOException {
    checkExportDirectory();
    String id = (String) person.attributes.get(Person.ID);
    if (attemptedExport != null && !attemptedExport.isEmpty()) {
      Path attemptedExportPath = Paths.get(directory, String.format("%s.%s", id, exporterName));
      Files.write(attemptedExportPath, attemptedExport.getBytes());
    }
    if (validatonErrors != null && !validatonErrors.isEmpty()) {
      Path errorDoc = Paths.get(directory, String.format("%s.%s.txt", id, exporterName));
      String allErrors = validatonErrors.stream().collect(Collectors.joining("\n\n"));
      Files.write(errorDoc, allErrors.getBytes());
    }
    Path jsonFile = Paths.get(directory, String.format("%s.%s.json", id, exporterName));
    String json = JSONExporter.export(person);
    Files.write(jsonFile, json.getBytes());
  }

  /**
   * Read failed export files.
   * @param exporterName "CCDA", "FHIRR4", etc.
   * @return List of Files, each containing the contents of a failed export file.
   * @throws IOException when bad things happen
   */
  public static List<File> loadFailures(String exporterName) throws IOException {
    checkExportDirectory();
    List<File> failures = new ArrayList<File>();
    Path exportPath = Paths.get(directory);
    for (File file : exportPath.toFile().listFiles()) {
      if (file.isFile() && file.getName().endsWith(exporterName)) {
        failures.add(file);
      }
    }
    return failures;
  }
}