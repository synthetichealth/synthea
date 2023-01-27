package org.mitre.synthea.export.flexporter;

import ca.uhn.fhir.context.BaseRuntimeChildDefinition;
import ca.uhn.fhir.context.RuntimeResourceDefinition;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAmount;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.TimeType;
import org.mitre.synthea.export.FhirR4;

public abstract class FieldWrapper {
  protected BaseRuntimeChildDefinition fieldDef;

  public FieldWrapper(String fieldPath) {
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

  public FieldWrapper(Class<? extends Resource> clazz, String fieldName) {
    init(clazz, fieldName);
  }

  private void init(Class<? extends Resource> clazz, String fieldName) {
    RuntimeResourceDefinition rd = FhirR4.getContext().getResourceDefinition(clazz);
    // TODO: this only gets top-level fields. update to suport nested fields
    this.fieldDef = rd.getChildByName(fieldName);
    if (this.fieldDef == null) {
      System.out.println("breakpoint");
    }
  }

  public IBase getSingle(Resource resource) {
    return fieldDef.getAccessor().getFirstValueOrNull(resource).orElse(null);
  }

  public List<IBase> getAll(Resource resource) {
    return fieldDef.getAccessor().getValues(resource);
  }

  public void set(Resource resource, IBase value) {
    fieldDef.getMutator().setValue(resource, value);
  }


  public static class DateFieldWrapper extends FieldWrapper {

    public DateFieldWrapper(String fieldPath) {
      super(fieldPath);
    }

    public DateFieldWrapper(Class<? extends Resource> clazz, String fieldName) {
      super(clazz, fieldName);
    }

    private static LocalDateTime normalize(IBase rawValue) {
      if (!(rawValue instanceof Base)) {
        // should never happen, not sure it's possible
        return null;
      }

      Base rawValueBase = (Base) rawValue;
      if (rawValueBase.isPrimitive()) {
        String strValue = rawValueBase.primitiveValue();
        return ZonedDateTime.parse(strValue).toLocalDateTime();

      } else {
        // TODO - this would happen if it's a Period
        throw new IllegalArgumentException("Unable to normalize " + rawValue);
      }
    }

    public static Period shift(Period period, TemporalAmount amount) {
      if (period == null) {
        return null;
      }

      period.setStartElement(shift(period.getStartElement(), amount));
      period.setEndElement(shift(period.getEndElement(), amount));

      return period;
    }

    public static DateTimeType shift(DateTimeType dateTime, TemporalAmount amount) {
      if (dateTime == null) {
        return null;
      }

      Date d = dateTime.getValue();

      Instant shifted = d.toInstant().plus(amount);

      d.setTime(shifted.toEpochMilli());

      // TODO: is it necessary to call dateTime.setValue(d) again?

      return dateTime;
    }

    public static DateType shift(DateType date, TemporalAmount amount) {
      if (date == null) {
        return null;
      }

      Date d = date.getValue();

      Instant shifted = d.toInstant().plus(amount);

      d.setTime(shifted.toEpochMilli());

      // TODO: is it necessary to call date.setValue(d) again?

      return date;
    }

    public static TimeType shift(TimeType time, TemporalAmount amount) {
      if (time == null) {
        return null;
      }

      String t = time.getValue();

      LocalTime localTime = LocalTime.parse(t).plus(amount);

      time.setValue(localTime.toString());

      return time;
    }

    public static InstantType shift(InstantType instant, TemporalAmount amount) {
      if (instant == null) {
        return null;
      }

      Date d = instant.getValue();

      Instant shifted = d.toInstant().plus(amount);

      d.setTime(shifted.toEpochMilli());

      // TODO: is it necessary to call instant.setValue(d) again?

      return instant;
    }

    public void shift(Resource resource, String amountString) {
      IBase value = getSingle(resource);
      if (value == null) {
        return;
      }

      // note: amount can either be <= 1 day with second precision, or > 1 day with day precision
      // not both
      // (for example you can't shift by a year and 3 hours)
      TemporalAmount amount = null;

      if (amountString.contains("Y") || (amountString.indexOf('M') < amountString.indexOf('T'))) {
        // ISO-8601 period formats {@code PnYnMnD}
        // if we see Y, or M before T, it's a Period
        amount = java.time.Period.parse(amountString);
      } else {
        // ISO-8601 duration format {@code PnDTnHnMn.nS}
        amount = Duration.parse(amountString);
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
        throw new IllegalArgumentException("Unexpected value for a temporal field: " + value);
      }

      set(resource, newValue);
    }

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

        return (myValue.isAfter(min) || myValue.isEqual(min))
            && (myValue.isBefore(max) || myValue.isEqual(max));

      } else {
        // debatable which way this should go. we have a date field set to null,
        // does that mean it's in the given range?
        return true;
      }
    }
  }

  public static class ReferenceFieldWrapper extends FieldWrapper {
    public ReferenceFieldWrapper(String fieldPath) {
      super(fieldPath);
    }

    public ReferenceFieldWrapper(Class<? extends Resource> clazz, String fieldName) {
      super(clazz, fieldName);
    }

    public String getReference(Resource resource) {
      IBase referenceObject = getSingle(resource);
      if (!(referenceObject instanceof Reference)) {
        return null;
      }
      Reference ref = (Reference)referenceObject;
      return ref.getReference();
    }

    public List<String> getReferences(Resource resource) {
      List<IBase> referenceObjects = getAll(resource);

      return referenceObjects.stream()
          .map(ro -> ((Reference)ro).getReference())
          .collect(Collectors.toList());
    }
  }
}
