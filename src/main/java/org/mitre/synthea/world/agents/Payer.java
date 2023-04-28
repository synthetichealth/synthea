package org.mitre.synthea.world.agents;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.gson.internal.LinkedTreeMap;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.mitre.synthea.export.JSONSkip;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.behaviors.payeradjustment.IPayerAdjustment;
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.Claim.ClaimEntry;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Immunization;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;
import org.mitre.synthea.world.concepts.healthinsurance.InsurancePlan;

public class Payer implements Serializable {

  // Payer Adjustment strategy.
  @JSONSkip
  private IPayerAdjustment payerAdjustment;

  /* Payer Attributes. */
  private final Map<String, Object> attributes;
  private final String name;
  public final String uuid;
  private final List<InsurancePlan> plans;
  private final String ownership;
  private final int planLinkId;

  // The States that this payer covers & operates in.
  private final Set<String> statesCovered;

  /* Payer Statistics. */
  private BigDecimal revenue;
  private BigDecimal costsCovered;
  private BigDecimal costsUncovered;
  private double totalQOLS; // Total customer quality of life scores.
  // Unique utilizers of Payer, by Person ID, with number of utilizations per Person.
  private final Map<String, AtomicInteger> customerUtilization;
  // row: year, column: type, value: count.
  private transient Table<Integer, String, AtomicInteger> entryUtilization;

  /**
   * Simple bean used to add Java Serialization support to
   * com.google.common.collect.Table&lt;Integer, String, AtomicInteger&gt; which doesn't natively
   * support Serialization.
   */
  static class UtilizationBean implements Serializable {
    public Integer year;
    public String type;
    public AtomicInteger count;

    public UtilizationBean(Integer year, String type, AtomicInteger count) {
      this.year = year;
      this.type = type;
      this.count = count;
    }
  }

  /**
   * Java Serialization support for the entryUtilization field.
   * @param oos stream to write to
   */
  private void writeObject(ObjectOutputStream oos) throws IOException {
    oos.defaultWriteObject();
    ArrayList<UtilizationBean> entryUtilizationElements = null;
    if (entryUtilization != null) {
      entryUtilizationElements = new ArrayList<>(entryUtilization.size());
      for (Table.Cell<Integer, String, AtomicInteger> cell: entryUtilization.cellSet()) {
        entryUtilizationElements.add(
                new UtilizationBean(cell.getRowKey(), cell.getColumnKey(), cell.getValue()));
      }
    }
    oos.writeObject(entryUtilizationElements);
  }

