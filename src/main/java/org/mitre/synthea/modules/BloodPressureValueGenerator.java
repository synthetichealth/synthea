package org.mitre.synthea.modules;

import org.mitre.synthea.helpers.TrendingValueGenerator;
import org.mitre.synthea.helpers.ValueGenerator;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.BiometricsConfig;

/**
 * Generate realistic blood pressure vital signs. 
 * Can reproducibly look a few days into the past and future.
 * 
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

  private static final long ONE_DAY = 1 * 24 * 60 * 60 * 1000L;

  // How far into the past or into the future can this generator look reproducibly?
  private static final long TIMETRAVEL_DURATION = 10 * ONE_DAY;
  

  // Use a ringbuffer to reproducibly travel back in time for a bit, but not keep
  // a full history per patient.
  private static final int RING_ENTRIES = 10;
  private final TrendingValueGenerator[] ringBuffer = new TrendingValueGenerator[RING_ENTRIES];
  private int ringIndex = 0;

  private SysDias sysDias;

  public BloodPressureValueGenerator(Person person, SysDias sysDias) {
    super(person);
    this.sysDias = sysDias;
  }

  @Override
  public double getValue(long time) {
    // TODO: BP is circadian. Model change over time of day.
    final TrendingValueGenerator trendingValueGenerator = getTrendingValueGenerator(time, true);
    return trendingValueGenerator.getValue(time);
  }


  /**
   * The minimum duration that the trend continues in one direction.
   * @return the duration in days.
   */
  private int minTrendDuration() {
    return 2;
  }

  /**
   * The maximum duration that the trend continues in one direction.
   * @return the duration in days.
   */
  private int maxTrendDuration() {
    return 5;
  }

  /**
   * The maximum permitted change per day.
   * @return the maximum change per day.
   */
  private double maxChangePerDay() {
    return 15.0;
  }

  /**
   * Return a matching value generator for the given time.
   * 
   * @param time find a value generator for which time stamp?
   * @param createNewGenerators should a new generator be created when no match can be found?
   * @return a value generator, or potentially null (if none exists and none should be created)
   */
  private TrendingValueGenerator getTrendingValueGenerator(long time, boolean createNewGenerators) {
    // System.out.println("getTVG @ " + time);

    for (int i = 0; i < RING_ENTRIES; i++) {
      final TrendingValueGenerator trendingValueGenerator = ringBuffer[i];
      if (trendingValueGenerator != null && trendingValueGenerator.getBeginTime() <= time
          && trendingValueGenerator.getEndTime() >= time) {
        return trendingValueGenerator;
      }
    }
    if (!createNewGenerators) {
      return null;
    } else {
      createNewGenerators(time);
      return getTrendingValueGenerator(time, false);
    }
  }

  /**
   * Fill the ring buffer with a few new trending sections.
   * 
   * @param time a timestamp which shall be covered by the generators in the buffer
   * @param previousValueGenerator a previous generator, for potential continuity
   */
  private void createNewGenerators(long time) {
    int endIndex = 0;
    long endTime = Long.MIN_VALUE;
    for (int i = 0; i < RING_ENTRIES; i++) {
      if (ringBuffer[i] != null && ringBuffer[i].getEndTime() > endTime) {
        endIndex = i;
        endTime = ringBuffer[i].getEndTime();
      }
    }

    TrendingValueGenerator previousValueGenerator;
    if (time - endTime < TIMETRAVEL_DURATION) {
      // If the last ringbuffer entry is maximum of TIMETRAVEL_DURATION days in the past,
      // then continue from it.
      previousValueGenerator = ringBuffer[endIndex];
    } else {
      // Last entry is too far in the past. Start over.
      previousValueGenerator = null;
    }

    long currentTime;
    long generatePeriod;
    double startValue;
    if (previousValueGenerator == null) {
      // There is no recent previous buffer entry. Start from a few days in the past.
      // System.out.println("Starting over");
      currentTime = time - TIMETRAVEL_DURATION;
      generatePeriod = TIMETRAVEL_DURATION + TIMETRAVEL_DURATION;
      startValue = calculateMean(person, currentTime);
    } else {
      // System.out.println("Continuing @ " + endTime);
      currentTime = endTime;
      generatePeriod = TIMETRAVEL_DURATION;
      startValue = previousValueGenerator.getValue(endTime);
    }

    while (generatePeriod > 0L) {
      final int days = minTrendDuration() 
          + person.randInt(maxTrendDuration() - minTrendDuration() + 1);
      long duration = ONE_DAY * days;
      double endValue;
      do { // Limit the maximum rate of change.
        endValue = calculateMean(person, currentTime + duration);
      } while (Math.abs(startValue - endValue) > maxChangePerDay() * duration);

      ringBuffer[ringIndex] = new TrendingValueGenerator(person, 1.0, startValue, endValue,
          currentTime, currentTime + duration, null, null);
      // System.out.println("Filled [" + ringIndex + "] with: " + ringBuffer[ringIndex]);
      ringIndex++;
      ringIndex = ringIndex % RING_ENTRIES;
      currentTime = currentTime + duration + 1L;
      generatePeriod -= duration;
      startValue = endValue;
    }
  }

  private double calculateMean(Person person, long time) {
    // TODO: Take additional factors into consideration: age + gender
    boolean hypertension = (Boolean) person.attributes.getOrDefault("hypertension", false);
    boolean bloodPressureControlled =
        (Boolean) person.attributes.getOrDefault("blood_pressure_controlled", false);

    if (sysDias == SysDias.SYSTOLIC) {
      if (hypertension) {
        if (!bloodPressureControlled) {
          return person.rand(HYPERTENSIVE_SYS_BP_RANGE);
        } else {
          return person.rand(NORMAL_SYS_BP_RANGE);
        }
      } else {
        return person.rand(NORMAL_SYS_BP_RANGE);
      }
    } else {
      if (hypertension) {
        if (!bloodPressureControlled) {
          return person.rand(HYPERTENSIVE_DIA_BP_RANGE);
        } else {
          return person.rand(NORMAL_DIA_BP_RANGE);
        }
      } else {
        return person.rand(NORMAL_DIA_BP_RANGE);
      }
    }
  }
}