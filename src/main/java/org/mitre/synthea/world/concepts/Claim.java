package org.mitre.synthea.world.concepts;

import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Immunization;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;

public class Claim {

  private Encounter encounter;
  private Medication medication;
  // The Entries have the actual cost, so the claim has the amount that the payer covered.
  private double coveredCost;
  public List<Entry> items;
  public Payer payer;
  public Person person;

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
    // Initialize additional items as part of this entry.
    this.items = new ArrayList<>();
  }

  /**
   * Default constructor to create a blank claim that will be unused because
   * health insurance is turned off.
   */
  public Claim() {
    this.items = new ArrayList<>();
  }

  /**
   * Assigns the costs of the claim to the patient and payer.
   */
  public void assignCosts() {

    // Casts either the encounter or medication of the claim depending on which is not null.
    Entry entry;
    // The total cost of this claim.
    double totalCost = 0.0;

    Provider provider = null;
    if (this.encounter != null) {
      entry = (Entry) encounter;
      provider = encounter.provider;
      // Costs of any immunizations/procedures tied to this encounter.
      totalCost += this.getAdditionalCosts();
    } else if (this.medication != null) {
      entry = (Entry) medication;
    } else {
      // Claims can only be made for encounters and medications.
      return;
    }

    // Entry's initial cost.
    totalCost += entry.getCost().doubleValue();

    // Calculate the Patient's Copay.
    double patientCopay = 0.0;
    if (this.person != null) {
      patientCopay = payer.determineCopay(entry);
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
      // Person's Payer will not cover care, but the person will afford it.
      this.payerDoesNotCoverEntry(entry);
      costToPatient = totalCost;
    } else {
      // Payer won't cover the care and the person cannot afford it: Person does not recieve care.
      this.payerDoesNotCoverEntry(entry);
      costToPatient = totalCost;
      // QOLS/GBD will be affected here.
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
  private double getAdditionalCosts() {
    double additionalCosts = 0.0;
    // Sum Immunization costs.
    for (Immunization immunization : encounter.immunizations) {
      additionalCosts += immunization.getCost().doubleValue();
    }
    // Sum Procedure costs.
    for (Procedure procedure : encounter.procedures) {
      additionalCosts += procedure.getCost().doubleValue();
    }
    return additionalCosts;
  }

  /**
   * Adds a line item to the total resources used in the encounter. (Ex.
   * anesthesia, other non-explicit costs beyond encounter)
   */
  public void addItem(Entry entry) {
    items.add(entry);
  }

  /**
   * Returns the cost of the non-explicit line items.
   */
  public double getLineItemCosts() {
    double lineItemCosts = 0.0;

    for (Entry lineItem : items) {
      lineItemCosts += lineItem.getCost().doubleValue();
    }
    return lineItemCosts;
  }

  /**
   * Returns the total cost of the Claim, including immunizations/procedures tied to the encounter.
   */
  public double getTotalClaimCost() {
    double totalCost = 0.0;
    // Get the main encounter/medication cost.
    if (this.encounter != null) {
      totalCost += this.encounter.getCost().doubleValue();
      // Get any tied immunization/procedure costs.
      totalCost += this.getAdditionalCosts();
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

  /**
   * Increments the uncovered entry utilization of the payer.
   */
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
    // Results in adding to NO_INSURANCE's costs, but not their encounter/customer utilization.
    this.payer = Payer.noInsurance;
  }
}