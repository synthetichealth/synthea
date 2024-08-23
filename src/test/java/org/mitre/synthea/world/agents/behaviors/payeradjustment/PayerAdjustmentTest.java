package org.mitre.synthea.world.agents.behaviors.payeradjustment;

import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;

import org.junit.Test;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mockito.Mockito;

public class PayerAdjustmentTest {

  private static final Code code = new Code("system", "code", "display");

  @Test
  public void testPayerAdjustmentNone() {
    PayerManager.loadNoInsurance();
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.coverage.setPlanToNoInsurance(0L);
    Encounter encounter = person.record.encounterStart(0L, EncounterType.EMERGENCY);
    encounter.codes.add(code);
    Claim claim = new Claim(encounter, person);
    claim.assignCosts();

    IPayerAdjustment adjustment = new PayerAdjustmentNone();
    BigDecimal result = adjustment.adjustClaim(claim.mainEntry, person);
    assertTrue("Adjustment should be zero.", result.equals(Claim.ZERO_CENTS));
  }

  @Test
  public void testPayerAdjustmentFixed() {
    PayerManager.loadNoInsurance();
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.coverage.setPlanToNoInsurance(0L);
    Encounter encounter = person.record.encounterStart(0L, EncounterType.EMERGENCY);
    encounter.codes.add(code);
    Claim claim = new Claim(encounter, person);
    claim.assignCosts();

    IPayerAdjustment adjustment = new PayerAdjustmentFixed(0.5);
    BigDecimal result = adjustment.adjustClaim(claim.mainEntry, person);
    assertTrue("Adjustment should be non-zero", result.compareTo(claim.mainEntry.cost) < 0);

    adjustment = new PayerAdjustmentFixed(1.0);
    result = adjustment.adjustClaim(claim.mainEntry, person);
    assertTrue("Adjustment should be total", result.equals(claim.mainEntry.cost));

    adjustment = new PayerAdjustmentFixed(0.0);
    result = adjustment.adjustClaim(claim.mainEntry, person);
    assertTrue("Adjustment should be zero", result.equals(Claim.ZERO_CENTS));
  }

  @Test
  public void testPayerAdjustmentRandom() {
    PayerManager.loadNoInsurance();
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.coverage.setPlanToNoInsurance(0L);
    Encounter encounter = person.record.encounterStart(0L, EncounterType.EMERGENCY);
    encounter.codes.add(code);
    Claim claim = new Claim(encounter, person);
    claim.assignCosts();

    Person shadow = Mockito.mock(Person.class);
    Mockito.when(shadow.rand(0.0, 0.5)).thenReturn(0.5);
    Mockito.when(shadow.randBoolean()).thenReturn(true);

    IPayerAdjustment adjustment = new PayerAdjustmentRandom(0.5);
    BigDecimal result = adjustment.adjustClaim(claim.mainEntry, shadow);
    assertTrue("Adjustment should be non-zero", result.compareTo(claim.mainEntry.cost) < 0);

    Mockito.when(shadow.randBoolean()).thenReturn(false);
    result = adjustment.adjustClaim(claim.mainEntry, shadow);
    assertTrue("Adjustment should be zero", result.equals(Claim.ZERO_CENTS));
  }

  @Test
  public void testRateBounds() {
    // Below zero
    PayerAdjustmentFixed adjustment = new PayerAdjustmentFixed(-1);
    assertTrue("Below zero rate should be zero.", (adjustment.rate == 0));

    // Above one
    adjustment = new PayerAdjustmentFixed(2);
    assertTrue("Rates above one should be one.", (adjustment.rate == 1));

    // Below zero
    PayerAdjustmentRandom randomAdjustment = new PayerAdjustmentRandom(-1);
    assertTrue("Below zero rate should be zero.", (randomAdjustment.rate == 0));

    // Above one
    randomAdjustment = new PayerAdjustmentRandom(2);
    assertTrue("Rates above one should be one.", (randomAdjustment.rate == 1));
  }
}
