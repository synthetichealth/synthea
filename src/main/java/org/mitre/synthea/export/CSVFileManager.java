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
   * Writer for patients.csv.
   */
  private OutputStreamWriter patients;
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

  public CSVFileManager(Path outputDirectory, List<String> includedFiles,
                         List<String> excludedFiles, boolean append) {
    this.outputDirectory = outputDirectory;
    this.includedFiles = includedFiles;
    this.excludedFiles = excludedFiles;
    this.append = append;
  }

  /**
   * Helper method to get the writer for the given output file.
   * Returns a "no-op" writer for any excluded files.
   *
   * @param outputDirectory Parent directory for output csv files
   * @param filename Filename for the current file
   * @param append True = append to an existing file, False = overwrite any existing files
   * @param includedFiles List of filenames that should be included in output
   * @param excludedFiles List of filenames that should not be included in output
   *
   * @return OutputStreamWriter for the given output file.
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

  public OutputStreamWriter getWriter(String resourceKey) throws IOException {
    OutputStreamWriter writer = writerMap.get(resourceKey);
    if (writer == null) {
      writer = getResourceWriter(resourceKey);
      writerMap.put(resourceKey, writer);
      if (!append) {
        synchronized (writer) {
          writer.write(CSVConstants.HEADER_LINE_MAP.get(resourceKey));
        }
      }
    }
    return writer;
  }
}
