package org.mitre.synthea.export.rif.enrollment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.mitre.synthea.export.rif.identifiers.FixedLengthIdentifier;
import org.mitre.synthea.export.rif.identifiers.PlanBenefitPackageID;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

/**
 * Utility class to manage a beneficiary's contract history.
 */
public abstract class ContractHistory<T extends FixedLengthIdentifier> {

  private List<ContractPeriod> contractPeriods;
  private static final PlanBenefitPackageID[] planBenefitPackageIDs = initPlanBenefitPackageIDs();

  /**
   * Create a new random contract history.
   * @param person source of randomness
   * @param stopTime when the history should end (as ms since epoch)
   * @param yearsOfHistory how many years should be covered
   * @param percentChangeOpenEnrollment percent chance contract will change at open enrollment
   * @param percentChangeMidYear percent chance contract will change mid year
   */
  public ContractHistory(Person person, long stopTime, int yearsOfHistory,
          int percentChangeOpenEnrollment, int percentChangeMidYear) {
    int endYear = Utilities.getYear(stopTime);
    int endMonth = 12;
    contractPeriods = new ArrayList<>();
    ContractPeriod currentContractPeriod = new ContractPeriod(endYear - yearsOfHistory, person);
    for (int year = endYear - yearsOfHistory; year <= endYear; year++) {
      if (year == endYear) {
        endMonth = Utilities.getMonth(stopTime);
      }
      for (int month = 1; month <= endMonth; month++) {
        if ((month == 1 && person.randInt(100) < percentChangeOpenEnrollment)
                || person.randInt(100) < percentChangeMidYear) {
          ContractPeriod newContractPeriod = new ContractPeriod(year, month, person);
          T currentContractID = currentContractPeriod.getContractID();
          T newContractID = newContractPeriod.getContractID();
          if ((currentContractID != null && !currentContractID.equals(newContractID))
                  || currentContractID != newContractID) {
            currentContractPeriod.setEndBefore(newContractPeriod);
            contractPeriods.add(currentContractPeriod);
            currentContractPeriod = newContractPeriod;
          }
        }
      }
    }
    currentContractPeriod.setEnd(stopTime);
    contractPeriods.add(currentContractPeriod);
  }

  /**
   * Get the contract ID for the specified point in time.
   * @param timeStamp the point in time
   * @return the contract ID or null if not enrolled at the specified point in time
   */
  public T getContractID(long timeStamp) {
    for (ContractPeriod contractPeriod : contractPeriods) {
      if (contractPeriod.covers(timeStamp)) {
        return contractPeriod.getContractID();
      }
    }
    return null;
  }

  /**
   * Get a list of contract periods that were active during the specified year.
   * @param year the year
   * @return the list
   */
  public List<ContractPeriod> getContractPeriods(int year) {
    List<ContractPeriod> periods = new ArrayList<>();
    for (ContractPeriod period : contractPeriods) {
      if (period.coversYear(year)) {
        periods.add(period);
      }
    }
    return Collections.unmodifiableList(periods);
  }

  /**
   * Get a count of months that were covered by Part D in the specified year.
   * @param year the year
   * @return the count
   */
  public int getCoveredMonthsCount(int year) {
    int count = 0;
    for (ContractPeriod period : getContractPeriods(year)) {
      if (period.getContractID() != null) {
        count += period.getCoveredMonths(year).size();
      }
    }
    return count;
  }

  /**
   * Get a random contract ID or null if bene is not enrolled. Implementations of this method
   * should use rand to model the likelihood of a bene being enrolled.
   * @param rand source of randomness
   * @return a contract ID or null
   */
  protected abstract T getRandomContractID(RandomNumberGenerator rand);

  /**
   * Get a random plan benefit package ID or null if the supplied contract ID is null.
   * @param rand a source of randomness
   * @param contractID the contract ID
   * @return a random plan benefit package ID
   */
  protected PlanBenefitPackageID getRandomPlanBenefitPackageID(RandomNumberGenerator rand,
          T contractID) {
    if (contractID == null) {
      return null; // no benefit package if not on contract
    } else {
      return planBenefitPackageIDs[rand.randInt(planBenefitPackageIDs.length)];
    }
  }

  /**
   * Initialize an array containing all of the configured plan benefit package IDs.
   * @return the package IDs
   */
  private static PlanBenefitPackageID[] initPlanBenefitPackageIDs() {
    int numPackages = Config.getAsInteger("exporter.bfd.plan_benefit_package_count", 5);
    PlanBenefitPackageID[] packageIDs = new PlanBenefitPackageID[numPackages];
    PlanBenefitPackageID packageID = PlanBenefitPackageID.parse(Config.get(
            "exporter.bfd.plan_benefit_package_start", "800"));
    for (int i = 0; i < numPackages; i++) {
      packageIDs[i] = packageID;
      packageID = packageID.next();
    }
    return packageIDs;
  }

  /**
   * Utility class that represents a period of time and an associated contract id.
   */
  public class ContractPeriod {

    private LocalDate startDate;
    private LocalDate endDate;
    private T contractID;
    private PlanBenefitPackageID planBenefitPackageID;

