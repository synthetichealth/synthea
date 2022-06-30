import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

import ca.uhn.fhir.parser.IParser;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Resource;
import org.mitre.synthea.export.flexporter.Actions;
import org.mitre.synthea.export.flexporter.FhirPathUtils;
import org.mitre.synthea.export.flexporter.Mapping;


public class RunFlexporter {
  public static void main(String[] args) throws Exception {
    Queue<String> argsQ = new LinkedList<String>(Arrays.asList(args));

    File igDirectory = null;
    File sourceFile = null;
    File mappingFile = null;

    while (!argsQ.isEmpty()) {
      String currArg = argsQ.poll();

      if (currArg.equals("-ig")) {
        String value = argsQ.poll();

        if (value == null) {
          throw new FileNotFoundException("No implementation guide directory provided");
        }

        igDirectory = new File(value);

        if (!igDirectory.isDirectory()) {
          throw new FileNotFoundException(String.format(
              "Specified implementation guide directory (%s) does not exist or is not a directory",
              value));
        } else if (isDirEmpty(igDirectory.toPath())) {
          throw new FileNotFoundException(
              String.format("Specified implementation guide directory (%s) is empty", value));
        }

      } else if (currArg.equals("-m")) {
        String value = argsQ.poll();

        if (value == null) {
          throw new FileNotFoundException("No mapping file provided");
        }

        mappingFile = new File(value);

        if (!mappingFile.exists()) {
          throw new FileNotFoundException(
              String.format("Specified mapping file (%s) does not exist", value));
        }

      } else if (currArg.equals("-s")) {
        String value = argsQ.poll();
        sourceFile = new File(value);

        if (value == null) {
          throw new FileNotFoundException("No Synthea source FHIR provided");
        }

        if (!sourceFile.exists()) {
          throw new FileNotFoundException(
              String.format("Specified Synthea source FHIR (%s) does not exist", value));
        }
      }
    }

    if (mappingFile == null || sourceFile == null) {
      usage();
      System.exit(1);
    }

    convertFhir(mappingFile, igDirectory, sourceFile);
  }

  public static void convertFhir(File mappingFile, File igDirectory, File sourceFhir)
      throws IOException {

    Mapping mapping = Mapping.parseMapping(mappingFile);

    IParser parser = FhirPathUtils.FHIR_CTX.newJsonParser().setPrettyPrint(true);

    if (sourceFhir.isDirectory()) {

      // TODO

    } else {
      String fhirJson = new String(Files.readAllBytes(sourceFhir.toPath()));
      Bundle bundle = parser.parseResource(Bundle.class, fhirJson);

      for (BundleEntryComponent bec : bundle.getEntry()) {
        Resource r = bec.getResource();
        if (r.getId().startsWith("urn:uuid:")) {
          // HAPI does some weird stuff with IDs
          // by default in Synthea they are just plain UUIDs
          // and the entry.fullUrl is urn:uuid:(id)
          // but somehow when they get parsed back in, the id is urn:uuid:etc
          // which then doesn't get written back out at the end
          // so this removes the "urn:uuid:" bit if it got added
          r.setId(r.getId().substring(9));
        }
      }

      // bundle is modified in-place
      convertFhir(bundle, mapping);

      String bundleJson = parser.encodeResourceToString(bundle);

      File outFile =
          new File("./output/" + System.currentTimeMillis() + "_" + sourceFhir.getName());

      Files.write(outFile.toPath(), bundleJson.getBytes(), StandardOpenOption.CREATE_NEW);

      System.out.println("Wrote " + outFile);
    }
  }

  public static Bundle convertFhir(Bundle bundle, Mapping mapping) {
    Actions.applyMapping(bundle, mapping, null);

    return bundle;
  }

  private static boolean isDirEmpty(final Path directory) throws IOException {
    try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
      return !dirStream.iterator().hasNext();
    }
  }

  private static void usage() {
    System.out.println("Usage: run_flexporter -m MAPPING_FILE -s SOURCE_FHIR [-i IG_FOLDER]");
  }
}
