package org.mitre.synthea.helpers;

import org.mitre.synthea.world.agents.Person;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DICOMFileSelector {
  private static List<String> dicomFileNames;

  public static String selectRandomDICOMFile(Person person) throws IOException {
    initialize();
    int index = person.randInt(dicomFileNames.size());
    return dicomFileNames.remove(index);
  }

  private static void initialize() throws IOException {
    if(dicomFileNames == null) {
      dicomFileNames = new ArrayList();
      String dicomDirectory = Config.get("dicom.directory");
      Path path = Path.of(dicomDirectory);
      Files.newDirectoryStream(path, "*.dcm").forEach(p -> {
        dicomFileNames.add(p.toString());
      });
    }
  }

  public static boolean filesRemain() throws IOException {
    initialize();
    return dicomFileNames.size() > 0;
  }
}
