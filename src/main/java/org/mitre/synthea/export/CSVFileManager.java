package org.mitre.synthea.export;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // TODO: alter filenames if configured to use multiple files
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
   * @param includedFiles List of filenames that should be included in output
   * @param excludedFiles List of filenames that should not be included in output
   */
  public CSVFileManager(List<String> includedFiles,
                        List<String> excludedFiles) {
    append = Config.getAsBoolean("exporter.csv.append_mode");

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

    this.includedFiles = includedFiles;
    this.excludedFiles = excludedFiles;
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
  public synchronized OutputStreamWriter getWriter(String resourceKey) throws IOException {
    synchronized (writerMap) {
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
  }

  /**
   * Flush all CSV export files.
   */
  public void flushWriters() throws IOException {
    synchronized (writerMap) {
      for (var entry : writerMap.entrySet()) {
        OutputStreamWriter writer = entry.getValue();
        if (entry != null) {
          writer.flush();
        }
      }
    }
  }
}
