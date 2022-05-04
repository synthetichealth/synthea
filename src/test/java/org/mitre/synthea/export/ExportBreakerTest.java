package org.mitre.synthea.export;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.mdht.uml.cda.util.BasicValidationHandler;
import org.eclipse.mdht.uml.cda.util.CDAUtil;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

/**
 * This class isn't intended to be a part of the regular test suite. That is why it is marked as
 * ignore. The purpose of this class is to have a place where an exporter can be run on a ton of
 * people until you can find a case where it breaks. This is also set up to run generation, export
 * and validation concurrently, which hopefully helps identify issues more quickly.
 */
@Ignore
public class ExportBreakerTest {
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void runUntilBreaking() throws Exception {
    int numberOfPeople = 10000;
    Generator generator = new Generator(numberOfPeople);
    generator.options.overflow = false;
    TestHelper.loadTestProperties();
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Massachusetts");
    Config.set("exporter.baseDirectory", tempFolder.newFolder().toString());
    // This is currently set up for CCDA testing, but it should be easy to remove this and do FHIR
    // testing or whatever else needs to be run.
    CDAUtil.loadPackages();
    TestHelper.exportOff();
    Config.set("exporter.ccda.export", "true");
    // I picked 6 because it allows me to run tests on my 8 core machine, but have it still be
    // usable for other things.
    ExecutorService service = Executors.newFixedThreadPool(6);
    for (int i = 0; i < numberOfPeople; i++) {
      final int personIndex = i;
      service.submit(() -> {
        try {
          Person p = generator.generatePerson(personIndex);
          // Export work goes here
          String ccdaXml = CCDAExporter.export(p, System.currentTimeMillis());
          InputStream inputStream = IOUtils.toInputStream(ccdaXml, "UTF-8");
          // Validation work goes here
          CDAUtil.load(inputStream, new BasicValidationHandler() {
            public void handleError(Diagnostic diagnostic) {
              System.out.println("ERROR: " + diagnostic.getMessage());
              System.out.println(ccdaXml);
            }
          });
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });

    }
    service.shutdown();
    service.awaitTermination(1, TimeUnit.HOURS);

  }
}