package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.world.agents.Person;

public class TransitionTest {

  private Person person;

  @Before
  public void setup() {
    person = new Person(19L); // seed chosen specifically for testDistributedTransition()
  }

  @Test
  public void testDistributedTransition() throws Exception {
    Module distributedTransition = TestHelper.getFixture("distributed_transition.json");

    Map<String, Integer> counts = new HashMap<>();
    counts.put("Terminal1", 0);
    counts.put("Terminal2", 0);
    counts.put("Terminal3", 0);

    for (int i = 0; i < 100; i++) {
      distributedTransition.process(person, 0L);
      @SuppressWarnings("unchecked")
      List<State> history = (List<State>) person.attributes.remove("Distributed Module");
      String finalStateName = history.get(0).name;
      int count = counts.get(finalStateName);
      counts.put(finalStateName, count + 1);
    }

    assertEquals(15, counts.get("Terminal1").intValue());
    assertEquals(55, counts.get("Terminal2").intValue());
    assertEquals(30, counts.get("Terminal3").intValue());
  }

  @Test
  public void testDistributedTransitionWithAttributes() throws Exception {
    person.attributes.put("probability1", 1.0);

    Module distributedTransitionWithAttrs = TestHelper
        .getFixture("distributed_transition_with_attrs.json");

    Map<String, Integer> counts = new HashMap<>();
    counts.put("Terminal1", 0);
    counts.put("Terminal2", 0);
    counts.put("Terminal3", 0);

    for (int i = 0; i < 100; i++) {
      distributedTransitionWithAttrs.process(person, 0L);
      @SuppressWarnings("unchecked")
      List<State> history = (List<State>) person.attributes
          .remove("Distributed With Attributes Module");
      String finalStateName = history.get(0).name;
      int count = counts.get(finalStateName);
      counts.put(finalStateName, count + 1);
    }

    assertEquals(100, counts.get("Terminal1").intValue());
    assertEquals(0, counts.get("Terminal2").intValue());
    assertEquals(0, counts.get("Terminal3").intValue());

    person.attributes.put("probability1", 0.0);
    person.attributes.put("probability2", 0.0);
    person.attributes.put("probability3", 1.0);

    counts.put("Terminal1", 0);
    counts.put("Terminal2", 0);
    counts.put("Terminal3", 0);

    for (int i = 0; i < 100; i++) {
      distributedTransitionWithAttrs.process(person, 0L);
      @SuppressWarnings("unchecked")
      List<State> history = (List<State>) person.attributes
          .remove("Distributed With Attributes Module");
      String finalStateName = history.get(0).name;
      int count = counts.get(finalStateName);
      counts.put(finalStateName, count + 1);
    }

    assertEquals(0, counts.get("Terminal1").intValue());
    assertEquals(0, counts.get("Terminal2").intValue());
    assertEquals(100, counts.get("Terminal3").intValue());
  }
}
