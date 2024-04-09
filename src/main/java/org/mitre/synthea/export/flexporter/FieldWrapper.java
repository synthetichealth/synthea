package org.mitre.synthea.export.flexporter;

import ca.uhn.fhir.context.BaseRuntimeChildDefinition;
import ca.uhn.fhir.context.RuntimeResourceDefinition;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAmount;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.CareTeam;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Device;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.ExplanationOfBenefit;
import org.hl7.fhir.r4.model.Goal;
import org.hl7.fhir.r4.model.ImagingStudy;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Media;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.SupplyDelivery;
import org.hl7.fhir.r4.model.TimeType;
import org.mitre.synthea.export.FhirR4;

public abstract class FieldWrapper {
  protected Function<Resource, List<IBase>> getter;
  protected BiConsumer<Resource, IBase> setter;

  /**
   * Create a FieldWrapper for the field defined by the given path.
   *
   * @param fieldPath Path to FHIR field (not FHIRPath). Should be of the form:
   *     Resource.field.nestedField.nested2...
   */
  public FieldWrapper(String fieldPath) {
    // split at the first . so we get two items in the array:
    // [0] = resource, [1] = field[.nestedField etc]
    String[] pathParts = fieldPath.split("\\.", 2);

    try {
      String className = "org.hl7.fhir.r4.model." + pathParts[0];

      @SuppressWarnings("unchecked")
      Class<? extends Resource> clazz =
           (Class<? extends Resource>) Class.forName(className);
      init(clazz, pathParts[1]);
    } catch (Exception e) {
      // means we defined some classname that isn't a resource type
      throw new RuntimeException(e);
    }
  }

  /**
   * Create a FieldWrapper for the field on the given resource type defined by the given path.
   *
   * @param fieldName Path to FHIR field (not FHIRPath). Should not include resourceType and
   *     should be of the form: field.nestedField.nested2...
   */
  public FieldWrapper(Class<? extends Resource> clazz, String fieldName) {
    init(clazz, fieldName);
  }

  private void init(Class<? extends Resource> clazz, String fieldName) {
    RuntimeResourceDefinition rd = FhirR4.getContext().getResourceDefinition(clazz);
    if (fieldName.contains(".")) {
      // the .stream() and everything after it is just turning List<Base> into List<IBase> ugh
      this.getter = (resource) ->
                      FhirPathUtils.evaluateResource(resource, fieldName)
                        .stream()
                        .map(b -> (IBase)b)
                        .collect(Collectors.toList());
      this.setter = (resource, value) ->
                      CustomFHIRPathResourceGeneratorR4.setField(resource, fieldName, value);
    } else {
      BaseRuntimeChildDefinition fieldDef = rd.getChildByName(fieldName);
      if (fieldDef == null) {
        // maybe it's a choice type.
        fieldDef = rd.getChildByName(fieldName + "[x]");
        if (fieldDef == null) {
          throw new IllegalArgumentException(
              "Unknown field " + fieldName + " on resourceType " + clazz.getSimpleName());
        }

      }
      this.getter = fieldDef.getAccessor()::getValues;
      this.setter = fieldDef.getMutator()::setValue;
    }
  }

  /**
   * Get a single value from the field this FieldWrapper represents from the given resource.
   * For fields that may have more than a single value, use getAll(Resource) instead.
   * Calling this on a field that has more than one value will return only the first value.
   * @param resource Resource to get value from
   * @return the value from the field
   */
  public IBase getSingle(Resource resource) {
    return getAll(resource).stream().findFirst().orElse(null);
  }

  /**
   * Get the values from the field this FieldWrapper represents from the given resource.
   * @param resource Resource to get value from
   * @return the list of values from the field
   */
  public List<IBase> getAll(Resource resource) {
    return this.getter.apply(resource);
  }

  /**
   * Set the given value for the field this FieldWrapper represents on the given resource.
   * @param resource The resource to set a value on
   * @param value The value to set
   */
  public void set(Resource resource, IBase value) {
    this.setter.accept(resource, value);
  }


