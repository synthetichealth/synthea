package org.mitre.synthea.modules;

import java.util.LinkedList;

import org.junit.Assert;
import org.junit.Test;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.VitalSign;

public class PerformCABGTest {
  @Test
  public void test() {
    Person person = new Person(0L);
    person.setVitalSign(VitalSign.BMI, 20.0);
    
    person.attributes.put("cabg_number_of_grafts", (int)person.rand(1, 6));
    person.attributes.put("cabg_arterial_conduits", (int)person.rand(1, 6));
    
    HealthRecord.Procedure opApp = person.record.procedure(0, "OperativeApproach");
    person.attributes.put("cabg_operative_approach", opApp);
    
    person.history = new LinkedList<State>();
    person.history.add(new State.Initial());
    person.history.get(0).name = "Initial";
    PerformCABG module = new PerformCABG();
    Assert.assertFalse(module.process(person, 0L));
    
    long eightHours = Utilities.convertTime("hours", 8);
    Assert.assertTrue(module.process(person, eightHours));
  }
}