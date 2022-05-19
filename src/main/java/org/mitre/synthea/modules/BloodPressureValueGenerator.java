package org.mitre.synthea.modules;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mitre.synthea.engine.Components.Range;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.helpers.ValueGenerator;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.BiometricsConfig;
import org.mitre.synthea.world.concepts.HealthRecord;

/**
 * Generate realistic blood pressure vital signs,
 * with impacts from select medications.
 */
public class BloodPressureValueGenerator extends ValueGenerator {
  public enum SysDias {
    SYSTOLIC, DIASTOLIC
  }

  private static final int[] HYPERTENSIVE_SYS_BP_RANGE = BiometricsConfig
      .ints("metabolic.blood_pressure.hypertensive.systolic");
  private static final int[] HYPERTENSIVE_DIA_BP_RANGE = BiometricsConfig
      .ints("metabolic.blood_pressure.hypertensive.diastolic");
  private static final int[] NORMAL_SYS_BP_RANGE = BiometricsConfig
      .ints("metabolic.blood_pressure.normal.systolic");
  private static final int[] NORMAL_DIA_BP_RANGE = BiometricsConfig
      .ints("metabolic.blood_pressure.normal.diastolic");


  // Notes on where these impacts come from:
  // https://www.bmj.com/content/359/bmj.j5542
  // https://pubmed.ncbi.nlm.nih.gov/31668726/
  // https://academic.oup.com/ajh/article/18/7/935/221053
  private static final Table<String, SysDias, Range<Double>> HTN_DRUG_IMPACTS;