  /**
   * DateFieldWrapper is used to represent a field on a resource representing a date and/or time.
   * This wrapper offers convenience features such as normalizing the various types that a FHIR
   * field can be to a LocalDateTime, checking if the value is in a given range,
   * and shifting the value by a given amount.
   */
  public static class DateFieldWrapper extends FieldWrapper {
    public DateFieldWrapper(String fieldPath) {
      super(fieldPath);
    }

    public DateFieldWrapper(Class<? extends Resource> clazz, String fieldName) {
      super(clazz, fieldName);
    }

    /**
     * Normalize the given raw date value into a LocalDateTime. FHIR values can be a number of
     * different types so this checks which one the value is and converts accordingly.
     * @param rawValue Original raw FHIR value
     * @return parsed date as LocalDateTime
     */
    private static LocalDateTime normalize(IBase rawValue) {
      if (!(rawValue instanceof Base)) {
        // should never happen, not sure it's possible
        // (though this also catches nulls)
        return null;
      }

      Base rawValueBase = (Base) rawValue;
      if (rawValueBase.isPrimitive()) {

        String strValue = rawValueBase.primitiveValue();

        if (rawValueBase instanceof DateType) {
          return LocalDate.parse(strValue).atStartOfDay();
        } else if (rawValueBase instanceof DateTimeType) {
          return ZonedDateTime.parse(strValue).toLocalDateTime();
        } else if (rawValueBase instanceof DateType) {
          return ZonedDateTime.parse(strValue).toLocalDateTime();
        } else if (rawValueBase instanceof TimeType) {
          return ZonedDateTime.parse(strValue).toLocalDateTime();
        } else if (rawValueBase instanceof InstantType) {
          return ZonedDateTime.parse(strValue).toLocalDateTime();
        }
      }

      // various reasons we arrive here, for example a choice type that includes non-time values
      return null;
    }

    /**
     * Shift the Period by the given TemporalAmount, by shifting both the start and end
     * elements of the Period by the given amount.
     * @param period Period to shift
     * @param amount TemporalAmount to shift by
     * @return the shifted Period (note: the same object instance as passed in)
     */
    static Period shift(Period period, TemporalAmount amount) {
      if (period == null) {
        return null;
      }

      period.setStartElement(shift(period.getStartElement(), amount));
      period.setEndElement(shift(period.getEndElement(), amount));
      return period;
    }

    static DateTimeType shift(DateTimeType dateTime, TemporalAmount amount) {
      if (dateTime == null) {
        return null;
      }

      String s = dateTime.getValueAsString();
      ZonedDateTime shifted = ZonedDateTime.parse(s).plus(amount);
      dateTime.setValueAsString(shifted.toString());
      return dateTime;
    }

    static DateType shift(DateType date, TemporalAmount amount) {
      if (date == null) {
        return null;
      }

      String s = date.getValueAsString();
      LocalDate shifted = LocalDate.parse(s).plus(amount);
      date.setValueAsString(shifted.toString());
      return date;
    }

    static TimeType shift(TimeType time, TemporalAmount amount) {
      if (time == null) {
        return null;
      }
      if (amount instanceof java.time.Period) {
        // trying to shift years/months/days but we only have a time of day
        // so just keep it the same
        return time;
      }

      String t = time.getValue();
      LocalTime localTime = LocalTime.parse(t).plus(amount);
      time.setValue(localTime.toString());
      return time;
    }

    static InstantType shift(InstantType instant, TemporalAmount amount) {
      if (instant == null) {
        return null;
      }

      String s = instant.getValueAsString();

      // note that we use ZonedDateTime vs java Instant here
      // because Instant doesn't allow shifting by time units > Days
      ZonedDateTime shifted = ZonedDateTime.parse(s).plus(amount);

      instant.setValueAsString(shifted.toString());

      return instant;
    }

    /**
     * Shift the value of the field represented by this FieldWrapper by the given amount.
     * @param resource Resource to modify the value on
     * @param amount Amount to shift the date by
     */
    public void shift(Resource resource, TemporalAmount amount) {
      IBase value = getSingle(resource);
      if (value == null) {
        return;
      }

      IBase newValue;

      if (value instanceof Period) {
        newValue = shift((Period) value, amount);
      } else if (value instanceof DateTimeType) {
        newValue = shift((DateTimeType) value, amount);
      } else if (value instanceof DateType) {
        newValue = shift((DateType) value, amount);
      } else if (value instanceof TimeType) {
        newValue = shift((TimeType) value, amount);
      } else if (value instanceof InstantType) {
        newValue = shift((InstantType) value, amount);
      } else {
        // many choice fields have both temporal and  non-temporal options,
        // for example Observation.value
        // just do nothing

        return;
      }

      set(resource, newValue);
    }

