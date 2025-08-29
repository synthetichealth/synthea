package org.mitre.synthea.export;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.output.NullOutputStream;
import org.mitre.synthea.export.CSVConstants;
import org.mitre.synthea.helpers.Config;

public class CSVFileManager {
  /**
   * Charset for specifying the character set of the output files.
   */
  private Charset charset = Charset.forName(Config.get("exporter.encoding", "UTF-8"));
  private boolean append;
  private Path outputDirectory;
  private List<String> includedFiles;
  private List<String> excludedFiles;
  private Map<String, String> filenameMap = initializeFilenameMap();
  private Map<String, OutputStreamWriter> writerMap = new HashMap<>();

  private Map<String, String> initializeFilenameMap() {
    HashMap<String, String> map = new HashMap<>();

    map.putAll(CSVConstants.BASE_FILENAME_MAP);

    return map;
  }

  /**
   * "No-op" writer to use to prevent writing to excluded files.
   * Note that this uses an Apache "NullOutputStream", but JDK11 provides its own.
   */
  private static final OutputStreamWriter NO_OP =
      new OutputStreamWriter(NullOutputStream.NULL_OUTPUT_STREAM);

  /**
   * Constructor for CSVFileManager, which manages the creation of files for the
   * CSV export.
   */
  public CSVFileManager() {
    initializeAppend();
    initializeOutputDirectory();
    initializeIncludedAndExcludedFiles();
  }

  private void initializeAppend() {
    append = Config.getAsBoolean("exporter.csv.append_mode");
  }

  private void initializeOutputDirectory() {
    File output = Exporter.getOutputFolder("csv", null);
    output.mkdirs();
    outputDirectory = output.toPath();

    if (Config.getAsBoolean("exporter.csv.folder_per_run")) {
      // we want a folder per run, so name it based on the timestamp
      String timestamp = ExportHelper.iso8601Timestamp(System.currentTimeMillis());
      String subfolderName = timestamp.replaceAll("\\W+", "_"); // make sure it's filename-safe
      outputDirectory = outputDirectory.resolve(subfolderName);
      outputDirectory.toFile().mkdirs();
    }
  }

  private void initializeIncludedAndExcludedFiles() {
    String includedFilesStr = Config.get("exporter.csv.included_files", "").trim();
    String excludedFilesStr = Config.get("exporter.csv.excluded_files", "").trim();

    includedFiles = Collections.emptyList();
    excludedFiles = Collections.emptyList();

    if (!includedFilesStr.isEmpty() && !excludedFilesStr.isEmpty()) {
      includedFiles = propStringToList(includedFilesStr);
      excludedFiles = propStringToList(excludedFilesStr);

      // Check if there is any overlap
      for (String includedFile : includedFiles) {
        if (excludedFiles.contains(includedFile)) {
          System.err.println("ERROR! CSV exporter is set to include and exclude the same file: "
                  + includedFile);
          throw new IllegalArgumentException(
                  "CSV exporter cannot include and exclude the same file: " + includedFile);
        }
      }
    } else {
      if (!includedFilesStr.isEmpty()) {
        includedFiles = propStringToList(includedFilesStr);

        if (!includedFiles.contains("patients.csv")) {
          System.err.println("WARNING! CSV exporter is set to not include patients.csv!");
          System.err.println("This is probably not what you want!");
        }

      } else {
        excludedFiles = propStringToList(excludedFilesStr);
      }
    }
  }

  /**
   * Helper function to convert a list of files directly from synthea.properties to filenames.
   * @param fileListString String directly from Config, ex "patients.csv,conditions , procedures"
   * @return normalized list of filenames as strings
   */
  private static List<String> propStringToList(String fileListString) {
    List<String> files = Arrays.asList(fileListString.split(","));
    // normalize filenames -- trim, lowercase, add .csv if not included
    files = files.stream().map(f -> {
      f = f.trim().toLowerCase();
      if (!f.endsWith(".csv")) {
        f = f + ".csv";
      }
      return f;
    }).collect(Collectors.toList());

    return files;
  }

  /**
   * Helper method to instantiate, if necessary, and return the writer for the
   * resource type's CSV file. Returns a "no-op" writer for any excluded files.
   *
   * @param resourceKey Key from CSVConstants for the resource type being written
   *
   * @return OutputStreamWriter for the given resource type's CSV file
   */
  private OutputStreamWriter getResourceWriter(String resourceKey) throws IOException {
    String baseFilename = CSVConstants.BASE_FILENAME_MAP.get(resourceKey);
    boolean excluded = (!includedFiles.isEmpty() && !includedFiles.contains(baseFilename))
        || excludedFiles.contains(baseFilename);
    if (excluded) {
      return NO_OP;
    }

    String filename = filenameMap.get(resourceKey);
    File file = outputDirectory.resolve(filename).toFile();
    // file writing may fail if we tell it to append to a file that doesn't already exist
    boolean appendToThisFile = append && file.exists();

    return new OutputStreamWriter(new FileOutputStream(file, appendToThisFile), charset);
  }

  /**
   * Method to get the writer for the file for a particular resource type.
   *
   * @param resourceKey Key from CSVConstants for the resource type being written
   *
   * @return OutputStreamWriter for the given resource type's CSV file
   */
  public OutputStreamWriter getWriter(String resourceKey) throws IOException {
    OutputStreamWriter writer = writerMap.get(resourceKey);
    if (writer == null) {
      writer = getResourceWriter(resourceKey);
      writerMap.put(resourceKey, writer);
      if (!append) {
        writer.write(CSVConstants.HEADER_LINE_MAP.get(resourceKey));
      }
    }

    return writer;
  }

  /**
   * Flush the writer for the given resource.
   *
   * @param resourceKey Key from CSVConstants for the resource type being flushed
   */
  public void flushWriter(String resourceKey) throws IOException {
    synchronized (resourceKey) {
      OutputStreamWriter writer = getWriter(resourceKey);
      if (writer != null) {
        writer.flush();
      }
    }
  }

  /**
   * Write a line of CSV representing a resource to the appropriate CSV file.
   *
   * @param resourceLine Line of CSV representing a resource
   * @param resourceKey Key from CSVConstants for the resource type being written
   */
  public void writeResourceLine(String resourceLine, String resourceKey) throws IOException {
    synchronized (resourceKey) {
      getWriter(resourceKey).write(resourceLine);
    }
  }
}
