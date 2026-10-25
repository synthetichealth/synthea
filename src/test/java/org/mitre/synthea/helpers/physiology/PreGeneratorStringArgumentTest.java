package org.mitre.synthea.helpers.physiology;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;
import org.mitre.synthea.helpers.ConstantValueGenerator;
import org.mitre.synthea.helpers.ValueGenerator;
import org.mitre.synthea.helpers.physiology.PreGenerator.PreGeneratorArg;
import org.mitre.synthea.world.agents.Person;

public class PreGeneratorStringArgumentTest {
  @Test
  public void stringArgumentSelectsPersonAttributeAlongsidePrimitiveArgument() {
    Person person = new Person(0);
    person.attributes.put("measurement", 42.0);
    person.attributes.put("other", 100.0);

    ValueGenerator generator = attributeGenerator(person, "measurement", 1.5);

    assertEquals(43.5, generator.getValue(0), 0.0);
  }

  @Test
  public void stringArgumentPreservesWhitespaceAndPunctuation() {
    Person person = new Person(0);
    String attribute = "  pressure / #{source}  ";
    person.attributes.put(attribute, 127.25);
    person.attributes.put(attribute.trim(), 99.0);

    ValueGenerator generator = attributeGenerator(person, attribute, -0.5);

    assertEquals(126.75, generator.getValue(0), 0.0);
  }

  @Test
  public void emptyStringRemainsAStringArgument() {
    Person person = new Person(0);
    person.attributes.put("", 5.0);

    ValueGenerator generator = attributeGenerator(person, "", 2.0);

    assertEquals(7.0, generator.getValue(0), 0.0);
  }

  @Test
  public void numericLookingStringIsNotConvertedToANumber() {
    Person person = new Person(0);
    person.attributes.put("001", 31.0);
    person.attributes.put("1", 100.0);

    ValueGenerator generator = attributeGenerator(person, "001", 0.0);

    assertEquals(31.0, generator.getValue(0), 0.0);
  }

  @Test
  public void existingNumericGeneratorStillReceivesItsPrimitiveValue() {
    PreGenerator config = new PreGenerator();
    config.setClassName(ConstantValueGenerator.class.getName());
    config.setArgs(Arrays.asList(argument("java.lang.Double", "3.25", true)));

    assertEquals(3.25, config.getGenerator(new Person(0)).getValue(0), 0.0);
  }

  @Test
  public void omittedArgumentsStillPassThePersonToTheConstructor() {
    Person person = new Person(0);
    person.attributes.put("measurement", 17.5);
    PreGenerator config = new PreGenerator();
    config.setClassName(PersonOnlyGenerator.class.getName());

    assertEquals(17.5, config.getGenerator(person).getValue(0), 0.0);
  }

  private static ValueGenerator attributeGenerator(Person person, String attribute, double offset) {
    PreGenerator config = new PreGenerator();
    config.setClassName(AttributeGenerator.class.getName());
    config.setArgs(Arrays.asList(
        argument("java.lang.String", attribute, false),
        argument("java.lang.Double", Double.toString(offset), true)));
    return config.getGenerator(person);
  }

  private static PreGeneratorArg argument(String type, String value, boolean primitive) {
    PreGeneratorArg argument = new PreGeneratorArg();
    argument.setType(type);
    argument.setValue(value);
    argument.setPrimitive(primitive);
    return argument;
  }

  /** A configurable generator that reads a named attribute and applies an offset. */
  public static class AttributeGenerator extends ValueGenerator {
    private final String attribute;
    private final double offset;

    /**
     * Construct a generator using both String and primitive constructor arguments.
     * @param person the person whose attribute is read
     * @param attribute the exact attribute name
     * @param offset an offset applied to the attribute value
     */
    public AttributeGenerator(Person person, String attribute, double offset) {
      super(person);
      this.attribute = attribute;
      this.offset = offset;
    }

    @Override
    public double getValue(long time) {
      return ((Number) person.attributes.get(attribute)).doubleValue() + offset;
    }
  }

  /** A generator whose only constructor argument is the Person. */
  public static class PersonOnlyGenerator extends ValueGenerator {
    /**
     * Construct a generator without any explicitly configured arguments.
     * @param person the person whose measurement is read
     */
    public PersonOnlyGenerator(Person person) {
      super(person);
    }

    @Override
    public double getValue(long time) {
      return ((Number) person.attributes.get("measurement")).doubleValue();
    }
  }
}
