package org.mitre.synthea.world.concepts;

import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Claim {

  private Entry mainEntry;
  // The Entries have the actual cost, so the claim has the amount that the payer covered.
  // Used as amount allowed.
  private double coveredCost;
  private double uncoveredCost;

  // Claim cost calculations object that factors in deductible
  public ClaimCosts claimCosts;
  public Payer payer;
  public Person person;
  public String claimId;
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
    this.claimId = UUID.randomUUID().toString();
    // Set the Payer.
    this.payer = this.person.getPayerAtTime(entry.start);
    if (this.payer == null) {
      // This can rarely occur when an death certification encounter
      // occurs on the birthday or immediately afterwards before a new
      // insurance plan is selected.
      this.payer = this.person.getPreviousPayerAtTime(entry.start);
    }
    if (this.payer == null) {
      this.payer = Payer.noInsurance;
    }
    this.items = new ArrayList<Entry>();
    this.claimCosts = new ClaimCosts();
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
    this.claimCosts.setOverallCost(totalCost);

    // Determine who covers the care and assign the costs accordingly.
    if (this.payer.coversCare(mainEntry)) {
      // Person's Payer covers their care.

      // Calculates costs for payer and patient with deductible
      // and returns total cost to patient.
      costToPatient = claimCosts.determineClaimCosts(this);
      costToPayer = totalCost - costToPatient;
      this.payerCoversEntry(mainEntry);
    }  else {
      // Payer will not cover the care.
      // Calculates costs for payer and patient with deductible
      // and returns total cost to patient.  With no insurance,
      // all costs assigned to patient.  Coinsurance is 100%
      // patient liability.
      this.payerDoesNotCoverEntry(mainEntry);
      // assigns cost to patient and returns that cost
      costToPatient = claimCosts.determineClaimCosts(this);
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
    this.uncoveredCost = costToPatient;
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

  public Entry getMainEntry() {
    return mainEntry;
  }

  /**
   * Returns the total cost of the Claim, including immunizations/procedures tied to the encounter.
   */
  public double getTotalClaimCost() {
    double totalCost = 0.0;
    totalCost += this.getLineItemCosts();
    totalCost += mainEntry.getCost().doubleValue();
    return totalCost;
  }

  /**
   * Returns the total cost that the Payer covered for this claim.
   */
  public double getCoveredCost() {
    return this.coveredCost;
  }

  public double getUncoveredCost() {
    return uncoveredCost;
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

  public ClaimCosts getClaimCosts() {
    return claimCosts;
  }
}