package org.mitre.synthea.export;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.output.NullOutputStream;
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
  public OutputStreamWriter getWriter(String filename) throws IOException {
    boolean excluded = (!includedFiles.isEmpty() && !includedFiles.contains(filename))
        || excludedFiles.contains(filename);
    if (excluded) {
      return NO_OP;
    }

    File file = outputDirectory.resolve(filename).toFile();
    // file writing may fail if we tell it to append to a file that doesn't already exist
    boolean appendToThisFile = append && file.exists();

    return new OutputStreamWriter(new FileOutputStream(file, appendToThisFile), charset);
  }

  public OutputStreamWriter patientWriter() throws IOException {
    if (patients == null) {
      patients = getWriter("patients.csv");
    }
    return patients;
  }
}
