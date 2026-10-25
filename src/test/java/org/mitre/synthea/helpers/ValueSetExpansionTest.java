package org.mitre.synthea.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionContainsComponent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.export.ValueSetCodeResolver;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;

/** Regression coverage for optional totals and hierarchical ValueSet expansions. */
public class ValueSetExpansionTest {
  private static final String URI = "https://example.org/fhir/ValueSet/expansion-regression";
  private static final String SYSTEM = "http://snomed.info/sct";
  private final Map<String, List<Code>> previousCache = new HashMap<>();
  private final List<Code> previousSelectedCodes = new ArrayList<>();

  /** Keep this fixture independent of previously loaded terminology resources. */
  @Before
  public void setUp() {
    previousCache.putAll(RandomCodeGenerator.codeListCache);
    previousSelectedCodes.addAll(RandomCodeGenerator.selectedCodes);
    RandomCodeGenerator.codeListCache.clear();
    RandomCodeGenerator.selectedCodes.clear();
  }

  /** Restore shared caches after each test. */
  @After
  public void tearDown() {
    RandomCodeGenerator.codeListCache.clear();
    RandomCodeGenerator.codeListCache.putAll(previousCache);
    RandomCodeGenerator.selectedCodes.clear();
    RandomCodeGenerator.selectedCodes.addAll(previousSelectedCodes);
  }

  @Test
  public void optionalTotalDoesNotPreventCodeSelection() {
    ValueSet valueSet = expansion();
    addCode(valueSet.getExpansion().addContains(), "A");
    assertFalse(valueSet.getExpansion().hasTotal());

    RandomCodeGenerator.loadValueSet(null, valueSet);

    assertEquals("A", RandomCodeGenerator.getCode(URI, 1234).code);
  }

  @Test
  public void abstractPlaceholderDoesNotHideItsSelectableChild() {
    ValueSet valueSet = expansion();
    valueSet.getExpansion().setTotal(1);
    ValueSetExpansionContainsComponent parent = valueSet.getExpansion().addContains();
    parent.setAbstract(true).setDisplay("Navigation group");
    addCode(parent.addContains(), "A");

    RandomCodeGenerator.loadValueSet(null, valueSet);

    Code selected = RandomCodeGenerator.getCode(URI, 1234);
    assertEquals(SYSTEM, selected.system);
    assertEquals("A", selected.code);
    assertEquals("Concept A", selected.display);
  }

  @Test
  public void selectableParentsAndNestedChildrenAreAllIncluded() {
    ValueSet valueSet = expansion();
    valueSet.getExpansion().setTotal(3);
    ValueSetExpansionContainsComponent parent = valueSet.getExpansion().addContains();
    addCode(parent, "A");
    addCode(parent.addContains(), "B");
    addCode(parent.addContains(), "C");

    RandomCodeGenerator.loadValueSet(null, valueSet);

    assertTrue(RandomCodeGenerator.codeInValueSet(code("A"), URI));
    assertTrue(RandomCodeGenerator.codeInValueSet(code("B"), URI));
    assertTrue(RandomCodeGenerator.codeInValueSet(code("C"), URI));
    assertEquals(3, RandomCodeGenerator.codeListCache.get(URI).size());
  }

  @Test
  public void abstractConceptsWithCodesCannotBeSelected() {
    ValueSet valueSet = expansion();
    valueSet.getExpansion().setTotal(2);
    ValueSetExpansionContainsComponent parent = valueSet.getExpansion().addContains();
    addCode(parent, "A");
    parent.setAbstract(true);
    addCode(parent.addContains(), "B");

    RandomCodeGenerator.loadValueSet(null, valueSet);

    for (int seed = 0; seed < 20; seed++) {
      assertEquals("B", RandomCodeGenerator.getCode(URI, seed).code);
    }
    assertFalse(RandomCodeGenerator.codeInValueSet(code("A"), URI));
  }

  @Test
  public void deeplyNestedNavigationGroupsAreTraversed() {
    ValueSet valueSet = expansion();
    valueSet.getExpansion().setTotal(1);
    ValueSetExpansionContainsComponent parent = valueSet.getExpansion().addContains();
    parent.setAbstract(true).setDisplay("Outer group");
    ValueSetExpansionContainsComponent child = parent.addContains();
    child.setAbstract(true).setDisplay("Inner group");
    addCode(child.addContains(), "A");

    RandomCodeGenerator.loadValueSet(null, valueSet);

    assertEquals("A", RandomCodeGenerator.getCode(URI, 1234).code);
  }

