package org.mitre.synthea.export;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Hospital;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

public class HospitalDSTU2ExporterTest {

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void testFHIRDSTU2Export() throws Exception {
    FhirContext ctx = FhirContext.forDstu2();
    FhirValidator validator = ctx.newValidator();
    validator.setValidateAgainstStandardSchema(true);
    validator.setValidateAgainstStandardSchematron(true);

    File tempOutputFolder = tempFolder.newFolder();
    Config.set("exporter.baseDirectory", tempOutputFolder.toString());
    Config.set("exporter.hospital.fhir_dstu2.export", "true");
    Hospital.loadHospitals();
    assertNotNull(Hospital.getHospitalList());
    assertFalse(Hospital.getHospitalList().isEmpty());

    Hospital.getHospitalList().get(0).incrementEncounters(EncounterType.WELLNESS.toString(), 0);
    HospitalDSTU2Exporter.export(0L);

    File expectedExportFolder = tempOutputFolder.toPath().resolve("fhir_dstu2").toFile();
    assertTrue(expectedExportFolder.exists() && expectedExportFolder.isDirectory());

    File expectedExportFile = expectedExportFolder.toPath().resolve("hospitalInformation0.json")
        .toFile();
    assertTrue(expectedExportFile.exists() && expectedExportFile.isFile());

    FileReader fileReader = new FileReader(expectedExportFile.getPath());
    BufferedReader bufferedReader = new BufferedReader(fileReader);
    StringBuilder fhirJson = new StringBuilder();
    String line = null;
    while ((line = bufferedReader.readLine()) != null) {
      fhirJson.append(line);
    }
    bufferedReader.close();
    IBaseResource resource = ctx.newJsonParser().parseResource(fhirJson.toString());
    ValidationResult result = validator.validateWithResult(resource);
    if (result.isSuccessful() == false) {
      for (SingleValidationMessage message : result.getMessages()) {
        System.out.println(message.getSeverity().toString() + ": " + message.getMessage());
      }
    }
    assertTrue(result.isSuccessful());
  }
}
