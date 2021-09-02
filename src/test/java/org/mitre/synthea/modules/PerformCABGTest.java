package org.mitre.synthea.modules;

import java.util.LinkedList;

import org.junit.Assert;
import org.junit.Test;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.VitalSign;

public class PerformCABGTest {
  @Test
  public void test() {
    Person person = new Person(0L);
    person.setVitalSign(VitalSign.BMI, 20.0);
    person.history = new LinkedList<State>();
    person.history.add(new State.Initial());
    person.history.get(0).name = "Initial";
    PerformCABG module = new PerformCABG();
    Assert.assertFalse(module.process(person, 0L));
    Assert.assertTrue(module.process(person, 1_000_0000L));
  }
}