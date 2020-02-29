package org.mitre.synthea.export;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.OutputStreamWriter;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.powermock.reflect.Whitebox;

public class CDWExporterTest {
  /**
   * Temporary folder for any exported files, guaranteed to be deleted at the end of the test.
   */
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void testCDWExport() throws Exception {
    TestHelper.exportOff();
    Config.set("exporter.cdw.export", "true");
    Config.set("generate.veteran_population_override", "true");
    File tempOutputFolder = tempFolder.newFolder();
    Config.set("exporter.baseDirectory", tempOutputFolder.toString());

    int numberOfPeople = 10;
    Generator generator = new Generator(numberOfPeople);
    generator.options.overflow = false;
    for (int i = 0; i < numberOfPeople; i++) {
      generator.generatePerson(i);
    }
    Config.set("generate.veteran_population_override", "false");
    CDWExporter.getInstance().writeFactTables();

    // Ensure the files are synchronized with the tempFolder...
    String[] variables = { "lookuppatient", "spatient", "spatientaddress", "spatientphone",
        "patientrace", "patientethnicity", "consult", "visit", "appointment", "inpatient",
        "immunization", "allergy", "allergicreaction", "allergycomment", "problemlist",
        "vdiagnosis", "rxoutpatient", "rxoutpatfill", "nonvamed", "cprsorder",
        "ordereditem", "labchem", "labpanel", "patientlabchem", "vprocedure",
        "surgeryProcedureDiagnosisCode", "surgeryPRE", "vitalSign" };
    for (String variable : variables) {
      OutputStreamWriter ow =
          Whitebox.<OutputStreamWriter>getInternalState(CDWExporter.getInstance(), variable);
      ow.close();
    }

    // if we get here we at least had no exceptions
    File expectedExportFolder = tempOutputFolder.toPath().resolve("cdw").toFile();
    assertTrue(expectedExportFolder.exists() && expectedExportFolder.isDirectory());

    for (File cdwFile : expectedExportFolder.listFiles()) {
      if (!cdwFile.getName().endsWith(".csv")) {
        continue;
      }
      System.out.println("Parsing " + cdwFile.getPath());

      String cdwData = new String(Files.readAllBytes(cdwFile.toPath()));

      // the CDW exporter doesn't use the SimpleCSV class to write the data,
      // so we can use it here for a level of validation.
      assertTrue(SimpleCSV.parse(cdwData).size() >= 0);
    }
  }
}
