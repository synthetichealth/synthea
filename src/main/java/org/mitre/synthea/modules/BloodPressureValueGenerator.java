package org.mitre.synthea.modules;

import java.util.Random;

import org.mitre.synthea.helpers.TrendingValueGenerator;
import org.mitre.synthea.helpers.ValueGenerator;
import org.mitre.synthea.world.agents.Person;


/**
 * Generate realistic blood pressure vital signs. Can reproducibly look a few days into the past and future.
 * @see https://raywinstead.com/bp/thrice.htm for desired result
 */
public class BloodPressureValueGenerator extends ValueGenerator {
    public enum SysDias {
        SYSTOLIC, DIASTOLIC
    }

    // Use a ringbuffer to reproducibly travel back in time for a bit, but not keep a full history per patient.
    private final static int RING_ENTRIES = 10;
    private final TrendingValueGenerator[] ringBuffer = new TrendingValueGenerator[RING_ENTRIES]; 
    private int ringIndex = 0;

    private SysDias sysDias;


    public BloodPressureValueGenerator(Person person, SysDias sysDias) {
        super(person);
        this.sysDias = sysDias;
    }


    @Override
    public double getValue(long time) {
        final TrendingValueGenerator trendingValueGenerator = getTrendingValueGenerator(time, true);
        return trendingValueGenerator.getValue(time);
    }


    /**
     * Return a matching value generator for the given time.
     * 
     * @param time
     * @param createNewGenerators
     * @return a value generator
     */
    private TrendingValueGenerator getTrendingValueGenerator(long time, boolean createNewGenerators) {
        for (int i=0; i < RING_ENTRIES; i++)
        {
            final TrendingValueGenerator trendingValueGenerator = ringBuffer[i];
            if (trendingValueGenerator != null && trendingValueGenerator.getBeginTime() <= time && trendingValueGenerator.getEndTime() >= time)
                return trendingValueGenerator;
        }
        if (!createNewGenerators)
            return null;
        else
        {
            createNewGenerators(time, getTrendingValueGenerator(time - 1L, false));
            return getTrendingValueGenerator(time, false);
        }
    }


    /**
     * Fill the ring buffer with a few new trending sections
     * 
     * @param time
     * @param previousValueGenerator
     */
    private void createNewGenerators(long time, TrendingValueGenerator previousValueGenerator) {
        final long ONE_DAY = 1*24*60*60*1000L;
        final long TEN_DAYS = 10*ONE_DAY;

        long currentTime;
        long generatePeriod;
        double startValue; 
        if (previousValueGenerator == null) {
            // There is no previous buffer entry. Start from a few days in the past.
            currentTime = time - TEN_DAYS;
            generatePeriod = TEN_DAYS + TEN_DAYS;
            startValue = 120.0;  // TODO: Have a function which takes hypertension, etc. into account
        } else {
            currentTime = time;
            generatePeriod = TEN_DAYS;
            startValue = previousValueGenerator.getValue(time - 1L);
        }


        // TODO: This is not yet properly picking up the end value from the previous segment (always starts at 120.0)
        while(generatePeriod > 0L) {
            long duration = ONE_DAY * (1 + person.randInt(4)); // Random duration from 1-5 days.
            double endValue = person.rand(120, 155); // TODO: Use values taking sys/dias + hypertension + age into account
            ringBuffer[ringIndex] = new TrendingValueGenerator(person, 1.0, startValue, endValue,
                    currentTime, currentTime + duration, null, null);
            System.out.println("Filled [" + ringIndex + "] with: " + ringBuffer[ringIndex]);
            ringIndex++;
            ringIndex = ringIndex % RING_ENTRIES;
            currentTime = currentTime + duration + 1L;
            generatePeriod -= duration;
        }
    }
}