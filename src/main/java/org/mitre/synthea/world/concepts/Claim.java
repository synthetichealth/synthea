package org.mitre.synthea.world.concepts;

import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;

public class Claim {

  private Encounter encounter;
  private Medication medication;
  // The Entries have the actual cost, so the claim has the amount that the payer covered.
  private double coveredCost;
  public Payer payer;
  public Person person;
  public List<Entry> items;

  /**
   * Constructor of a health insurance Claim for an encounter.
   */
  public Claim(Encounter encounter, Person person) {
    this((Entry) encounter, person);
    this.encounter = encounter;
  }

  /**
   * Constructor of a health insurance Claim for a medication.
   */
  public Claim(Medication medication, Person person) {
    this((Entry) medication, person);
    this.medication = medication;
  }

  /**
   * Constructor of a Claim for an Entry.
   */
  private Claim(Entry entry, Person person) {
    // Set the Person.
    this.person = person;
    // Set the Payer.
    if (this.person.getPayerAtTime(entry.start) == null) {
      // Person hasn't checked to get insurance at this age yet. Will check now.
      Module.processHealthInsuranceModule(this.person, entry.start);
    }
    this.payer = this.person.getPayerAtTime(entry.start);
    this.items = new ArrayList<Entry>();
  }

  /**
   * Default constructor to create a blank claim that will be unused because
   * health insurance is turned off.
   */
  public Claim() {
    this.items = new ArrayList<Entry>();
  }

  /**
   * Adds non-explicit costs to the Claim. (Procedures/Immunizations/etc).
   */
  public void addLineItem(Entry entry) {
    this.items.add(entry);
  }

  /**
   * Assigns the costs of the claim to the patient and payer.
   */
  public void assignCosts() {

    // Casts either the encounter or medication of the claim depending on which is not null.
    Entry entry;
    Provider provider = null;

    if (this.encounter != null) {
      entry = (Entry) encounter;
      provider = encounter.provider;
    } else if (this.medication != null) {
      entry = (Entry) medication;
    } else {
      // Claims can only be made for encounters and medications.
      return;
    }

    // The total cost of this claim.
    double totalCost = 0.0;
    totalCost += entry.getCost().doubleValue();
    totalCost += this.getLineItemCosts();

    double patientCopay = payer.determineCopay(entry);
    double costToPatient = 0.0;
    double costToPayer = 0.0;

    // Determine who covers the care and assign the costs accordingly.
    if (this.payer.coversCare(entry)) {
      // Person's Payer covers their care.
      costToPatient = totalCost > patientCopay ? patientCopay : totalCost;
      costToPayer = totalCost > patientCopay ? totalCost - patientCopay : 0.0;
      this.payerCoversEntry(entry);
    }  else {
      // Payer will not cover the care.
      this.payerDoesNotCoverEntry(entry);
      costToPatient = totalCost;
      if (person.canAffordCare(entry)) {
        // Update the person's costs, they get the encounter.
      } else {
        // The person does not get the encounter. Lower their QOLS/GBD.
      }
    }

    // Update Person's Expenses and Coverage.
    this.person.addExpense(costToPatient, entry.start);
    this.person.addCoverage(costToPayer, entry.start);
    // Update Payer's Covered and Uncovered Costs.
    this.payer.addCoveredCost(costToPayer);
    this.payer.addUncoveredCost(costToPatient);
    // Update the Provider's Revenue if this is an encounter.
    if (encounter != null) {
      provider.addRevenue(totalCost);
    }
    // Update the Claim.
    this.coveredCost = costToPayer;
  }

  /**
   * Returns the additional costs from any immunzations/procedures tied to the encounter.
   */
  private double getLineItemCosts() {
    double additionalCosts = 0.0;
    // Sum line-item entry costs.
    for (Entry entry : this.items) {
      additionalCosts += entry.getCost().doubleValue();
    }
    return additionalCosts;
  }

  /**
   * Returns the total cost of the Claim, including immunizations/procedures tied to the encounter.
   */
  public double getTotalClaimCost() {
    double totalCost = 0.0;
    totalCost += this.getLineItemCosts();
    // Get the main encounter/medication cost.
    if (this.encounter != null) {
      totalCost += this.encounter.getCost().doubleValue();
    } else if (this.medication != null) {
      totalCost += this.medication.getCost().doubleValue();
    }
    return totalCost;
  }

  /**
   * Returns the total cost that the Payer covered for this claim.
   */
  public double getCoveredCost() {
    return this.coveredCost;
  }

  /**
   * Increments the covered entry utilization of the payer.
   */
  private void payerCoversEntry(Entry entry) {
    // Payer covers the entry.
    this.payer.incrementEntriesCovered(entry);
    // Payer covers the line items.
    for (Entry lineItemEntry : this.items) {
      this.payer.incrementEntriesCovered(lineItemEntry);
    }
  }

  /**
   * Increments the uncovered entry utilization of the payer.
   */
  private void payerDoesNotCoverEntry(Entry entry) {
    // Payer does not cover the entry.
    this.payer.incrementEntriesNotCovered(entry);
    // Payer does not cover the line items.
    for (Entry lineItemEntry : this.items) {
      this.payer.incrementEntriesNotCovered(lineItemEntry);
    }
    // Results in adding to NO_INSURANCE's costs uncovered, but not their utilization.
    this.payer = Payer.noInsurance;
  }
}