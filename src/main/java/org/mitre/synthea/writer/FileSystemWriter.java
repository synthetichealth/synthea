package org.mitre.synthea.writer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;


import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;


public class FileSystemWriter {
  /**
   * Get the folder where the patient record should be stored. See the
   * configuration settings "exporter.subfolders_by_id_substring" and
   * "exporter.baseDirectory".
   *
   * @param folderName The base folder to use.
   * @param person     The person being exported.
   * @return Either the base folder provided, or a subdirectory, depending on
   *         configuration settings.
   */
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

  /**
   * Write a new file with the given contents.
   * 
   * @param outDirectory
   * @param fileName
   * @param contents
   */
  public static void writeNewFile(File outDirectory, String fileName, String contents) {
    try {
      Path outFilePath = outDirectory.toPath().resolve(fileName);
      Files.write(outFilePath, Collections.singleton(contents), StandardOpenOption.CREATE_NEW);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Append contents to the end of a file.
   * 
   * @param outDirectory
   * @param fileName
   * @param contents
   */
   public static synchronized void appendToFile(File outDirectory, String fileName, String contents) {
    Path outFilePath = outDirectory.toPath().resolve(fileName);
    try {
      if (Files.notExists(outFilePath)) {
        Files.createFile(outFilePath);
      }
    } catch (Exception e) {
      // Ignore... multi-threaded race condition to create a file that didn't exist,
      // but does now because one of the other exporter threads beat us to it.
    }
    try {
      Files.write(outFilePath, Collections.singleton(contents), StandardOpenOption.APPEND);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}