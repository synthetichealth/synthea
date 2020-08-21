package org.mitre.synthea.world.concepts;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;

public class Claim implements Serializable {

  private Entry mainEntry;
  // The Entries have the actual cost, so the claim has the amount that the payer covered.
  private double totalCost;
  private double payerCost;
  private double patientDeductible;
  private double patientCopay;
  private double patientCoinsurance;
  private double patientCost;
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

    totalCost = this.getTotalClaimCost();
    patientDeductible = payer.getDeductible();
    patientCopay = payer.determineCopay(mainEntry);
    patientCoinsurance = payer.getCoinsurance();
    patientCost = 0.0;
    payerCost = 0.0;

    // Determine who covers the care and assign the costs accordingly.
    if (this.payer.coversCare(mainEntry)) {
      // Person's Payer covers their care.
      if (totalCost > (patientDeductible + patientCopay)) {
        patientCost = (patientDeductible + patientCopay);
        patientCost += (totalCost * patientCoinsurance);
        if (patientCost > totalCost) {
          patientCost = totalCost;
          payerCost = 0;
        } else {
          payerCost = totalCost - patientCost;
        }
      } else {
        patientCost = totalCost;
      }
      this.payerCoversEntry(mainEntry);
    } else {
      // Payer will not cover the care.
      this.payerDoesNotCoverEntry(mainEntry);
      patientCost = totalCost;
    }

    // Update Person's Expenses and Coverage.
    this.person.addExpense(patientCost, mainEntry.start);
    this.person.addCoverage(payerCost, mainEntry.start);
    // Update Payer's Covered and Uncovered Costs.
    this.payer.addCoveredCost(payerCost);
    this.payer.addUncoveredCost(patientCost);
    // Update the Provider's Revenue if this is an encounter.
    if (mainEntry instanceof Encounter) {
      Encounter e = (Encounter) mainEntry;
      e.provider.addRevenue(totalCost);
    }
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
    return this.payerCost;
  }

  public double getDeductiblePaid() {
    return this.patientDeductible;
  }

  public double getCopayPaid() {
    return this.patientCopay;
  }

  public double getCoinsurancePaid() {
    return this.patientCoinsurance;
  }

  /**
   * Returns the total cost to the patient, including copay, coinsurance, and deductible.
   */
  public double getPatientCost() {
    return this.patientCost;
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