    /**
     * Create a new contract period. Contract periods have a one month granularity so the
     * supplied start and end are adjusted to the first day of the start month and last day of
     * the end month.
     * @param start the start of the contract period
     * @param end the end of the contract period
     * @param contractID the contract id
     * @param planBenefitPackageID the plan benefit package id
     */
    public ContractPeriod(LocalDate start, LocalDate end, T contractID,
            PlanBenefitPackageID planBenefitPackageID) {
      if (start != null) {
        this.startDate = LocalDate.of(start.getYear(), start.getMonthValue(), 1);
      }
      if (end != null) {
        this.endDate = LocalDate.of(end.getYear(), end.getMonthValue(), 1)
                .plusMonths(1).minusDays(1);
      }
      this.contractID = contractID;
      this.planBenefitPackageID = planBenefitPackageID;
    }

    /**
     * Create a new contract period. Contract periods have a one month granularity so the
     * supplied start and end are adjusted to the first day of the start month and last day of
     * the end month. A random plan benefit package ID is chosen
     * @param start the start of the contract period
     * @param end the end of the contract period
     * @param contractID the contract id
     * @param rand source of randomness
     */
    public ContractPeriod(LocalDate start, LocalDate end, T contractID,
            RandomNumberGenerator rand) {
      this(start, end, contractID, getRandomPlanBenefitPackageID(rand, contractID));
    }

    /**
     * Create a new contract period starting on the first days of the specified month. A contract
     * id is randomly assigned.
     * @param year the year
     * @param month the month
     * @param rand source of randomness
     */
    public ContractPeriod(int year, int month, RandomNumberGenerator rand) {
      this(LocalDate.of(year, month, 1), null, getRandomContractID(rand), rand);
    }

    /**
     * Create a new contract period starting on the first days of the specified year. A contract
     * id is randomly assigned.
     * @param year the year
     * @param rand source of randomness
     */
    public ContractPeriod(int year, RandomNumberGenerator rand) {
      this(year, 1, rand);
    }

    /**
     * Get the start of the period as epoch millis.
     * @return the start of the period
     */
    public long getStart() {
      return startDate.toEpochSecond(LocalTime.MIDNIGHT, ZoneOffset.UTC) * 1000;
    }

    /**
     * Get the end of the period as epoch millis.
     * @return the start of the period
     */
    public long getEnd() {
      return (endDate.plusDays(1).toEpochSecond(LocalTime.MIDNIGHT, ZoneOffset.UTC) * 1000) - 1;
    }

    /**
     * Get the contract id.
     * @return the contract id or null if not enrolled during this period
     */
    public T getContractID() {
      return contractID;
    }

    public PlanBenefitPackageID getPlanBenefitPackageID() {
      return planBenefitPackageID;
    }

    /**
     * Get a list of years covered by this period.
     * @return list of years
     * @throws IllegalStateException if the period has a null start and end
     */
    public List<Integer> getCoveredYears() {
      if (startDate == null || endDate == null) {
        throw new IllegalStateException(
                "Contract period is unbounded (either start or end is null)");
      }
      ArrayList<Integer> years = new ArrayList<>();
      for (int year = startDate.getYear(); year <= endDate.getYear(); year++) {
        years.add(year);
      }
      return years;
    }

    /**
     * Get the list of months that are covered by this contract period in the specified year.
     * @param year the year
     * @return the list
     */
    public List<Integer> getCoveredMonths(int year) {
      ArrayList<Integer> months = new ArrayList<>();
      if (year < startDate.getYear() || year > endDate.getYear()) {
        return months;
      }
      int startMonth = 1;
      int endMonth = 12;
      if (year == startDate.getYear()) {
        startMonth = startDate.getMonthValue();
      }
      if (year == endDate.getYear()) {
        endMonth = endDate.getMonthValue();
      }
      for (int i = startMonth; i <= endMonth; i++) {
        months.add(i);
      }
      return months;
    }

    /**
     * Set the end of this period to occur the month before the start of the specified period.
     * @param newContractPeriod the period to end one before
     * @throws IllegalStateException if the supplied period has a null start
     */
    public void setEndBefore(ContractPeriod newContractPeriod) {
      if (newContractPeriod.startDate == null) {
        throw new IllegalStateException("Contract period has an unbounded start (start is null)");
      }
      this.endDate = newContractPeriod.startDate.minusDays(1);
    }

    /**
     * Set the end of this period to the supplied point in time (in ms since the epoch).
     * @param stopTime the point in time
     */
    public void setEnd(long stopTime) {
      endDate = Instant.ofEpochMilli(stopTime).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /**
     * Check whether this period includes the specified point in time. Unbounded periods are
     * assumed to cover all times before, after or both.
     * @param timeStamp point in time
     * @return true of the period covers the point in time, false otherwise
     */
    public boolean covers(long timeStamp) {
      LocalDate date = Instant.ofEpochMilli(timeStamp).atZone(ZoneId.systemDefault()).toLocalDate();
      return (startDate == null || startDate.isBefore(date) || startDate.isEqual(date))
              && (endDate == null || endDate.isAfter(date) || endDate.isEqual(date));
    }

    /**
     * Check if this period has any overlap with the specified year.
     * @param year the year
     * @return true if the period overlap with any point in the year, false otherwise.
     */
    public boolean coversYear(int year) {
      return (startDate == null || startDate.getYear() <= year)
              && (endDate == null || endDate.getYear() >= year);
    }
  }

}