  static {
    try {
      Table<String, SysDias, Range<Double>> drugImpacts = HashBasedTable.create();

      String csv = Utilities.readResource("htn_drugs.csv");

      List<LinkedHashMap<String, String>> table = SimpleCSV.parse(csv);

      for (LinkedHashMap<String,String> line : table) {
        String code = line.get("RxNorm");
        double impactMinSystolic = Double.parseDouble(line.get("Systolic Impact Minimum"));
        double impactMaxSystolic = Double.parseDouble(line.get("Systolic Impact Maximum"));

        Range<Double> impactSystolic = new Range<>();
        impactSystolic.low = impactMinSystolic;
        impactSystolic.high = impactMaxSystolic;
        drugImpacts.put(code, SysDias.SYSTOLIC, impactSystolic);

        double impactMinDiastolic = Double.parseDouble(line.get("Diastolic Impact Minimum"));
        double impactMaxDiastolic = Double.parseDouble(line.get("Diastolic Impact Maximum"));
        Range<Double> impactDiastolic = new Range<>();
        impactDiastolic.low = impactMinDiastolic;
        impactDiastolic.high = impactMaxDiastolic;
        drugImpacts.put(code, SysDias.DIASTOLIC, impactDiastolic);
      }

      HTN_DRUG_IMPACTS = drugImpacts;
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  // simple 1-value cache, so that we get consistent results when called with the same timestamp
  // note that consistency is not guaranteed if we go time A -> time B -> time A across modules.
  // in that case we'd need a bigger cache
  private Double cachedValue;
  private long cacheTime;

  private SysDias sysDias;

  public BloodPressureValueGenerator(Person person, SysDias sysDias) {
    super(person);
    this.sysDias = sysDias;
  }

  @Override
  public double getValue(long time) {
    if (cacheTime == time && cachedValue != null) {
      return cachedValue;
    }

    double baseline = calculateBaseline(person);

    cachedValue = baseline
      + getMedicationImpacts(person)
      + getLifestyleImpacts(person, baseline, time)
      + getVariation(person, time);

    cacheTime = time;
    return cachedValue;
  }

  /**
   * Helper function to ensure a person has a consistent impact from a drug.
   */
  private static double getDrugImpact(Person person, String drug, SysDias sysDias,
      Range<Double> impactRange) {
    Table<String, SysDias, Double> personalDrugImpacts =
        (Table<String, SysDias, Double>)person.attributes.get("htn_drug_impacts");
    if (personalDrugImpacts == null) {
      personalDrugImpacts = HashBasedTable.create();
      person.attributes.put("htn_drug_impacts", personalDrugImpacts);
    }

    if (personalDrugImpacts.contains(drug, sysDias)) {
      return personalDrugImpacts.get(drug, sysDias);
    }

    // note these are intentionally flipped. "max impact" == lower number, since these are negative
    double impact = person.rand(impactRange.high, impactRange.low);

    personalDrugImpacts.put(drug, sysDias, impact);

    return impact;
  }

  /**
   * Get the "baseline" BP for a Person.
   * Baseline here means the BP values accounting for hypertension, but before
   * applying medications, lifestyle changes, and intra-daily variation.
   */
  private double calculateBaseline(Person person) {
    boolean hypertension = (boolean) person.attributes.getOrDefault("hypertension", false);
    boolean severe = (boolean) person.attributes.getOrDefault("hypertension_severe", false);

    double baseline;

    String bpBaselineKey = "bp_baseline_" + hypertension + "_" + sysDias.toString();

    if (person.attributes.containsKey(bpBaselineKey)) {
      baseline = (Double)person.attributes.get(bpBaselineKey);
      return baseline;

    } else {
      if (sysDias == SysDias.SYSTOLIC) {
        if (hypertension) {
          if (severe) {
            // this leaves fewer people at the upper end of the spectrum
            baseline = person.rand(HYPERTENSIVE_SYS_BP_RANGE[1], HYPERTENSIVE_SYS_BP_RANGE[2]);

          } else {
            // this skews the distribution to be more on the lower side of the range
            baseline = person.rand(HYPERTENSIVE_SYS_BP_RANGE[0], HYPERTENSIVE_SYS_BP_RANGE[1]);
          }
        } else {
          baseline = person.rand(NORMAL_SYS_BP_RANGE);
        }
      } else {
        if (hypertension) {
          baseline = person.rand(HYPERTENSIVE_DIA_BP_RANGE);
        } else {
          baseline = person.rand(NORMAL_DIA_BP_RANGE);
        }
      }

      if ("M".equals(person.attributes.get(Person.GENDER))) {
        baseline += 1;
      } else {
        baseline -= 1;
      }

      person.attributes.put(bpBaselineKey, baseline);
      return baseline;
    }
  }

  private static final long ONE_YEAR = Utilities.convertTime("years", 1);

  /**
   * Get the amount of impact that lifestyle changes, if this Person is making any,
   * will have on their BP. Lifestyle changes are tracked as specific CarePlans.
   */
  private double getLifestyleImpacts(Person person, double baseline, long time) {
    // if the person has a "blood pressure care plan"
    // assume that over ~1 year their blood pressure will be reduced by ~14 mmHg
    // with most of that happening in the first 6 mos

    // https://pubmed.ncbi.nlm.nih.gov/12709466/

    double maxDrop;
    if (this.sysDias == SysDias.SYSTOLIC) {
      // don't allow diet/exercise alone to drop below 124
      double delta = Math.abs(baseline - 124);
      maxDrop = -Math.min(delta, 14.0);
    } else {
      // don't allow diet/exercise alone to drop below 78
      double delta = Math.abs(baseline - 78);
      maxDrop = -Math.min(delta, 8.0);
    }

    // 443402002 = base hypertension careplan
    // allow for multiple codes, and consider each independently,
    // but if one is active then skip the rest
    // (assume they don't add together)
    String[] carePlanCodes = {"443402002"};

    for (String code : carePlanCodes) {
      double adherenceRatio = 0.15;
      // estimate 3-25% of patients are adherent,
      // so for a single # pick ~15%

      HealthRecord.CarePlan careplan = (HealthRecord.CarePlan) person.record.present.get(code);

      if (careplan != null && careplan.stop == 0L) {
        boolean carePlanAdherent;
        String key = "htn_trial_" + code + "_careplan_adherent";
        if (person.attributes.containsKey(key)) {
          carePlanAdherent = (boolean) person.attributes.get(key);
        } else {
          carePlanAdherent = person.rand() < adherenceRatio;
          person.attributes.put(key, carePlanAdherent);
        }
        if (carePlanAdherent) {
          long start = careplan.start;

          if (time > (start + ONE_YEAR)) {
            // max value
            return maxDrop;
          } else if (time < start) {
            return 0.0;
          }

          // assume a linear relationship between time and drop
          double fractionThruYear = ((double)(time - start)) / ((double)ONE_YEAR);

          return fractionThruYear * maxDrop;
        }
      }
    }

    return 0.0;
  }

  /**
   * Get the amount that Medications will impact this Person's BP.
   */
  private double getMedicationImpacts(Person person) {
    double drugImpactDelta = 0.0;
    // see also LifecycleModule.calculateVitalSigns

    for (Map.Entry<String, Range<Double>> e : HTN_DRUG_IMPACTS.column(this.sysDias).entrySet()) {
      String medicationCode = e.getKey();
      Range<Double> impactRange = e.getValue();
      if (person.record.medicationActive(medicationCode)) {
        double impact = getDrugImpact(person, medicationCode, this.sysDias, impactRange);

        // impacts are negative, so add them (ie, don't subtract them)
        drugImpactDelta += impact;
      }
    }

    return drugImpactDelta;
  }

  private static final long CYCLE_TIME = Utilities.convertTime("hours", 12);

  /**
   * Get an small amount of daily variation in the Person's BP.
   */
  private double getVariation(Person person, long time) {
    // blood pressure can vary significantly during the day
    // https://www.health.harvard.edu/heart-health/experts-call-for-home-blood-pressure-monitoring

    // some notes for the ideal implementation:
    // - systolic and diastolic should generally move in the same direction at the same time
    // - BP should generally be lowest at night (by 10-20%)

    // for now we'll just use a normal distribution, SD of 8 for systolic and 5 for diastolic
    // the sign will be set based on time so that sys & dias have the same one

    double normalSD = this.sysDias == SysDias.SYSTOLIC ? 8 : 5;
    double magnitude = Math.abs(person.randGaussian()) * normalSD;
    int sign = (time / CYCLE_TIME) % 2 == 0 ? 1 : -1;
    // the goal here is to get sign = 1 for ~ half the time and -1 half the time
    // so (x) % 2 == 0 just checks if a number is even.
    // and by dividing time / 12 hours,
    //  we should get a number that is even for 12 hours then odd for 12 hours

    return sign * magnitude;
  }
}