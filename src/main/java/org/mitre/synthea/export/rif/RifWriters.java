package org.mitre.synthea.export.rif;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Utility class to manage a set of SynchronizedBBLineWriter instances. Will create one per output
 * file type per year specified. Per year files are currently only used for RIF beneficiary files.
 */
class RifWriters {

  private final Map<Integer, Map<Class, SynchronizedBBLineWriter>> allWriters;
  private final Path outputDir;

  public RifWriters(Path outputDir) {
    this.outputDir = outputDir;
    allWriters = Collections.synchronizedMap(new TreeMap<>());
  }

  public Set<Integer> getYears() {
    return allWriters.keySet();
  }

  private synchronized Map<Class, SynchronizedBBLineWriter> getWriters(int year) {
    Map<Class, SynchronizedBBLineWriter> writers = allWriters.get(year);
    if (writers == null) {
      writers = Collections.synchronizedMap(new HashMap<>());
      allWriters.put(year, writers);
    }
    return writers;
  }

  public <E extends Enum<E>> SynchronizedBBLineWriter<E> getWriter(Class<E> rifEnum, int year) {
    return getWriters(year).get(rifEnum);
  }

  private <E extends Enum<E>> Path getFilePath(Class<E> enumClass, int year) {
    return getFilePath(enumClass, year, "csv");
  }

  private <E extends Enum<E>> Path getFilePath(Class<E> enumClass, int year, String ext) {
    String prefix = enumClass.getSimpleName().toLowerCase();
    String suffix = year == -1 ? "" : "_" + year;
    String fileName = String.format("%s%s.%s", prefix, suffix, ext);
    return outputDir.resolve(fileName);
  }

  public synchronized <E extends Enum<E>> SynchronizedBBLineWriter<E> getOrCreateWriter(
          Class<E> enumClass) {
    return getOrCreateWriter(enumClass, -1);
  }

  public synchronized <E extends Enum<E>> SynchronizedBBLineWriter<E> getOrCreateWriter(
          Class<E> enumClass, int year) {
    return getOrCreateWriter(enumClass, year, "csv", "|");
  }

  public synchronized <E extends Enum<E>> SynchronizedBBLineWriter<E> getOrCreateWriter(
          Class<E> enumClass, int year, String ext, String separator) {
    SynchronizedBBLineWriter<E> writer = getWriter(enumClass, year);
    if (writer == null) {
      Path filePath = getFilePath(enumClass, year, ext);
      writer = new SynchronizedBBLineWriter<>(enumClass, filePath, separator);
      getWriters(year).put(enumClass, writer);
    }
    return writer;
  }

  public <E extends Enum<E>> void writeValues(Class<E> enumClass, Map<E, String> fieldValues)
          throws IOException {
    writeValues(enumClass, fieldValues, -1);
  }

  public <E extends Enum<E>> void writeValues(Class<E> enumClass, Map<E, String> fieldValues,
          int year) throws IOException {
    getOrCreateWriter(enumClass, year).writeValues(fieldValues);
  }

}