    /**
     * Tests if the value of the field represented by this FieldWrapper is in the given range.
     * At least one or both of min or max is required, an "open range" is possible by
     * providing only one.
     * @param resource Resource to get the value from
     * @param min Earliest/start value of the time range
     * @param max Latest/stop/end value of the time range
     * @return Whether the value is in the range.
     */
    public boolean valueInRange(Resource resource, LocalDateTime min, LocalDateTime max) {
      if (min == null && max == null) {
        return true;
      }

      if (min == null) {
        min = LocalDateTime.MIN;
      }

      if (max == null) {
        max = LocalDateTime.MAX;
      }

      IBase value = this.getSingle(resource);

      if (value instanceof Period) {
        Period period = (Period) value;

        Date startDt = period.getStart();
        LocalDateTime start = startDt.toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime();
        Date endDt = period.getEnd();

        if (endDt == null) {
          // use the same single value check as if this were not a Period
          return (start.isAfter(min) || start.isEqual(min))
              && (start.isBefore(max) || start.isEqual(max));
        }

        LocalDateTime end = endDt.toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime();

        // https://stackoverflow.com/a/325964
        // (StartA <= EndB) and (EndA >= StartB)

        return (start.isBefore(max) || start.isEqual(max))
            && (end.isAfter(min) || end.isEqual(min));

      } else if (value != null) {
        LocalDateTime myValue = normalize(value);

        if (myValue == null) {
          return true;
          // probably wasn't actually a temporal value
        }

        return (myValue.isAfter(min) || myValue.isEqual(min))
            && (myValue.isBefore(max) || myValue.isEqual(max));

      } else {
        // debatable which way this should go. we have a date field set to null,
        // does that mean it's in the given range?
        return true;
      }
    }
  }

  public static final Map<ResourceType, List<DateFieldWrapper>> DATE_FIELDS = buildDateFields();

  // TODO: this could instead iterate over all fields on relevant resources,
  // which might be cleaner or slower, especially if it means a lot of iteration
  // over resource types or fields we never use.
  // A human would do it by looking at all fields that are actually present on a resource
  // and see if they are dates. But I don't see an easy way to do that here
  private static Map<ResourceType, List<DateFieldWrapper>> buildDateFields() {
    Map<ResourceType, List<DateFieldWrapper>> dateFields = new HashMap<>();

    dateFields.put(ResourceType.Patient, List.of(
        new DateFieldWrapper(Patient.class, "birthDate"),
        new DateFieldWrapper(Patient.class, "deceased")
        ));

    dateFields.put(ResourceType.Encounter, List.of(
        new DateFieldWrapper(Encounter.class, "period")
      ));

    dateFields.put(ResourceType.Condition, List.of(
          new DateFieldWrapper(Condition.class, "onset"),
          new DateFieldWrapper(Condition.class, "abatement"),
          new DateFieldWrapper(Condition.class, "recordedDate")
        ));

    dateFields.put(ResourceType.AllergyIntolerance, List.of(
        new DateFieldWrapper(AllergyIntolerance.class, "recordedDate")
      ));

    dateFields.put(ResourceType.Procedure, List.of(
        new DateFieldWrapper(Procedure.class, "performed") // Period or dateTime
      ));

    dateFields.put(ResourceType.Observation, List.of(
        new DateFieldWrapper(Observation.class, "effective"),
        // new DateFieldWrapper(Observation.class, "value"),
        // value could technically be a date but I don't think we ever use that
        new DateFieldWrapper(Observation.class, "issued")
        ));

    dateFields.put(ResourceType.MedicationRequest, List.of(
        new DateFieldWrapper(MedicationRequest.class, "authoredOn")
      ));

    dateFields.put(ResourceType.MedicationAdministration, List.of(
        new DateFieldWrapper(MedicationAdministration.class, "effective")
      ));

    dateFields.put(ResourceType.CarePlan, List.of(
        new DateFieldWrapper(CarePlan.class, "period")
      ));

    dateFields.put(ResourceType.Goal, List.of(
        new DateFieldWrapper(Goal.class, "target.due")
      ));

    dateFields.put(ResourceType.CareTeam, List.of(
        new DateFieldWrapper(CareTeam.class, "period")
      ));

    dateFields.put(ResourceType.ImagingStudy, List.of(
        new DateFieldWrapper(ImagingStudy.class, "started"),
        new DateFieldWrapper(ImagingStudy.class, "series.started")
      ));

    dateFields.put(ResourceType.DiagnosticReport, List.of(
        new DateFieldWrapper(DiagnosticReport.class, "effective"),
        new DateFieldWrapper(DiagnosticReport.class, "issued")
      ));

    dateFields.put(ResourceType.Device, List.of(
        new DateFieldWrapper(Device.class, "manufactureDate"),
        new DateFieldWrapper(Device.class, "expirationDate")
      ));

    dateFields.put(ResourceType.SupplyDelivery, List.of(
        new DateFieldWrapper(SupplyDelivery.class, "occurrence")
      ));

    dateFields.put(ResourceType.ExplanationOfBenefit, List.of(
        new DateFieldWrapper(ExplanationOfBenefit.class, "billablePeriod"),
        new DateFieldWrapper(ExplanationOfBenefit.class, "procedure.date")
      ));

    dateFields.put(ResourceType.Provenance, List.of(
        new DateFieldWrapper(Provenance.class, "recorded")
      ));

    return dateFields;
  }

