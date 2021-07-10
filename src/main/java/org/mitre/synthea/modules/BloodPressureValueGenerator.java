package org.mitre.synthea.modules;

import java.util.HashMap;
import java.util.Map;

import org.mitre.synthea.engine.Components.Range;
import org.mitre.synthea.helpers.TrendingValueGenerator;
import org.mitre.synthea.helpers.ValueGenerator;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.BiometricsConfig;

/**
 * Generate realistic blood pressure vital signs. 
 * Can reproducibly look a few days into the past and future.
 * <p></p>
 * See <a href="https://raywinstead.com/bp/thrice.htm">https://raywinstead.com/bp/thrice.htm</a>
 * for desired result
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
  
  // simple 1-value cache, so that we get consistent results when called with the same timestamp
  // note that consistency is not guaranteed if we go time A -> time B -> time A across different modules.
  // in that case we need a bigger cache
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

    double value = calculateMean(person, null, 0, time);
    
    cachedValue = value + getMedicationImpacts(person, time);
    cacheTime = time;
    return cachedValue;
  }

  /**
   * Helper function to ensure a person has a consistent impact from a drug
   * @param person
   * @param time
   * @param drug
   * @param impactRange
   * @return
   */
  private static double getDrugImpact(Person person, long time, String drug, Range<Double> impactRange) {
    Map<String, Double> personalDrugImpacts = (Map<String, Double>)person.attributes.get("htn_drug_impacts");
    if (personalDrugImpacts == null) {
      personalDrugImpacts = new HashMap<>();
      person.attributes.put("htn_drug_impacts", personalDrugImpacts);
    }
    
    if (personalDrugImpacts.containsKey(drug)) return personalDrugImpacts.get(drug);
    
    // note these are intentionally flipped. "max impact" == lower number, since these are negative
    double impact = person.rand(impactRange.high, impactRange.low);
    
    personalDrugImpacts.put(drug, impact);
    
    return impact;
  }
  
  private double calculateMean(Person person, Double startValue, int days, long time) {
    boolean hypertension = (Boolean) person.attributes.getOrDefault("hypertension", false);
    boolean severe = (Boolean) person.attributes.getOrDefault("hypertension_severe", false);

    double baseline;
    
    String bpBaselineKey = "bp_baseline_" + hypertension + "_" + sysDias.toString();
    if (person.attributes.containsKey(bpBaselineKey)) {
      baseline = (Double)person.attributes.get(bpBaselineKey);
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
      
      if (person.attributes.get(Person.GENDER).equals("M")) {
        baseline += 1;
      } else {
        baseline -= 1;
      }
      
      person.attributes.put(bpBaselineKey, baseline);
    }
    return baseline;
  }
  
  
  private double getMedicationImpacts(Person person, long time) {
    double drugImpactDelta = 0.0;
    // see also LifecycleModule.calculateVitalSigns
    
    for (Map.Entry<String, Range<Double>> e : HypertensionTrial.HTN_DRUG_IMPACTS.entrySet()) {
      String medicationCode = e.getKey();
      Range<Double> impactRange = e.getValue();
      if (person.record.medicationActive(medicationCode)) {
        double impact = getDrugImpact(person, time, medicationCode, impactRange);
        HypertensionTrial.TitrationDirection titration = HypertensionTrial.getTitrated(medicationCode, person);
        if (titration != null) {
          if (titration.equals(HypertensionTrial.TitrationDirection.UP)) {
            impact = impactRange.high;
          } else {
            // down titrate = cut it in half? TODO: figure out the right approach here
            impact = impact / 2;
          }
        }
        
        // impacts are negative, so add them (ie, don't subtract them)
        drugImpactDelta += impact;
      }
    }
    
    return drugImpactDelta;
  }
}