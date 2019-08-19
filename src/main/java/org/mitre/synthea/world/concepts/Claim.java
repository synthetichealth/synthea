package org.mitre.synthea.world.concepts;

import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;

public class Claim {

  private Entry mainEntry;
  // The Entries have the actual cost, so the claim has the amount that the payer covered.
  private double coveredCost;
  public Payer payer;
  public Person person;
  public List<Entry> items;

  /**
   * Constructor of a Claim for an Entry.
   */
  public Claim(Entry entry, Person person) {
    // Set the Entry.
    if ((entry instanceof Encounter) || (entry instanceof Medication)) {
      this.mainEntry = entry;
    } else {
      throw new RuntimeException(
          "A Claim can only be made with entry types Encounter or Medication.");
    }
    // Set the Person.
    this.person = person;
    // Set the Payer.
    this.payer = this.person.getPayerAtTime(entry.start);
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

    double totalCost = this.getTotalClaimCost();
    double patientCopay = payer.determineCopay(mainEntry);
    double costToPatient = 0.0;
    double costToPayer = 0.0;

    // Determine who covers the care and assign the costs accordingly.
    if (this.payer.coversCare(mainEntry)) {
      // Person's Payer covers their care.
      costToPatient = totalCost > patientCopay ? patientCopay : totalCost;
      costToPayer = totalCost > patientCopay ? totalCost - patientCopay : 0.0;
      this.payerCoversEntry(mainEntry);
    }  else {
      // Payer will not cover the care.
      this.payerDoesNotCoverEntry(mainEntry);
      costToPatient = totalCost;
      if (person.canAffordCare(mainEntry)) {
        // Update the person's costs, they get the encounter.
      } else {
        // TODO The person does not get the encounter. Lower their QOLS/GBD.
      }
    }

    // Update Person's Expenses and Coverage.
    this.person.addExpense(costToPatient, mainEntry.start);
    this.person.addCoverage(costToPayer, mainEntry.start);
    // Update Payer's Covered and Uncovered Costs.
    this.payer.addCoveredCost(costToPayer);
    this.payer.addUncoveredCost(costToPatient);
    // Update the Provider's Revenue if this is an encounter.
    if (mainEntry instanceof Encounter) {
      ((Encounter) mainEntry).provider.addRevenue(totalCost);
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
    totalCost = mainEntry.getCost().doubleValue();
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
    this.payer.incrementCoveredEntries(entry);
    // Payer covers the line items.
    for (Entry lineItemEntry : this.items) {
      this.payer.incrementCoveredEntries(lineItemEntry);
    }
  }

  /**
   * Increments the uncovered entry utilization of the payer.
   */
  private void payerDoesNotCoverEntry(Entry entry) {
    // Payer does not cover the entry.
    this.payer.incrementUncoveredEntries(entry);
    // Payer does not cover the line items.
    for (Entry lineItemEntry : this.items) {
      this.payer.incrementUncoveredEntries(lineItemEntry);
    }
    // Results in adding to NO_INSURANCE's costs uncovered, but not their utilization.
    this.payer = Payer.noInsurance;
  }
}