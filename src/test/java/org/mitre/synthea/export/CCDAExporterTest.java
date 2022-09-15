package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.mdht.uml.cda.util.BasicValidationHandler;
import org.eclipse.mdht.uml.cda.util.CDAUtil;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.FailedExportHelper;
import org.mitre.synthea.ParallelTestingService;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.geography.Location;

/**
 * Uses Model Driven Health Tools (MDHT) to validate exported CCDA R2.1.
 * https://github.com/mdht/mdht-models
 */
public class CCDAExporterTest {

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @BeforeClass
  public static void loadCDAUtils() {
    CDAUtil.loadPackages();
  }

  @Test
  public void testCCDAExport() throws Exception {
    PayerManager.clear();
    PayerManager.loadPayers(new Location(Generator.DEFAULT_STATE, null));
    TestHelper.loadTestProperties();
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Massachusetts");
    Config.set("exporter.baseDirectory", tempFolder.newFolder().toString());
    List<String> errors = ParallelTestingService.runInParallel((person) -> {
      List<String> validationErrors = new ArrayList<String>();
      TestHelper.exportOff();
      Config.set("exporter.ccda.export", "true");
      String ccdaXml = CCDAExporter.export(person, System.currentTimeMillis());
      InputStream inputStream = IOUtils.toInputStream(ccdaXml, "UTF-8");
      try {
        CDAUtil.load(inputStream, new BasicValidationHandler() {
          public void handleError(Diagnostic diagnostic) {
            System.out.println("ERROR: " + diagnostic.getMessage());
            validationErrors.add(diagnostic.getMessage());
          }
        });
      } catch (Exception e) {
        e.printStackTrace();
        validationErrors.add(e.getMessage());
      }
      if (! validationErrors.isEmpty()) {
        FailedExportHelper.dumpInfo("CCDA", ccdaXml, validationErrors, person);
      }
      return validationErrors;
    });

    assertEquals("Validation of exported CCDA failed: "
        + String.join("|", errors), 0, errors.size());
  }

  @Test
  public void testExportWithNoPreferredWellnessProvider() throws Exception {
    PayerManager.clear();
    PayerManager.loadPayers(new Location(Generator.DEFAULT_STATE, null));
    TestHelper.loadTestProperties();
    Person[] people = TestHelper.getGeneratedPeople();
    List<String> validationErrors = new ArrayList<String>();
    TestHelper.exportOff();
    Config.set("exporter.ccda.export", "true");
    Optional<Person> personWithWellnessProvider = Arrays.stream(people).filter((person -> {
      return person.attributes.get(Person.PREFERREDYPROVIDER + "wellness") != null;
    })).findFirst();
    if (personWithWellnessProvider.isPresent()) {
      Person toExport = personWithWellnessProvider.get();
      toExport.attributes.remove(Person.PREFERREDYPROVIDER + "wellness");
      String ccdaXml = CCDAExporter.export(toExport, System.currentTimeMillis());
      InputStream inputStream = IOUtils.toInputStream(ccdaXml, "UTF-8");
      try {
        CDAUtil.load(inputStream, new BasicValidationHandler() {
          public void handleError(Diagnostic diagnostic) {
            System.out.println("ERROR: " + diagnostic.getMessage());
            validationErrors.add(diagnostic.getMessage());
          }
        });
      } catch (Exception e) {
        e.printStackTrace();
        validationErrors.add(e.getMessage());
      }
      assertEquals(0, validationErrors.size());
    } else {
      System.out.println("There were no people generated that have wellness providers... odd.");
    }
  }

  @Ignore("Manual test to debug failed CCDA exports.")
  @Test
  public void testFailedCCDAExports() throws Exception {
    System.out.println("Revalidating Failed CCDA Exports...");
    TestHelper.loadTestProperties();
    List<String> validationErrors = new ArrayList<String>();
    List<File> failures = FailedExportHelper.loadFailures("CCDA");
    for (File failure : failures) {
      System.out.println("Validating " + failure.getAbsolutePath() + "...");
      validationErrors.clear();
      String content = new String(Files.readAllBytes(failure.toPath()));
      InputStream inputStream = IOUtils.toInputStream(content, "UTF-8");
      try {
        CDAUtil.load(inputStream, new BasicValidationHandler() {
          public void handleError(Diagnostic diagnostic) {
            System.out.println("ERROR: " + diagnostic.getMessage());
            validationErrors.add(diagnostic.getMessage());
          }
        });
      } catch (Exception e) {
        e.printStackTrace();
      }
      if (validationErrors.isEmpty()) {
        System.out.println("  No validation errors.");
      } else {
        System.out.println("Validation Errors: " + validationErrors.size());
      }
    }
    System.out.println("Done.");
  }

}