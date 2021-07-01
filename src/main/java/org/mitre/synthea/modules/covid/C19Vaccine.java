package org.mitre.synthea.modules.covid;

import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.apache.commons.math3.util.Pair;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class C19Vaccine {
  public static final HashMap<EUASet, C19Vaccine> EUAs = new HashMap();
  public static EnumeratedDistribution<EUASet> shotSelector;

  private String display;
  private String cvx;
  private boolean twoDose;
  private double usagePercentage;
  private long timeBetweenDoses;

  public enum EUASet {
    PFIZER,
    MODERNA,
    JANSSEN
  }

  public static void initialize() {
    EUAs.put(EUASet.PFIZER,
        new C19Vaccine("SARS-COV-2 (COVID-19) vaccine, mRNA, spike protein, LNP, preservative free, 30 mcg/0.3mL dose",
            "208", true, 0.531, Utilities.convertTime("days", 21)));
    EUAs.put(EUASet.MODERNA,
        new C19Vaccine("SARS-COV-2 (COVID-19) vaccine, mRNA, spike protein, LNP, preservative free, 100 mcg/0.5mL dose",
            "207", true, 0.398, Utilities.convertTime("days", 28)));
    EUAs.put(EUASet.JANSSEN,
        new C19Vaccine("SARS-COV-2 (COVID-19) vaccine, vector non-replicating, recombinant spike protein-Ad26, preservative free, 0.5 mL",
            "212", false, 0.071, 0));

    List pmf = EUAs.entrySet().stream()
        .map(entry -> new Pair(entry.getKey(), entry.getValue().getUsagePercentage()))
        .collect(Collectors.toList());
    shotSelector = new EnumeratedDistribution(pmf);
  }

  public static EUASet selectShot(Person person) {
    shotSelector.reseedRandomGenerator(person.randLong());
    return shotSelector.sample();
  }

  public C19Vaccine(String display, String cvx, boolean twoDose, double usagePercentage, long timeBetweenDoses) {
    this.display = display;
    this.cvx = cvx;
    this.twoDose = twoDose;
    this.usagePercentage = usagePercentage;
    this.timeBetweenDoses = timeBetweenDoses;
  }

  public String getDisplay() {
    return display;
  }

  public void setDisplay(String display) {
    this.display = display;
  }

  public boolean isTwoDose() {
    return twoDose;
  }

  public void setTwoDose(boolean twoDose) {
    this.twoDose = twoDose;
  }

  public double getUsagePercentage() {
    return usagePercentage;
  }

  public void setUsagePercentage(double usagePercentage) {
    this.usagePercentage = usagePercentage;
  }

  public long getTimeBetweenDoses() {
    return timeBetweenDoses;
  }

  public void setTimeBetweenDoses(long timeBetweenDoses) {
    this.timeBetweenDoses = timeBetweenDoses;
  }

  public String getCvx() {
    return cvx;
  }

  public void setCvx(String cvx) {
    this.cvx = cvx;
  }
}
