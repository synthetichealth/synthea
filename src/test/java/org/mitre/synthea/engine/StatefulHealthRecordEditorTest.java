package org.mitre.synthea.engine;

import static java.lang.Thread.sleep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

public class StatefulHealthRecordEditorTest {

  public static class Dummy extends StatefulHealthRecordEditor {
    @Override
    public boolean shouldRun(Person person, HealthRecord record, long time) {
      return false;
    }

    @Override
    public void process(Person person, List<HealthRecord.Encounter> encounters,
        long time) {
    }
  }
  
  Dummy dummy;

  @Before
  public void setup() {
    dummy = new Dummy();
  }
  
  @Test
  public void testDifferentPerson() {
    Person p1 = new Person(1);
    Person p2 = new Person(1);
    Map<String, Object> context = dummy.getOrInitContextFor(p1);
    assertTrue(context.isEmpty());
    assertFalse(dummy.isNewPerson(p1));
    assertTrue(dummy.isNewPerson(p2));
  }
  
  @Test
  public void testClearContext() {
    Person p1 = new Person(1);
    Map<String, Object> context = dummy.getOrInitContextFor(p1);
    context.put("FOO", "BAR");
    assertEquals(1, context.size());
    dummy.clearContext();
    context = dummy.getContext();
    assertTrue(context.isEmpty());
  }
  
  @Test
  public void testNewContextForNewPerson() {
    Person p1 = new Person(1);
    Map<String, Object> context = dummy.getOrInitContextFor(p1);
    context.put("FOO", "BAR");
    assertEquals(1, context.size());
    context = dummy.getContext();
    assertEquals(1, context.size());
    context = dummy.getOrInitContextFor(p1);
    assertEquals(1, context.size());
    Person p2 = new Person(1);
    context = dummy.getOrInitContextFor(p2);
    assertTrue(context.isEmpty());
  }
  
  public static class DummyRunner implements Runnable {
    
    private final Dummy dummy;
    private final int keys;
    
    public DummyRunner(Dummy dummy, int keys) {
      this.dummy = dummy;
      this.keys = keys;
    }
    
    @Override
    public void run() {
      Person p1 = new Person(1);
      Map<String, Object> context = dummy.getOrInitContextFor(p1);
      for (int i = 0; i < keys; i++) {
        context.put(Integer.toString(i), i);
      }
      try {
        sleep(250);
      } catch (InterruptedException ex) {
        // Ignore
      }
      assertEquals(keys, dummy.getContext().size());
    }
    
  }
  
  @Test
  public void testTwoThreads() throws InterruptedException {
    Thread t1 = new Thread(new DummyRunner(dummy, 5));
    Thread t2 = new Thread(new DummyRunner(dummy, 10));
    t1.start();
    t2.start();
    t1.join();
    t2.join();
  }
}