  public static class ReferenceFieldWrapper extends FieldWrapper {
    public ReferenceFieldWrapper(String fieldPath) {
      super(fieldPath);
    }

    public ReferenceFieldWrapper(Class<? extends Resource> clazz, String fieldName) {
      super(clazz, fieldName);
    }

    /**
     * Get a single reference from the field this FieldWrapper represents on the given resource,
     * as a string.
     * @param resource Resource to get the value from
     * @return Reference as string, ex. "Patient/123" or "urn:uuid:98d1c..."
     */
    public String getReference(Resource resource) {
      IBase referenceObject = getSingle(resource);
      if (!(referenceObject instanceof Reference)) {
        return null;
      }
      Reference ref = (Reference)referenceObject;
      return ref.getReference();
    }

    /**
     * Get all references from the field this FieldWrapper represents on the given resource,
     * as a list of strings.
     * @param resource Resource to get the value from
     * @return References as strings, ex. "Patient/123" or "urn:uuid:98d1c..."
     */
    public List<String> getReferences(Resource resource) {
      List<IBase> referenceObjects = getAll(resource);

      return referenceObjects.stream()
          .map(ro -> {
            if (!(ro instanceof Reference)) {
              return null;
            }
            return ((Reference)ro).getReference();
          })
          .filter(ro -> ro != null)
          .collect(Collectors.toList());
    }
  }

  public static final Map<ResourceType, List<ReferenceFieldWrapper>> REFERENCE_FIELDS =
      buildReferenceFields();

