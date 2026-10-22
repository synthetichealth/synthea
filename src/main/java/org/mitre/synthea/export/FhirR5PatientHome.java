package org.mitre.synthea.export;

import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.Identifier;
import org.hl7.fhir.r5.model.Location;

/**
 * Singleton class to manage the instance of the "Patient's Home" Location resource.
 * FHIR states that when there is a virtual or telehealth encounter, the Location should point to a
 * "kind" resource that represents the patient's home. That means a FHIR based system needs to
 * keep track of the single patient home resource, which is what this class does.
 */
public class FhirR5PatientHome {
  private static Location patientHome = null;

  /**
   * Provides the one and only patient home Location.
   * @return a Location resource
   */
  public static Location getPatientHome() {
    if (patientHome == null) {
      patientHome = new Location();
      patientHome.setMode(Location.LocationMode.KIND);
      patientHome.setStatus(Location.LocationStatus.ACTIVE);
      // Note: In FHIR R5, setType is used instead of setPhysicalType
      patientHome.setType(java.util.Arrays.asList(new CodeableConcept()
          .addCoding(new Coding()
              .setCode("ho")
              .setSystem("http://terminology.hl7.org/CodeSystem/location-physical-type")
              .setDisplay("House"))));
      patientHome.setDescription("Patient's Home");
      patientHome.setId("bb1ad573-19b8-9cd8-68fb-0e6f684df992");
      Identifier identifier = patientHome.addIdentifier();
      identifier.setSystem(FhirR5.SYNTHEA_IDENTIFIER);
      identifier.setValue(patientHome.getId());
    }
    return patientHome;
  }
}
