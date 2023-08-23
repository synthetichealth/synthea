package org.mitre.synthea.export;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.codesystems.LocationPhysicalType;

/**
 * Singleton class to manage the instance of the "Patient's Home" Location resource.
 * FHIR states that when there is a virtual or telehealth encounter, the Location should point to a
 * "kind" resource that represents the patient's home. That means a FHIR based system needs to
 * keep track of the single patient home resource, which is what this class does.
 */
public class FhirR4PatientHome {
  private static Location patientHome = null;

  /**
   * Provides the one and only patient home Location.
   * @return a Location resource
   */
  public static Location getPatientHome() {
    if (patientHome == null) {
      patientHome = new Location();
      patientHome.setMode(org.hl7.fhir.r4.model.Location.LocationMode.KIND);
      patientHome.setStatus(Location.LocationStatus.ACTIVE);
      patientHome.setPhysicalType(new CodeableConcept()
          .addCoding(new Coding()
              .setCode(LocationPhysicalType.HO.toCode())
              .setSystem(LocationPhysicalType.HO.getSystem())
              .setDisplay(LocationPhysicalType.HO.getDisplay())
          ));
      patientHome.setDescription("Patient's Home");
      // Given that this is not tied to a particular provider or person,
      // this is a fixed value based on an arbitrary choice:
      // new Patient(1).randUUID().toString() --> bb1ad573-19b8-9cd8-68fb-0e6f684df992
      patientHome.setId("bb1ad573-19b8-9cd8-68fb-0e6f684df992");
      Identifier identifier = patientHome.addIdentifier();
      identifier.setSystem(FhirR4.SYNTHEA_IDENTIFIER);
      identifier.setValue(patientHome.getId());
    }
    return patientHome;
  }
}