import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.apache.commons.io.FilenameUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ValueSet;
import org.mitre.synthea.export.flexporter.Actions;
import org.mitre.synthea.export.flexporter.FhirPathUtils;
import org.mitre.synthea.export.flexporter.Mapping;
import org.mitre.synthea.helpers.RandomCodeGenerator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;


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
    
    if (igDirectory != null) {
      loadIG(igDirectory);
    }

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

  private static void loadIG(File igDirectory) throws IOException {
      File[] artifacts = igDirectory.listFiles();

      for (File artifact : artifacts) {
          if (artifact.isFile() && FilenameUtils.getExtension(artifact.toString()).equals("json")) {
            
            IParser parser = FhirPathUtils.FHIR_CTX.newJsonParser();

            String fhirJson = new String(Files.readAllBytes(artifact.toPath()));
            IBaseResource resource = null;
            
            try {
              resource = parser.parseResource(fhirJson);
            } catch (DataFormatException dfe) {
              // why does an IG contain bad data?
              System.err.println("Warning: Unable to parse IG artifact " + artifact.getAbsolutePath());
              dfe.printStackTrace();
            }
            
//            System.out.println(resource);
            
            if (resource instanceof ValueSet) {
              // TODO: fix RandomCodeGenerator to work with HAPI objects
              // because this is silly
              
              ObjectMapper objectMapper = new ObjectMapper();

              Map<String, Object> valueSet = objectMapper.readValue(fhirJson,
                  new TypeReference<Map<String, Object>>() {
                  });
              try {
                RandomCodeGenerator.loadValueSet(null, valueSet);
              } catch (Exception e) {
                System.err.println("WARNING: Unable to load ValueSet " + artifact.getAbsolutePath());
                e.printStackTrace();
              }

            }
            
//              JsonObject jsonObj = (JsonObject) JsonParser.parseReader(new FileReader(artifact));
//              String resourceType = jsonObj.get("resourceType").getAsString();
//
//              if(RESOURCES_IN_SCOPE_MAP.contains(resourceType)) {
//                  Resource resource = new Gson().fromJson(jsonObj, Resource.class);
//
//                  if(resource.getResourceType() == ResourceType.StructureDefinition) {
//                      String type = jsonObj.get("type").getAsString();
//
//                      if(type.equals("Extension")) {
//                          igArtifacts.addExtension(resource.url, resource);
//                      }
//                      else {
//                          igArtifacts.addProfile(resource);
//                      }
//                  }
//                  else if(resource.resourceType.equals("ValueSet")) {
//                      for(ValueSet valueSet : resource.compose.getList()) {
//                          valueSet.setCodes();
//                      }
//                      igArtifacts.addValueSet(resource.url, resource);
//                  }
//              }
          }
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
