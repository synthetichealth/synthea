package org.mitre.synthea.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.hl7.fhir.r4.model.Group;
import org.hl7.fhir.r4.model.Group.GroupType;
import org.hl7.fhir.r4.model.Reference;
import org.mitre.synthea.helpers.Config;

public abstract class FhirGroupExporterR4 {

  private static final FhirContext FHIR_CTX = FhirContext.forR4();
  private static final List<String> patientList = new ArrayList<String>();

  /**
   * Reset the patient list to empty.
   */
  public static void resetPatientList() {
    patientList.clear();
  }

  /**
   * Add the patient to the patient list.
   * @param resourceId The resource ID of the patient.
   */
  public static synchronized void addPatient(String resourceId) {
    patientList.add(resourceId);
  }

  /**
   * Export the patient list as a FHIR Group resource.
   * @param stop The stop time.
   * @return FHIR Group resource.
   */
  public static Group export(long stop) {
    String uuid = UUID.randomUUID().toString();

    Group group = new Group();
    group.setId(uuid);
    group.addIdentifier()
      .setSystem("urn:ietf:rfc:3986")
      .setValue("urn:uuid:" + uuid);
    group.setActive(true);
    group.setType(GroupType.PERSON);
    group.setActual(true);
    group.setName("Synthea Patients");
    group.setQuantity(patientList.size());
    for (String resourceId : patientList) {
      group.addMember().setEntity(new Reference(FhirR4.getUrlPrefix("Patient") + resourceId));
    }
    return group;
  }

  /**
   * Export the current patient list as a FHIR Group resource and save it as a JSON file.
   */
  public static void exportAndSave(long stop) {
    if (Boolean.parseBoolean(Config.get("exporter.groups.fhir.export"))) {
      Group group = export(stop);

      // get output folder
      List<String> folders = new ArrayList<>();
      folders.add("fhir");
      String baseDirectory = Config.get("exporter.baseDirectory");
      File f = Paths.get(baseDirectory, folders.toArray(new String[0])).toFile();
      f.mkdirs();
      Path outFilePath = null;
      String groupJson = null;

      if (Boolean.parseBoolean(Config.get("exporter.fhir.bulk_data"))) {
        IParser parser = FHIR_CTX.newJsonParser().setPrettyPrint(false);
        groupJson = parser.encodeResourceToString(group);
        String filename = group.getResourceType().toString() + ".ndjson";
        outFilePath = f.toPath().resolve(filename);
      } else {
        IParser parser = FHIR_CTX.newJsonParser().setPrettyPrint(true);
        groupJson = parser.encodeResourceToString(group);
        outFilePath = f.toPath().resolve("groupInformation" + stop + ".json");
      }

      try {
        Files.write(outFilePath, Collections.singleton(groupJson), StandardOpenOption.CREATE_NEW);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}