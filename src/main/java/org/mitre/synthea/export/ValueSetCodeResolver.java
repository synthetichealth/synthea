package org.mitre.synthea.export;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.mitre.synthea.helpers.RandomCodeGenerator;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.ImagingStudy;
import org.mitre.synthea.world.concepts.HealthRecord.ImagingStudy.Instance;
import org.mitre.synthea.world.concepts.HealthRecord.ImagingStudy.Series;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;
import org.mitre.synthea.world.concepts.HealthRecord.Report;

/**
 * This class knows how to search through all the Code values within a Person object, generating
 * random codes using the ValueSet URI if one has been supplied.
 */
public class ValueSetCodeResolver {
  
  private final Person person;

  public ValueSetCodeResolver(@Nonnull Person person) {
    this.person = person;
  }

  /**
   * Generates random codes in any coded fields that have specified a ValueSet URI.
   * 
   * @return the updated Person object
   */
  public Person resolve() {
    resolveCodesInHealthRecord(person.record);
    resolveCodesInAttributes(person.attributes);
    return person;
  }

  private void resolveCodesInHealthRecord(@Nullable HealthRecord healthRecord) {
    if (healthRecord == null) {
      return;
    }
    healthRecord.encounters.forEach(this::resolveCodesInEncounter);
    healthRecord.present.values().forEach(this::resolveCodesInEntry);
  }

  private void resolveCodesInEncounter(@Nullable Encounter encounter) {
    if (encounter == null) {
      return;
    }
    encounter.observations.forEach(this::resolveCodesInEntry);
    encounter.reports.forEach(this::resolveCodesInEntry);
    encounter.conditions.forEach(this::resolveCodesInEntry);
    encounter.allergies.forEach(this::resolveCodesInEntry);
    encounter.procedures.forEach(this::resolveCodesInEntry);
    encounter.immunizations.forEach(this::resolveCodesInEntry);
    encounter.medications.forEach(this::resolveCodesInEntry);
    encounter.careplans.forEach(this::resolveCodesInEntry);
    encounter.imagingStudies.forEach(this::resolveCodesInEntry);
    encounter.devices.forEach(this::resolveCodesInEntry);
    encounter.reason = resolveCode(encounter.reason);
    encounter.discharge = resolveCode(encounter.discharge);
  }

  private void resolveCodesInEntry(@Nullable Entry entry) {
    if (entry == null) {
      return;
    }
    
    // Resolve codes in any Code-types Entry fields.
    entry.codes = resolveCodes(entry.codes);
    
    // Resolve codes in any fields specific to subtypes of Entry.
    Class<? extends Entry> entryClass = entry.getClass();
    if (entryClass.equals(Encounter.class)) {
      resolveCodesInEncounter((Encounter) entry);
    } else if (entryClass.equals(Observation.class)) {
      resolveCodesInObservation((Observation) entry);
    } else if (entryClass.equals(Report.class)) {
      resolveCodesInReport((Report) entry);
    } else if (entryClass.equals(Procedure.class)) {
      resolveCodesInProcedure((Procedure) entry);
    } else if (entryClass.equals(Medication.class)) {
      resolveCodesInMedication((Medication) entry);
    } else if (entryClass.equals(CarePlan.class)) {
      resolveCodesInCarePlan((CarePlan) entry);
    } else if (entryClass.equals(ImagingStudy.class)) {
      resolveCodesInImagingStudy((ImagingStudy) entry);
    }
  }

  private void resolveCodesInObservation(@Nullable Observation observation) {
    if (observation == null) {
      return;
    }
    observation.value = resolveMaybeCode(observation.value);
  }

  private void resolveCodesInReport(@Nullable Report report) {
    if (report == null) {
      return;
    }
    report.observations.forEach(this::resolveCodesInObservation);
  }

  private void resolveCodesInProcedure(@Nullable Procedure procedure) {
    if (procedure == null) {
      return;
    }
    procedure.codes = resolveCodes(procedure.codes);
  }

  private void resolveCodesInMedication(@Nullable Medication medication) {
    if (medication == null) {
      return;
    }
    medication.reasons = resolveCodes(medication.reasons);
    medication.stopReason = resolveCode(medication.stopReason);
  }

  private void resolveCodesInCarePlan(@Nullable CarePlan carePlan) {
    if (carePlan == null) {
      return;
    }
    carePlan.activities = resolveCodes(carePlan.activities);
    carePlan.reasons = resolveCodes(carePlan.reasons);
    carePlan.stopReason = resolveCode(carePlan.stopReason);
  }

  private void resolveCodesInImagingStudy(@Nullable ImagingStudy imagingStudy) {
    if (imagingStudy == null) {
      return;
    }
    imagingStudy.series.forEach(this::resolveCodesInSeries);
  }

  private void resolveCodesInSeries(@Nullable Series series) {
    if (series == null) {
      return;
    }
    series.bodySite = resolveCode(series.bodySite);
    series.modality = resolveCode(series.modality);
    series.instances.forEach(this::resolveCodesInInstance);
  }

  private void resolveCodesInInstance(@Nullable Instance instance) {
    if (instance == null) {
      return;
    }
    instance.sopClass = resolveCode(instance.sopClass);
  }

  private void resolveCodesInAttributes(@Nullable Map<String, Object> attributes) {
    if (attributes == null) {
      return;
    }
    attributes.forEach((key, value) -> attributes.put(key, resolveMaybeCode(value)));
  }

  private Object resolveMaybeCode(@Nullable Object object) {
    if (object == null) {
      return null;
    }
    return object.getClass() == Code.class
           ? resolveCode((Code) object)
           : object;
  }

  private Code resolveCode(@Nullable Code code) {
    if (code == null) {
      return null;
    }
    return code.valueSet != null
           ? RandomCodeGenerator.getCode(code.valueSet, person.seed)
           : code;
  }
  
  private List<Code> resolveCodes(@Nullable List<Code> codes) {
    if (codes == null) {
      return null;
    }
    return codes.stream().map(this::resolveCode).collect(Collectors.toList());
  }

  private Set<Code> resolveCodes(@Nullable Set<Code> codes) {
    if (codes == null) {
      return null;
    }
    return codes.stream().map(this::resolveCode).collect(Collectors.toSet());
  }

}