  @Test
  public void onlyAbstractEntriesAreStillAnEmptySelectableExpansion() {
    ValueSet valueSet = expansion();
    valueSet.getExpansion().setTotal(2);
    ValueSetExpansionContainsComponent parent = valueSet.getExpansion().addContains();
    parent.setAbstract(true).setDisplay("Outer group");
    parent.addContains().setAbstract(true).setDisplay("Inner group");

    RuntimeException error = assertThrows(RuntimeException.class,
        () -> RandomCodeGenerator.loadValueSet(null, valueSet));

    assertEquals("ValueSet expansion does not contain any codes", error.getMessage());
    assertFalse(RandomCodeGenerator.codeListCache.containsKey(URI));
  }

  @Test
  public void anExplicitlyEmptyExpansionIsStillRejected() {
    ValueSet valueSet = expansion();
    valueSet.getExpansion().setTotal(0);

    RuntimeException error = assertThrows(RuntimeException.class,
        () -> RandomCodeGenerator.loadValueSet(null, valueSet));

    assertEquals("ValueSet expansion does not contain any codes", error.getMessage());
    assertFalse(RandomCodeGenerator.codeListCache.containsKey(URI));
  }

  @Test
  public void flatExpansionsKeepTheirExistingSeededSelection() {
    ValueSet valueSet = expansion();
    valueSet.getExpansion().setTotal(3);
    addCode(valueSet.getExpansion().addContains(), "A");
    addCode(valueSet.getExpansion().addContains(), "B");
    addCode(valueSet.getExpansion().addContains(), "C");
    RandomCodeGenerator.loadValueSet(null, valueSet);
    String[] expectedOrder = {"A", "B", "C"};

    for (int seed = 0; seed < 20; seed++) {
      String expected = expectedOrder[new Random(seed).nextInt(expectedOrder.length)];
      assertEquals(expected, RandomCodeGenerator.getCode(URI, seed).code);
    }
  }

  @Test
  public void explicitComposeCodesKeepTheirExistingBehavior() {
    ValueSet valueSet = new ValueSet().setUrl(URI).setStatus(PublicationStatus.ACTIVE);
    valueSet.getCompose().addInclude().setSystem(SYSTEM).addConcept()
        .setCode("A").setDisplay("Concept A");

    RandomCodeGenerator.loadValueSet(null, valueSet);

    assertEquals("A", RandomCodeGenerator.getCode(URI, 1234).code);
  }

  @Test
  public void resolverCanUseNestedExpansionWithoutTotal() {
    ValueSet valueSet = expansion();
    ValueSetExpansionContainsComponent parent = valueSet.getExpansion().addContains();
    parent.setAbstract(true).setDisplay("Navigation group");
    addCode(parent.addContains(), "A");
    RandomCodeGenerator.loadValueSet(null, valueSet);
    Person person = new Person(1234);
    Code fallback = code("fallback");
    fallback.valueSet = URI;
    person.attributes.put("coded_result", fallback);
    Code unchanged = code("unchanged");
    person.attributes.put("unmapped_result", unchanged);

    assertSame(person, new ValueSetCodeResolver(person).resolve());

    Code resolved = (Code) person.attributes.get("coded_result");
    assertEquals(SYSTEM, resolved.system);
    assertEquals("A", resolved.code);
    assertEquals("Concept A", resolved.display);
    assertSame(unchanged, person.attributes.get("unmapped_result"));
  }

  private ValueSet expansion() {
    ValueSet valueSet = new ValueSet().setUrl(URI).setStatus(PublicationStatus.ACTIVE);
    valueSet.getExpansion().setTimestamp(new Date(0));
    return valueSet;
  }

  private void addCode(ValueSetExpansionContainsComponent concept, String value) {
    concept.setSystem(SYSTEM).setCode(value).setDisplay("Concept " + value);
  }

  private Code code(String value) {
    return new Code(SYSTEM, value, "Concept " + value);
  }
}
