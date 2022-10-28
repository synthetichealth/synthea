package org.mitre.synthea.export.rif;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.mitre.synthea.export.Exporter;

/**
 * Utility class for writing to BB2 writers.
 */
class SynchronizedBBLineWriter<E extends Enum<E>> {

  private String bbFieldSeparator = "|";
  private final Path path;
  private final Class<E> clazz;

  /**
   * Construct a new instance. Fields will be separated using the default '|' character.
   * @param path the file path to write to
   * @throws IOException if something goes wrong
   */
  public SynchronizedBBLineWriter(Class<E> clazz, Path path) {
    this.path = path;
    this.clazz = clazz;
    writeHeaderIfNeeded();
  }

  /**
   * Construct a new instance.
   * @param path the file path to write to
   * @param separator overrides the default '|' field separator
   * @throws IOException if something goes wrong
   */
  public SynchronizedBBLineWriter(Class<E> clazz, Path path, String separator) {
    this.path = path;
    this.clazz = clazz;
    this.bbFieldSeparator = separator;
    writeHeaderIfNeeded();
  }

  /**
   * Write a BB2 header if the file is not present or is empty.
   * @throws IOException if something goes wrong
   */
  private void writeHeaderIfNeeded() {
    if (getFile().length() == 0) {
      String[] fields = Arrays.stream(clazz.getEnumConstants())
              .map(Enum::name)
              .toArray(String[]::new);
      writeLine(fields);
    }
  }

  /**
   * Write a line of output consisting of one or more fields separated by '|' and terminated with
   * a system new line.
   * @param fields the fields that will be concatenated into the line
   * @throws IOException if something goes wrong
   */
  private void writeLine(String... fields) {
    String line = String.join(bbFieldSeparator, fields);
    Exporter.appendToFile(path, line);
  }

  /**
   * Write a BB2 writer line.
   * @param fieldValues a sparse map of column names to values, missing values will result in
   *     empty values in the corresponding column
   * @throws IOException if something goes wrong
   */
  public void writeValues(Map<E, String> fieldValues) throws IOException {
    String[] fields = Arrays.stream(clazz.getEnumConstants())
            .map(e -> fieldValues.getOrDefault(e, ""))
            .toArray(String[]::new);
    writeLine(fields);
  }

  /**
   * Get the file that this writer writes to.
   * @return the file
   */
  public File getFile() {
    return path.toFile();
  }

}
