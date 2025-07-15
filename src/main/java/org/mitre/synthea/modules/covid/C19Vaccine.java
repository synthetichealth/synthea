package org.mitre.synthea.modules.covid;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Pair;
import org.mitre.synthea.helpers.SyncedEnumeratedDistro;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

/**
 * This class provides information on the currently Emergency Use Authorized (EUA) vaccines in the
 * United States. It also provides functionality to select a vaccine based on national usage
 * statistics.
 * <p>
 * Note that the Pfizer vaccine has received full approval from the FDA, so technically it is no
 * longer under EUA.
 * </p>
 */
public class C19Vaccine {
  /** Map of EUA keys to their object representation */
  public static final Map<EUASet, C19Vaccine> EUAs = new TreeMap<>();
  private static SyncedEnumeratedDistro<EUASet> shotSelector;

  private String display;
  private String cvx;
  private boolean twoDose;
  private double usagePercentage;
  private long timeBetweenDoses;

  /**
   * Enum representing the Emergency Use Authorization (EUA) set of vaccines.
   */
  public enum EUASet {
    /** Pfizer-BioNTech COVID-19 Vaccine. */
    PFIZER,
    /** Moderna COVID-19 Vaccine. */
    MODERNA,
    /** Johnson and Johnson (Janssen) COVID-19 Vaccine. */
    JANSSEN
  }

  /**
   * Initialize the class level information on vaccination. Needed so that selectShot will work.
   */
  public static void initialize() {
    EUAs.put(EUASet.PFIZER,
        new C19Vaccine("COVID-19, mRNA, LNP-S, PF, 30 mcg/0.3 mL dose",
            "208", true, 0.531,
            Utilities.convertTime("days", 21)));
    EUAs.put(EUASet.MODERNA,
        new C19Vaccine("COVID-19, mRNA, LNP-S, PF, 100 mcg/0.5mL dose or 50 mcg/0.25mL dose",
            "207", true, 0.398,
            Utilities.convertTime("days", 28)));
    EUAs.put(EUASet.JANSSEN,
        new C19Vaccine("COVID-19 vaccine, vector-nr, rS-Ad26, PF, 0.5 mL",
            "212", false, 0.071, 0));

    List<Pair<EUASet, Double>> pmf = EUAs.entrySet().stream()
        .map(entry -> new Pair<>(entry.getKey(), entry.getValue().getUsagePercentage()))
        .collect(Collectors.toList());

    shotSelector = new SyncedEnumeratedDistro<EUASet>(pmf);
  }

  /**
   * Select a vaccine to be used for an individual.
   * @param person to vaccinate
   * @return a vaccine to use
   */
  public static EUASet selectShot(Person person) {
    if (shotSelector == null) {
      initialize();
    }
    return shotSelector.syncedReseededSample(person);
  }

  /**
   * Create an instance of a COVID-19 Vaccine by supplying information on it.
   * @param display The display text for the vaccine
   * @param cvx The CVX code for the vaccine
   * @param twoDose Should be true if the vaccine requires two doses
   * @param usagePercentage What percentage of Americans have received this vaccine
   * @param timeBetweenDoses For two dose vaccines, milliseconds between doses. 0 for one dose
   *                         vaccines.
   */
  public C19Vaccine(String display, String cvx, boolean twoDose, double usagePercentage,
                    long timeBetweenDoses) {
    this.display = display;
    this.cvx = cvx;
    this.twoDose = twoDose;
    this.usagePercentage = usagePercentage;
    this.timeBetweenDoses = timeBetweenDoses;
  }

  /**
   * Get the display name of the vaccine.
   *
   * @return The display name as a string.
   */
  public String getDisplay() {
    return display;
  }

  /**
   * Set the display name of the vaccine.
   *
   * @param display The display name to set.
   */
  public void setDisplay(String display) {
    this.display = display;
  }

  /**
   * Get the CVX code for the vaccine.
   *
   * @return The CVX code as a string.
   */
  public String getCvx() {
    return cvx;
  }

  /**
   * Set the CVX code for the vaccine.
   *
   * @param cvx The CVX code to set.
   */
  public void setCvx(String cvx) {
    this.cvx = cvx;
  }

  /**
   * Check if the vaccine requires two doses.
   *
   * @return True if the vaccine requires two doses, otherwise false.
   */
  public boolean isTwoDose() {
    return twoDose;
  }

  /**
   * Set whether the vaccine requires two doses.
   *
   * @param twoDose True if the vaccine requires two doses, otherwise false.
   */
  public void setTwoDose(boolean twoDose) {
    this.twoDose = twoDose;
  }

  /**
   * Get the usage percentage of the vaccine.
   *
   * @return The usage percentage as a double.
   */
  public double getUsagePercentage() {
    return usagePercentage;
  }

  /**
   * Set the usage percentage of the vaccine.
   *
   * @param usagePercentage The usage percentage to set.
   */
  public void setUsagePercentage(double usagePercentage) {
    this.usagePercentage = usagePercentage;
  }

  /**
   * Get the time between doses for a two-dose vaccine.
   *
   * @return The time between doses in milliseconds.
   */
  public long getTimeBetweenDoses() {
    return timeBetweenDoses;
  }

  /**
   * Set the time between doses for a two-dose vaccine.
   *
   * @param timeBetweenDoses The time between doses in milliseconds to set.
   */
  public void setTimeBetweenDoses(long timeBetweenDoses) {
    this.timeBetweenDoses = timeBetweenDoses;
  }
}