  /**
   * Java Serialization support for the entryUtilization field.
   * @param ois stream to read from
   */
  private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
    ois.defaultReadObject();
    ArrayList<UtilizationBean> entryUtilizationElements =
            (ArrayList<UtilizationBean>)ois.readObject();
    if (entryUtilizationElements != null) {
      this.entryUtilization = HashBasedTable.create();
      for (UtilizationBean u: entryUtilizationElements) {
        this.entryUtilization.put(u.year, u.type, u.count);
      }
    }
  }

  /**
   * Payer constructor.
   * @param name  The name of the payer.
   * @param id  The payer ID.
   * @param statesCovered The list of states covered.
   * @param ownership The type of ownership (private/government).
   */
  public Payer(String name, int id, Set<String> statesCovered, String ownership) {
    if (name == null || name.isEmpty()) {
      throw new RuntimeException("ERROR: Payer must have a non-null name. Payer ID: " + id + ".");
    }
    // Initialize attributes.
    this.name = name;
    this.planLinkId = id;
    this.uuid = UUID.nameUUIDFromBytes((id + this.name).getBytes()).toString();
    this.statesCovered = statesCovered;
    this.plans = new ArrayList<InsurancePlan>();
    this.ownership = ownership;
    this.attributes = new LinkedTreeMap<>();

    // Initial tracking values.
    this.entryUtilization = HashBasedTable.create();
    this.customerUtilization = new HashMap<String, AtomicInteger>();
    this.costsCovered = Claim.ZERO_CENTS;
    this.costsUncovered = Claim.ZERO_CENTS;
    this.revenue = Claim.ZERO_CENTS;
    this.totalQOLS = 0.0;
  }

  public void addPlan(InsurancePlan newPlan) {
    this.plans.add(newPlan);
  }

  /**
   * Returns the payer's unique ID.
   */
  public String getResourceID() {
    return uuid;
  }

  /**
   * Returns the name of the payer.
   * @return the name.
   */
  public String getName() {
    return this.name;
  }

  /**
   * Returns the ownership type of the payer (Government/Private).
   * @return the ownership type.
   */
  public String getOwnership() {
    return this.ownership;
  }

  /**
   * Returns the Map of the payer's second class attributes.
   * @return any second-class attributes.
   */
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  /**
   * Returns the set of plans offered by this payer.
   * @return the set of plans.
   */
  public List<InsurancePlan> getPlans() {
    return this.plans;
  }

  /**
   * Returns whether or not this payer will cover the given entry.
   *
   * @param entry the entry that needs covering.
   * @return whether this payer covers the care of the entry.
   */
  public boolean coversCare(Entry entry) {
    return this.plans.iterator().next().coversService(entry);
  }

  /**
   * Determines whether or not this payer will adjust this claim, and by how
   * much. This determination is based on the claim adjustment strategy configuration,
   * which defaults to none.
   * @param claimEntry The claim entry to be adjusted.
   * @param person The person making the claim.
   * @return The dollar amount the claim entry was adjusted.
   */
  public BigDecimal adjustClaim(ClaimEntry claimEntry, Person person) {
    return payerAdjustment.adjustClaim(claimEntry, person);
  }

  /**
   * Increments the number of unique users.
   *
   * @param personId the person id who utilized the payer.
   */
  public synchronized void incrementCustomers(String personId) {
    if (!customerUtilization.containsKey(personId)) {
      customerUtilization.put(personId, new AtomicInteger(0));
    }
    customerUtilization.get(personId).incrementAndGet();
  }

  /**
   * Increments the entries covered by this payer.
   *
   * @param entry the entry covered.
   */
  public void incrementCoveredEntries(Entry entry) {

    String entryType = getEntryType(entry);

    incrementEntries(Utilities.getYear(entry.start), "covered-" + entryType);
    incrementEntries(Utilities.getYear(entry.start), "covered-" + entryType + "-" + entry.type);
  }

  /**
   * Increments the entries not covered by this payer.
   *
   * @param entry the entry covered.
   */
  public void incrementUncoveredEntries(Entry entry) {

    String entryType = getEntryType(entry);

    incrementEntries(Utilities.getYear(entry.start), "uncovered-" + entryType);
    incrementEntries(Utilities.getYear(entry.start), "uncovered-" + entryType
        + "-" + entry.type);
  }

  // Perhaps move to HealthRecord.java
  /**
   * Determines what entry type (Immunization/Encounter/Procedure/Medication) of the given entry.
   *
   * @param entry the entry to parse.
   */
  private String getEntryType(Entry entry) {

    String entryType;

    if (entry instanceof Encounter) {
      entryType = HealthRecord.ENCOUNTERS;
    } else if (entry instanceof Medication) {
      entryType = HealthRecord.MEDICATIONS;
    } else if (entry instanceof Procedure) {
      entryType = HealthRecord.PROCEDURES;
    } else if (entry instanceof Immunization) {
      entryType = HealthRecord.IMMUNIZATIONS;
    } else {
      // Not an entry with a cost.
      entryType = "no_cost";
    }
    return entryType;
  }

  /**
   * Increments encounter utilization for a given year and encounter type.
   *
   * @param year the year of the encounter to add
   * @param key the key (the encounter type and whether it was covered/uncovered)
   */
  private synchronized void incrementEntries(Integer year, String key) {
    if (!entryUtilization.contains(year, key)) {
      entryUtilization.put(year, key, new AtomicInteger(0));
    }
    entryUtilization.get(year, key).incrementAndGet();
  }

  /**
   * Increases the total costs incurred by the payer by the given amount.
   *
   * @param costToPayer the cost of the current encounter, after the patient's copay.
   */
  public void addCoveredCost(BigDecimal costToPayer) {
    this.costsCovered = this.costsCovered.add(costToPayer);
  }

  /**
   * Increases the costs the payer did not cover by the given amount.
   *
   * @param costToPatient the costs that the payer did not cover.
   */
  public void addUncoveredCost(BigDecimal costToPatient) {
    this.costsUncovered = this.costsUncovered.add(costToPatient);
  }

  /**
   * Adds the Quality of Life Score (QOLS) of a patient of the current (past?)
   * year. Increments the total number of years covered (for averaging out
   * purposes).
   *
   * @param qols the Quality of Life Score to be added.
   */
  public void addQols(double qols) {
    this.totalQOLS += qols;
  }

  /**
   * Returns the total amount of money received from patients.
   * Consists of monthly premium payments.
   * @return the total revenue.
   */
  public BigDecimal getRevenue() {
    return this.revenue;
  }

  /**
   * Returns the number of years the given customer was with this Payer.
   * @param personId  The person to check for.
   * @return  The number of years the person was with the payer.
   */
  public int getCustomerUtilization(String personId) {
    if (!customerUtilization.containsKey(personId)) {
      return 0;
    }
    return customerUtilization.get(personId).get();
  }

  /**
   * Returns the total number of unique customers of this payer.
   * @return the number of unique customers.
   */
  public int getUniqueCustomers() {
    return customerUtilization.size();
  }

  /**
   * Returns the total number of member years covered by this payer.
   * @return the number of years covered.
   */
  public int getNumYearsCovered() {
    return this.customerUtilization.values().stream().mapToInt(AtomicInteger::intValue).sum();
  }

  /**
   * Returns the number of encounters this payer paid for.
   * @return the number of covered encounters.
   */
  public int getEncountersCoveredCount() {
    return entryUtilization.column("covered-"
        + HealthRecord.ENCOUNTERS).values().stream().mapToInt(ai -> ai.get()).sum();
  }

  /**
   * Returns the number of encounters this payer did not cover for their customers.
   * @return the number of uncovered patient encounters.
   */
  public int getEncountersUncoveredCount() {
    return entryUtilization.column("uncovered-"
        + HealthRecord.ENCOUNTERS).values().stream().mapToInt(ai -> ai.get()).sum();
  }

  /**
   * Returns the number of medications this payer paid for.
   * @return the number of covered medications.
   */
  public int getMedicationsCoveredCount() {
    return entryUtilization.column("covered-"
        + HealthRecord.MEDICATIONS).values().stream().mapToInt(ai -> ai.get()).sum();
  }

  /**
   * Returns the number of medications this payer did not cover for their customers.
   * @return the number of uncovered patient medications.
   */
  public int getMedicationsUncoveredCount() {
    return entryUtilization.column("uncovered-"
        + HealthRecord.MEDICATIONS).values().stream().mapToInt(ai -> ai.get()).sum();
  }

  /**
   * Returns the number of procedures this payer paid for.
   * @return the number of covered procedures.
   */
  public int getProceduresCoveredCount() {
    return entryUtilization.column("covered-"
        + HealthRecord.PROCEDURES).values().stream().mapToInt(ai -> ai.get()).sum();
  }

  /**
   * Returns the number of procedures this payer did not cover for their customers.
   * @return the number of uncovered patient procedures.
   */
  public int getProceduresUncoveredCount() {
    return entryUtilization.column("uncovered-"
        + HealthRecord.PROCEDURES).values().stream().mapToInt(ai -> ai.get()).sum();
  }

  /**
   * Returns the number of immunizations this payer paid for.
   * @return the number of covered immunizations.
   */
  public int getImmunizationsCoveredCount() {
    return entryUtilization.column("covered-"
        + HealthRecord.IMMUNIZATIONS).values().stream().mapToInt(ai -> ai.get()).sum();
  }

  /**
   * Returns the number of immunizations this payer did not cover for their customers.
   * @return the number of uncovered patient immunizations.
   */
  public int getImmunizationsUncoveredCount() {
    return entryUtilization.column("uncovered-"
        + HealthRecord.IMMUNIZATIONS).values().stream().mapToInt(ai -> ai.get()).sum();
  }

  /**
   * Returns the amount of money the payer paid for healthcare.
   * @return the total value of coverage paid.
   */
  public BigDecimal getAmountCovered() {
    return this.costsCovered;
  }

  /**
   * Returns the amount of money the payer did not cover.'
   * @return the total value of uncovered patient healthcare.
   */
  public BigDecimal getAmountUncovered() {
    return this.costsUncovered;
  }

  /**
   * Returns the average of the payer's QOLS of customers over the number of years covered.
   * @return the average QOLS of the payer's patients.
   */
  public double getQolsAverage() {
    int numYears = this.getNumYearsCovered();
    return this.totalQOLS / numYears;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 53 * hash + Objects.hashCode(this.attributes);
    hash = 53 * hash + Objects.hashCode(this.name);
    hash = 53 * hash + Objects.hashCode(this.uuid);
    hash = 53 * hash + Objects.hashCode(this.ownership);
    hash = 53 * hash + Objects.hashCode(this.statesCovered);
    hash = 53 * hash + this.revenue.hashCode();
    hash = 53 * hash + this.costsCovered.hashCode();
    hash = 53 * hash + this.costsUncovered.hashCode();
    hash = 53 * hash + (int) (Double.doubleToLongBits(this.totalQOLS)
            ^ (Double.doubleToLongBits(this.totalQOLS) >>> 32));
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final Payer other = (Payer) obj;
    if (!this.revenue.equals(other.revenue)) {
      return false;
    }
    if (!this.costsCovered.equals(other.costsCovered)) {
      return false;
    }
    if (!this.costsUncovered.equals(other.costsUncovered)) {
      return false;
    }
    if (Double.doubleToLongBits(this.totalQOLS)
            != Double.doubleToLongBits(other.totalQOLS)) {
      return false;
    }
    if (!Objects.equals(this.plans, other.plans)) {
      return false;
    }
    if (!Objects.equals(this.name, other.name)) {
      return false;
    }
    if (!Objects.equals(this.uuid, other.uuid)) {
      return false;
    }
    if (!Objects.equals(this.ownership, other.ownership)) {
      return false;
    }
    if (!Objects.equals(this.attributes, other.attributes)) {
      return false;
    }
    if (!Objects.equals(this.statesCovered, other.statesCovered)) {
      return false;
    }
    return true;
  }

  /**
   * Sets the payer claim adjustment strategy.
   * @param payerAdjustment The payer adjustment algorithm.
   */
  public void setPayerAdjustment(IPayerAdjustment payerAdjustment) {
    this.payerAdjustment = payerAdjustment;
  }

  public boolean isGovernmentPayer() {
    return this.ownership.equals(PayerManager.GOV_OWNERSHIP);
  }

  /**
   * Adds the given revenue to the payer.
   * @param additionalRevenue The revenue to add.
   */
  public void addRevenue(BigDecimal additionalRevenue) {
    this.revenue = this.revenue.add(additionalRevenue);
  }

  /**
   * Returns the government payer plan if this is a government payer.
   * @return  This payer's government payer plan.
   */
  public InsurancePlan getGovernmentPayerPlan() {
    if (!this.ownership.equals(PayerManager.GOV_OWNERSHIP)) {
      throw new RuntimeException("Only government payers can call getGovernmentPayerPlan().");
    }
    return this.plans.iterator().next();
  }

  /**
   * Add additional attributes to Payer.
   */
  public void addAttribute(String key, String value) {
    this.attributes.put(key, value);
  }

  /**
   * Returns whether this is the no insurance payer.
   * @return  Returns whether this is no insurance.
   */
  public boolean isNoInsurance() {
    return this.name.equals(PayerManager.NO_INSURANCE);
  }

  /**
   * Returns the plan link id for this payer.
   * @return  The plan link id.
   */
  public int getPlanLinkId() {
    return this.planLinkId;
  }

}