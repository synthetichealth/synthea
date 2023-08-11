package org.mitre.synthea.export.flexporter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;

import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Goal;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Test;
import org.mitre.synthea.export.flexporter.FieldWrapper.DateFieldWrapper;
import org.mitre.synthea.export.flexporter.FieldWrapper.ReferenceFieldWrapper;


public class FieldWrapperTest {
  @Test
  public void testGetReference() {
    ReferenceFieldWrapper rfw = new ReferenceFieldWrapper(Observation.class, "subject");

    Observation obs = new Observation();
    obs.setSubject(new Reference().setReference("Patient/1"));

    assertEquals("Patient/1", rfw.getReference(obs));
  }

  @Test
  public void testGetNestedField() {
    DateFieldWrapper dfw = new DateFieldWrapper(Goal.class, "target.due");

    Goal goal = new Goal();
    goal.addTarget().setDue(new DateType("2023-08-01"));

    IBase result = dfw.getSingle(goal);

    assertTrue(result instanceof DateType);
    assertEquals("2023-08-01", ((DateType)result).getValueAsString());

    LocalDateTime jan12023 = LocalDateTime.of(2023, 1, 1, 0, 0);
    LocalDateTime dec312023 = LocalDateTime.of(2023, 12, 31, 0, 0);
    LocalDateTime jan11999 = LocalDateTime.of(1999, 1, 1, 0, 0);
    LocalDateTime dec312021 = LocalDateTime.of(2021, 12, 31, 0, 0);

    assertTrue(dfw.valueInRange(goal, jan12023, dec312023));
    assertFalse(dfw.valueInRange(goal, jan11999, dec312021));
  }


  @Test
  public void testGetNestedFieldChoiceType() {
    DateFieldWrapper dfw = new DateFieldWrapper(Procedure.class, "performed");

    Procedure p = new Procedure();
    p.setPerformed(new DateTimeType("2023-08-11T13:51:00"));

    IBase result = dfw.getSingle(p);
    assertTrue(result instanceof DateTimeType);
    assertEquals("2023-08-11T13:51:00", ((DateTimeType)result).getValueAsString());
  }

  @Test
  public void testSetNestedFieldChoiceType() {
    DateFieldWrapper dfw = new DateFieldWrapper(Procedure.class, "performed");

    Procedure p = new Procedure();
    dfw.set(p, new DateTimeType("2002-08-11T13:51:00"));

    IBase result = dfw.getSingle(p);
    assertTrue(result instanceof DateTimeType);
    assertEquals("2002-08-11T13:51:00", ((DateTimeType)result).getValueAsString());
  }
}
