package org.mitre.synthea.helpers;

import org.mitre.synthea.world.agents.Person;

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