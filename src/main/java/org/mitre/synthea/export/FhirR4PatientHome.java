package org.mitre.synthea.export;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.codesystems.LocationPhysicalType;
import org.mitre.synthea.world.agents.Person;

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
      // Not really generating a random UUID. Given that this is not tied to a particular provider
      // or person, this just makes up a person with a hardcoded random seed.
      patientHome.setId(new Person(1).randUUID().toString());
    }
    return patientHome;
  }
}