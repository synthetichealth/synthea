package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.time.Instant;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.healthinsurance.PlanRecord;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Deterministic coverage of C-CDA export outside an insurance enrollment period. */
public class CCDACoverageBoundaryTest {
  private static final long BIRTH_TIME = Instant.parse("1970-01-01T00:00:00Z").toEpochMilli();
  private static final long LATER_EXPORT_TIME = Instant.parse("1975-06-01T00:00:00Z")
      .toEpochMilli();
  private static final String[] REQUIRED_SECTIONS = {
    "2.16.840.1.113883.10.20.22.2.6.1", "2.16.840.1.113883.10.20.22.2.1.1",
    "2.16.840.1.113883.10.20.22.2.5.1", "2.16.840.1.113883.10.20.22.2.3.1",
    "2.16.840.1.113883.10.20.22.2.17", "2.16.840.1.113883.10.20.22.2.4.1"
  };

  private Field noInsuranceField;
  private Payer previousNoInsurance;

  /** Isolate the no-insurance fixture without replacing any other cached payers. */
  @Before
  public void setUp() throws Exception {
    noInsuranceField = PayerManager.class.getDeclaredField("noInsurance");
    noInsuranceField.setAccessible(true);
    previousNoInsurance = (Payer) noInsuranceField.get(null);
    PayerManager.loadNoInsurance();
  }

  /** Restore the exact no-insurance payer that was present before each test. */
  @After
  public void tearDown() throws Exception {
    noInsuranceField.set(null, previousNoInsurance);
  }

  @Test
  public void exportAtEnrollmentBoundaryCreatesActiveFallbackCoverage() throws Exception {
    Person person = person();
    assertFallbackCoverage(person, person.coverage.getLastPlanRecord().getStopTime());
  }

  @Test
  public void exportAfterEnrollmentBoundaryCreatesActiveFallbackCoverage() throws Exception {
    Person person = person();
    assertFallbackCoverage(person, person.coverage.getLastPlanRecord().getStopTime() + 1);
  }

  @Test
  public void exportSeveralYearsAfterEnrollmentCreatesActiveFallbackCoverage() throws Exception {
    assertFallbackCoverage(person(), LATER_EXPORT_TIME);
  }

  @Test
  public void exportAfterDeathCreatesFiniteActiveFallbackCoverage() throws Exception {
    Person person = person();
    person.attributes.put(Person.DEATHDATE,
        Instant.parse("1970-06-01T00:00:00Z").toEpochMilli());

    assertFallbackCoverage(person, LATER_EXPORT_TIME);
  }

  @Test
  public void exportWithActiveCoverageKeepsTheExistingPlan() throws Exception {
    Person person = person();
    PlanRecord initial = person.coverage.getLastPlanRecord();
    long originalStop = initial.getStopTime();

    assertExport(person, originalStop - 1);

    assertEquals(1, person.coverage.getPlanHistory().size());
    assertSame(initial, person.coverage.getPlanRecordAtTime(originalStop - 1));
    assertEquals(originalStop, initial.getStopTime());
  }

  @Test
  public void repeatedExportAtTheSameTimeKeepsTheFallbackPlan() throws Exception {
    Person person = person();
    assertFallbackCoverage(person, LATER_EXPORT_TIME);
    PlanRecord fallback = person.coverage.getPlanRecordAtTime(LATER_EXPORT_TIME);
    long originalStop = fallback.getStopTime();

    assertExport(person, LATER_EXPORT_TIME);

    assertEquals(2, person.coverage.getPlanHistory().size());
    assertSame(fallback, person.coverage.getPlanRecordAtTime(LATER_EXPORT_TIME));
    assertEquals(originalStop, fallback.getStopTime());
  }

  private void assertFallbackCoverage(Person person, long exportTime) throws Exception {
    PlanRecord initial = person.coverage.getLastPlanRecord();

    assertExport(person, exportTime);

    assertEquals(2, person.coverage.getPlanHistory().size());
    assertEquals(exportTime, initial.getStopTime());
    assertSame(initial, person.coverage.getPlanRecordAtTime(exportTime - 1));
    PlanRecord fallback = person.coverage.getPlanRecordAtTime(exportTime);
    assertEquals(exportTime, fallback.getStartTime());
    assertTrue(fallback.getStopTime() > exportTime);
    assertTrue(fallback.getStopTime() < Long.MAX_VALUE);
    assertTrue(fallback.getPlan().isNoInsurance());
  }

  private void assertExport(Person person, long exportTime) throws Exception {
    String xml = CCDAExporter.export(person, exportTime);
    Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(new InputSource(new StringReader(xml)));
    assertEquals("ClinicalDocument", document.getDocumentElement().getNodeName());
    XPath xpath = XPathFactory.newInstance().newXPath();
    for (String section : REQUIRED_SECTIONS) {
      NodeList sections = (NodeList) xpath.evaluate(
          "/ClinicalDocument/component/structuredBody/component/section"
          + "[templateId/@root='" + section + "']", document, XPathConstants.NODESET);
      assertEquals("Expected one C-CDA section " + section, 1, sections.getLength());
    }
  }

  private Person person() {
    Person person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, BIRTH_TIME);
    person.attributes.put(Person.ID, "coverage-boundary-patient");
    person.attributes.put(Person.NAME, "Coverage Patient");
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.RACE, "white");
    person.attributes.put(Person.ETHNICITY, "nonhispanic");
    person.attributes.put(Person.ADDRESS, "1 Test Street");
    person.attributes.put(Person.CITY, "Bedford");
    person.attributes.put(Person.STATE, "Massachusetts");
    person.attributes.put(Person.ZIP, "01730");
    Provider provider = new Provider();
    provider.name = "Test Provider";
    provider.address = "2 Test Street";
    provider.city = "Bedford";
    provider.state = "Massachusetts";
    provider.zip = "01730";
    person.attributes.put(Person.PREFERREDYPROVIDER + "wellness", provider);
    person.coverage.setPlanToNoInsurance(BIRTH_TIME);
    return person;
  }
}
