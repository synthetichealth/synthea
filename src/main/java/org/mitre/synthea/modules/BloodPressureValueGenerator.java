package org.mitre.synthea.modules;

import org.mitre.synthea.helpers.ValueGenerator;
import org.mitre.synthea.world.agents.Person;


public class BloodPressureValueGenerator extends ValueGenerator {
    public enum SysDias {
        SYSTOLIC, DIASTOLIC
    }

    private SysDias sysDias;

    
    public BloodPressureValueGenerator(Person person, SysDias sysDias) {
        super(person);
        this.sysDias = sysDias;
    }


    @Override
    public double getValue(long time) {
        return 0;
    }
}