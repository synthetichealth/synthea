package org.mitre.synthea.helpers;

import org.mitre.synthea.world.agents.Person;

public abstract class ValueGenerator
{
    protected final Person person;

    protected ValueGenerator(Person person)
    {
        this.person = person;
    }

    /**
     * Get a value at a given point in time
     * 
     * @param time the time, needs to be current or in the future.
     * @return a numerical value
     */
    public abstract double getValue(long time);
}