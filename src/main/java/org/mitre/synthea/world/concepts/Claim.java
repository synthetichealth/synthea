package org.mitre.synthea.world.concepts;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Immunization;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


public class Claim {

  private Encounter encounter;
  private Medication medication;
  // The Entry has the actual cost, so the claim has the amount that the payer
  // covered.
  private double coveredCost;
  public List<Entry> items;
  public Payer payer;
  public Person person;

  /**
   * Constructor of a Claim for an encounter.
   */
  public Claim(Encounter encounter, Person person) {

    this((Entry) encounter, person);
    this.encounter = encounter;
  }

  /**
   * Constructor of a Claim for a medication.
   */
  public Claim(Medication medication, Person person) {

    this((Entry) medication, person);
    this.medication = medication;
  }

  /**
   * Constructor of a Claim for an Entry.
   */
  private Claim(Entry entry, Person person) {

    this.person = person;

    if (Boolean.parseBoolean(Config.get("generate.health_insurance", "false"))) {

      this.payer = this.person.getPayerAtTime(entry.start);
      if (this.payer == null) {
        // Person hasn't checked to get insurance at this age yet. Have to check now.
        Module.processHealthInsuranceModule(this.person, entry.start);
        this.payer = this.person.getPayerAtTime(entry.start);
      }
    } else {
      this.payer = Payer.noInsurance;
    }

    // Additional items as part of this entry.
    this.items = new ArrayList<>();
  }

  // Assign the costs of the entry to the Payer and Patient.
  public void assignCosts() {

    if(this.coveredCost > 0) {
      throw new RuntimeException("ASsssss");
    }

    Entry entry;

    if (this.encounter != null) {
      entry = (Entry) encounter;
    } else if (this.medication != null) {
      entry = (Entry) medication;
    } else {
      throw new RuntimeException("Invalid Claim Entry.");
    }

    // Provider provider = entry.record.provider; Later, for determining
    // isInNetwork().
    // Right now, total cost is based on the main entry's cost, ignoring the
    // lineItem costs.
    double totalCost = entry.getCost().doubleValue();

    // Calculate the Patient's Copay.
    double patientCopay = 0.0;
    if (this.person != null) {
      // Temporarily null input until encounter type copays are implemented.
      patientCopay = payer.determineCopay(null);
    }

    double costToPatient = 0.0;
    double costToPayer = 0.0;

    // Determine who covers the care and assign the costs accordingly.
    if (this.payer.coversCare(entry)) {
      // Person's Payer covers their care.
      if (totalCost >= patientCopay) {
        costToPayer = totalCost - patientCopay;
        costToPatient = patientCopay;
      } else {
        costToPatient = totalCost;
      }
      this.payerCoversEntry(entry);
    } else if (person.canAffordCare(entry)) {
      // Person's Payer will not cover care, but the person can afford it.
      this.payerDoesNotCoverEntry(entry);
      // Affect the person's costs
    } else {
      this.payerDoesNotCoverEntry(entry);
      // Here is where QOLS/GBD should/could/would be affected.
    }

    // Update Person's Costs.
    this.person.addCost(costToPatient);
    // Update Payer's Costs.
    this.payer.addCost(costToPayer);
    this.payer.addUncoveredCost(costToPatient);
    // Update the Claim
    this.coveredCost = costToPayer;
  }

  /**
   * Adds a line item to the total resources used in the encounter. (Ex.
   * anesthesia, other non-explicit costs beyond encounter)
   */
  public void addItem(Entry entry) {
    items.add(entry);
  }

  /**
   * Returns the total cost of the Claim, including line item non-explicit costs.
   */
  public BigDecimal total() {
    BigDecimal totalCoveredByLineItem = BigDecimal.valueOf(coveredCost);

    for (Entry lineItem : items) {
      totalCoveredByLineItem = totalCoveredByLineItem.add(lineItem.getCost());
    }
    return totalCoveredByLineItem;
  }

  /**
   * Returns the total cost that the Payer covered for this claim.
   */
  public double getCoveredCost() {
    return this.coveredCost;
  }

  private void payerCoversEntry(Entry entry) {
    this.payer.incrementEntriesCovered(entry);
    if (this.encounter != null) {
      for (Procedure procedure : this.encounter.procedures) {
        this.payer.incrementEntriesCovered(procedure);
      }
      for (Immunization immunization : this.encounter.immunizations) {
        this.payer.incrementEntriesCovered(immunization);
      }
    }
  }

  private void payerDoesNotCoverEntry(Entry entry) {
    // Person does not recive the entry.
    this.payer.incrementEntriesNotCovered(entry);
    if (this.encounter != null) {
      for (Procedure procedure : this.encounter.procedures) {
        this.payer.incrementEntriesNotCovered(procedure);
      }
      for (Immunization immunization : this.encounter.immunizations) {
        this.payer.incrementEntriesNotCovered(immunization);
      }
    }
    // TODO - This might cause some issues with noInsurance statistics.
    this.payer = Payer.noInsurance;
  }
}