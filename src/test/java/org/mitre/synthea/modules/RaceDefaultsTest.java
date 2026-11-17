package org.mitre.synthea.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Test;
import org.mitre.synthea.engine.Logic;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.world.agents.Person;

/**
 * The race branches of the gallstones, anemia and cystic fibrosis modules end with an explicit
 * default: patients outside the groups a module names get the module's US average instead of
 * whichever branch came last.
 */
public class RaceDefaultsTest {
  private static final long TIME = 0L;
  private static final double EXACT = 1e-12;

  private static Person person(String race, String gender) {
    Person person = new Person(1L);
    person.attributes.put(Person.RACE, race);
    person.attributes.put(Person.GENDER, gender);
    return person;
  }

  private static State state(String module, String state) throws Exception {
    return Module.loadFile(Paths.get("modules", module + ".json"), false, null, false)
        .getState(state);
  }

  /** A private field, declared by the object's class or one of its superclasses. */
  private static Object field(Object target, String name) throws Exception {
    for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
      try {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
      } catch (NoSuchFieldException e) {
        // look in the superclass
      }
    }
    throw new NoSuchFieldException(name);
  }

  /** The option of a complex transition that a person takes: the first whose condition holds. */
  private static Object option(State state, Person person) throws Exception {
    for (Object option : (List<?>) field(state.getTransition(), "transitions")) {
      Logic condition = (Logic) field(option, "condition");
      if (condition == null || condition.test(person, TIME)) {
        return option;
      }
    }
    throw new AssertionError("No option for " + person.attributes.get(Person.RACE));
  }

  /** The chance that a complex transition's option gives to a state. */
  private static double chance(Object option, String target) throws Exception {
    for (Object distribution : (List<?>) field(option, "distributions")) {
      if (target.equals(field(distribution, "transition"))) {
        return ((Number) field(distribution, "distribution")).doubleValue();
      }
    }
    throw new AssertionError("No transition to " + target);
  }

  private static double chance(String module, String state, String target, Person person)
      throws Exception {
    return chance(option(state(module, state), person), target);
  }

  @Test
  public void gallstonesDefaultIsTheUsAverage() throws Exception {
    for (String race : new String[] {"other", "hawaiian"}) {
      Object women = option(state("gallstones", "Female"), person(race, "F"));
      assertNull("an explicit default", field(women, "condition"));
      assertEquals(0.177, chance(women, "Female_Gallstone_Formation"), EXACT);
      assertEquals(0.079, chance("gallstones", "Male", "Male_Gallstone_Formation",
          person(race, "M")), EXACT);
    }
    // The named groups keep their figures
    assertEquals(0.166, chance("gallstones", "Female", "Female_Gallstone_Formation",
        person("white", "F")), EXACT);
    assertEquals(0.295, chance("gallstones", "Male", "Male_Gallstone_Formation",
        person("native", "M")), EXACT);
  }

  @Test
  public void anemiaDefaultIsByTheSexOfThePatient() throws Exception {
    State initial = state("anemia___unknown_etiology", "Initial");
    for (String race : new String[] {"asian", "native", "other", "hawaiian"}) {
      assertEquals("Other_Males", initial.getTransition().follow(person(race, "M"), TIME));
      assertEquals("Other_Females", initial.getTransition().follow(person(race, "F"), TIME));
    }
    assertEquals("White_Males", initial.getTransition().follow(person("white", "M"), TIME));
    assertEquals("Black_Females", initial.getTransition().follow(person("black", "F"), TIME));
  }

  @Test
  public void cysticFibrosisHasNativeAndDefaultFigures() throws Exception {
    // 1 in 10,900 Native American births; 1 in 4,261, the US average, for the others
    assertEquals(0.000091743, chance("cystic_fibrosis", "Potential_Onset", "Cystic_Fibrosis",
        person("native", "F")), EXACT);
    for (String race : new String[] {"other", "hawaiian"}) {
      assertEquals(0.000234672, chance("cystic_fibrosis", "Potential_Onset", "Cystic_Fibrosis",
          person(race, "M")), EXACT);
    }
    assertEquals(0.000028571, chance("cystic_fibrosis", "Potential_Onset", "Cystic_Fibrosis",
        person("asian", "F")), EXACT);
  }
}