  private static Map<ResourceType, List<ReferenceFieldWrapper>> buildReferenceFields() {
    Map<ResourceType, List<ReferenceFieldWrapper>> refFields = new HashMap<>();

    refFields.put(ResourceType.Encounter, List.of(
        new ReferenceFieldWrapper(Encounter.class, "subject"),
        new ReferenceFieldWrapper(Encounter.class, "location.location"),
        new ReferenceFieldWrapper(Encounter.class, "participant.individual")
      ));

    refFields.put(ResourceType.Condition, List.of(
          new ReferenceFieldWrapper(Condition.class, "subject"),
          new ReferenceFieldWrapper(Condition.class, "encounter")
        ));

    refFields.put(ResourceType.AllergyIntolerance, List.of(
        new ReferenceFieldWrapper(AllergyIntolerance.class, "patient")
      ));

    refFields.put(ResourceType.Procedure, List.of(
        new ReferenceFieldWrapper(Procedure.class, "subject"),
        new ReferenceFieldWrapper(Procedure.class, "encounter"),
        new ReferenceFieldWrapper(Procedure.class, "reasonReference")
      ));

    refFields.put(ResourceType.MedicationRequest, List.of(
        new ReferenceFieldWrapper(MedicationRequest.class, "subject"),
        new ReferenceFieldWrapper(MedicationRequest.class, "encounter"),
        new ReferenceFieldWrapper(MedicationRequest.class, "medication"),
        new ReferenceFieldWrapper(MedicationRequest.class, "reasonReference")
      ));

    refFields.put(ResourceType.MedicationAdministration, List.of(
        new ReferenceFieldWrapper(MedicationAdministration.class, "subject"),
        new ReferenceFieldWrapper(MedicationAdministration.class, "context"),
        new ReferenceFieldWrapper(MedicationAdministration.class, "reasonReference")
      ));

    refFields.put(ResourceType.Observation, List.of(
        new ReferenceFieldWrapper(Observation.class, "subject"),
        new ReferenceFieldWrapper(Observation.class, "encounter")
      ));

    refFields.put(ResourceType.DiagnosticReport, List.of(
        new ReferenceFieldWrapper(DiagnosticReport.class, "subject"),
        new ReferenceFieldWrapper(DiagnosticReport.class, "encounter"),
        new ReferenceFieldWrapper(DiagnosticReport.class, "result")
      ));

    refFields.put(ResourceType.CarePlan, List.of(
        new ReferenceFieldWrapper(CarePlan.class, "subject"),
        new ReferenceFieldWrapper(CarePlan.class, "encounter"),
        new ReferenceFieldWrapper(CarePlan.class, "careTeam"),
        new ReferenceFieldWrapper(CarePlan.class, "addresses"),
        new ReferenceFieldWrapper(CarePlan.class, "activity.detail.reason"),
        new ReferenceFieldWrapper(CarePlan.class, "goal")
      ));

    refFields.put(ResourceType.Goal, List.of(
        new ReferenceFieldWrapper(Goal.class, "subject"),
        new ReferenceFieldWrapper(Goal.class, "addresses")
      ));

    refFields.put(ResourceType.CareTeam, List.of(
        new ReferenceFieldWrapper(CareTeam.class, "subject"),
        new ReferenceFieldWrapper(CareTeam.class, "encounter")
      ));

    refFields.put(ResourceType.Immunization, List.of(
        new ReferenceFieldWrapper(Immunization.class, "patient"),
        new ReferenceFieldWrapper(Immunization.class, "encounter")
      ));

    refFields.put(ResourceType.Device, List.of(
        new ReferenceFieldWrapper(Device.class, "patient")
      ));

    refFields.put(ResourceType.SupplyDelivery, List.of(
        new ReferenceFieldWrapper(SupplyDelivery.class, "patient")
      ));

    refFields.put(ResourceType.ImagingStudy, List.of(
        new ReferenceFieldWrapper(ImagingStudy.class, "subject"),
        new ReferenceFieldWrapper(ImagingStudy.class, "encounter")
      ));

    refFields.put(ResourceType.DocumentReference, List.of(
        new ReferenceFieldWrapper(DocumentReference.class, "subject")
      ));

    refFields.put(ResourceType.Media, List.of(
        new ReferenceFieldWrapper(Media.class, "subject"),
        new ReferenceFieldWrapper(Media.class, "encounter")
      ));

    refFields.put(ResourceType.Claim, List.of(
        new ReferenceFieldWrapper(Claim.class, "patient"),
        new ReferenceFieldWrapper(Claim.class, "item.encounter"),
        new ReferenceFieldWrapper(Claim.class, "prescription"),
        new ReferenceFieldWrapper(Claim.class, "procedure.procedure"),  // procedureReference
        new ReferenceFieldWrapper(Claim.class, "diagnosis.diagnosis")  // diagnosisReference

      ));

    refFields.put(ResourceType.ExplanationOfBenefit, List.of(
        new ReferenceFieldWrapper(ExplanationOfBenefit.class, "patient"),
        new ReferenceFieldWrapper(ExplanationOfBenefit.class, "claim"),
        new ReferenceFieldWrapper(ExplanationOfBenefit.class, "provider")
      ));

    refFields.put(ResourceType.Provenance, List.of(
        new ReferenceFieldWrapper(Provenance.class, "target")
      ));

    return refFields;
  }
}
