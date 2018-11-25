package org.mitre.synthea.helpers;

import org.mitre.synthea.world.agents.Person;

/**
 * A value generator that returns a set constant value for all eternity.
 */
public class ConstantValueGenerator extends ValueGenerator
{
    private double value;

    public ConstantValueGenerator(Person person, double value)
    {
        super(person);
        this.value = value;
    }

    @Override
    public double getValue(long time)
    {
        return this.value;
    }
}