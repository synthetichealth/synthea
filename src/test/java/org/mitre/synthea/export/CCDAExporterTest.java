package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.FailedExportHelper;
import org.mitre.synthea.ParallelTestingService;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.geography.Location;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * This is a rudimentary check of the CCDA Export. It uses XPath expressions to ensure that the
 * mandatory sections for the Continuity of Care Document (CCD) (V3) are present as specified in
 * HL7 Implementation Guide for CDA® Release 2: Consolidated CDA Templates for Clinical Notes
 * Specification Version: 2.1.0.7 September 2022.
 */
public class CCDAExporterTest {

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  public static HashMap<String, XPathExpression> sectionExpressions = new HashMap<>();

  /**
   * Creates XPath expressions to find each mandatory section in the CCDA / CCD document.
   * @throws XPathExpressionException should never happen
   */
  @BeforeClass
  public static void loadXPathExpressions() throws XPathExpressionException {
    XPathFactory xpathFactory = XPathFactory.newInstance();
    XPath xpath = xpathFactory.newXPath();
    XPathExpression allergiesSection = xpath.compile("/ClinicalDocument/component/structuredBody"
            + "/component/section/templateId[@root=\"2.16.840.1.113883.10.20.22.2.6.1\"]");
    sectionExpressions.put("Allergies and Intolerances Section", allergiesSection);
    XPathExpression medicationSection = xpath.compile("/ClinicalDocument/component/structuredBody"
            + "/component/section/templateId[@root=\"2.16.840.1.113883.10.20.22.2.1.1\"]");
    sectionExpressions.put("Medications Section", medicationSection);
    XPathExpression problemSection = xpath.compile("/ClinicalDocument/component/structuredBody"
            + "/component/section/templateId[@root=\"2.16.840.1.113883.10.20.22.2.5.1\"]");
    sectionExpressions.put("Problem Section", problemSection);
    XPathExpression resultsSection = xpath.compile("/ClinicalDocument/component/structuredBody"
            + "/component/section/templateId[@root=\"2.16.840.1.113883.10.20.22.2.3.1\" "
            + "and @extension=\"2015-08-01\"]");
    sectionExpressions.put("Results Section", resultsSection);
    XPathExpression socialHistorySection = xpath.compile("/ClinicalDocument/component"
            + "/structuredBody/component/section"
            + "/templateId[@root=\"2.16.840.1.113883.10.20.22.2.17\" "
            + "and @extension=\"2015-08-01\"]");
    sectionExpressions.put("Social History Section", socialHistorySection);
    XPathExpression vitalSignsSection = xpath.compile("/ClinicalDocument/component/structuredBody"
            + "/component/section/templateId[@root=\"2.16.840.1.113883.10.20.22.2.4.1\"]");
    sectionExpressions.put("Vital Signs Section", vitalSignsSection);
    XPathExpression vitalSignsOrganizerInterval = xpath.compile(
        "/ClinicalDocument/component/structuredBody/component/section"
        + "/templateId[@root=\"2.16.840.1.113883.10.20.22.2.4.1\"]"
        + "/../entry/organizer/templateId[@root=\"2.16.840.1.113883.10.20.22.4.26\"]"
        + "/../effectiveTime/low/../high");
    sectionExpressions.put("Vital Signs Organizer Interval", vitalSignsOrganizerInterval);
  }

  @Test
  public void testCCDAExport() throws Exception {
    PayerManager.clear();
    PayerManager.loadPayers(new Location(Generator.DEFAULT_STATE, null));
    TestHelper.loadTestProperties();
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Massachusetts");
    Config.set("exporter.baseDirectory", tempFolder.newFolder().toString());
    List<String> errors = ParallelTestingService.runInParallel((person) -> {
      List<String> validationErrors = new ArrayList<String>();
      TestHelper.exportOff();
      Config.set("exporter.ccda.export", "true");
      String ccdaXml = CCDAExporter.export(person, System.currentTimeMillis());
      InputStream inputStream = IOUtils.toInputStream(ccdaXml, "UTF-8");
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(inputStream);
      sectionExpressions.forEach((section, expression) -> {
        try {
          NodeList nodeList = (NodeList) expression.evaluate(doc, XPathConstants.NODESET);
          if (nodeList.getLength() == 0) {
            validationErrors.add("Unable to find the " + section);
          }
          if (nodeList.getLength() > 1) {
            validationErrors.add("More than one " + section);
          }
        } catch (XPathExpressionException xpe) {
          validationErrors.add("Issue trying to find the " + section + "\n" + xpe.getMessage());
        }
      });
      return validationErrors;
    });

    assertEquals("Validation of exported CCDA failed: "
        + String.join("|", errors), 0, errors.size());
  }

  @Test
  public void testExportWithNoPreferredWellnessProvider() throws Exception {
    PayerManager.clear();
    PayerManager.loadPayers(new Location(Generator.DEFAULT_STATE, null));
    TestHelper.loadTestProperties();
    Person[] people = TestHelper.getGeneratedPeople();
    List<String> validationErrors = new ArrayList<String>();
    TestHelper.exportOff();
    Config.set("exporter.ccda.export", "true");
    Optional<Person> personWithWellnessProvider = Arrays.stream(people).filter((person -> {
      return person.attributes.get(Person.PREFERREDYPROVIDER + "wellness") != null;
    })).findFirst();
    if (personWithWellnessProvider.isPresent()) {
      Person toExport = personWithWellnessProvider.get();
      toExport.attributes.remove(Person.PREFERREDYPROVIDER + "wellness");
      String ccdaXml = CCDAExporter.export(toExport, System.currentTimeMillis());
      InputStream inputStream = IOUtils.toInputStream(ccdaXml, "UTF-8");
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(inputStream);
      sectionExpressions.forEach((section, expression) -> {
        try {
          NodeList nodeList = (NodeList) expression.evaluate(doc, XPathConstants.NODESET);
          if (nodeList.getLength() == 0) {
            validationErrors.add("Unable to find the " + section);
          }
          if (nodeList.getLength() > 1) {
            validationErrors.add("More than one " + section);
          }
        } catch (XPathExpressionException xpe) {
          validationErrors.add("Issue trying to find the " + section + "\n" + xpe.getMessage());
        }
      });
      assertEquals(0, validationErrors.size());
    } else {
      System.out.println("There were no people generated that have wellness providers... odd.");
    }
  }

  @Test
  public void testExportDoesNotCreateMaxValueCoverageStopTime() throws Exception {
    PayerManager.clear();
    PayerManager.loadPayers(new Location(Generator.DEFAULT_STATE, null));
    TestHelper.loadTestProperties();
    Person toExport = TestHelper.getGeneratedPeople()[0];
    long exportTime = System.currentTimeMillis();
    toExport.coverage.getLastPlanRecord().updateStopTime(exportTime - 1);

    CCDAExporter.export(toExport, exportTime);

    assertFalse(toExport.coverage.getPlanHistory().stream()
        .anyMatch(planRecord -> planRecord.getStopTime() == Long.MAX_VALUE));
  }

  /**
   * Verifies C-CDA allergy severity structure per spec (2.16.840.1.113883.10.20.22.4.7):
   * - Each reaction's severity (4.8) is nested inside its reaction observation (4.9).
   * - The allergy-level severity (4.8) appears exactly once as a direct child of the allergy
   *   observation (4.7), characterizing the overall allergy.
   */
  @Test
  public void testAllergyReactionSeverityNesting() throws Exception {
    PayerManager.clear();
    PayerManager.loadPayers(new Location(Generator.DEFAULT_STATE, null));
    TestHelper.loadTestProperties();

    Person person = TestHelper.getGeneratedPeople()[0];

    // Build an allergy with one reaction+severity and a derived allergy-level severity.
    HealthRecord.Allergy allergy = person.record.new Allergy(0L, "test_penicillin_allergy");
    allergy.codes.add(new HealthRecord.Code(
        "http://www.nlm.nih.gov/research/umls/rxnorm", "723", "Amoxicillin"));
    allergy.allergyType = "allergy";
    allergy.category = "medication";
    allergy.reactions = new HashMap<>();
    allergy.reactions.put(
        new HealthRecord.Code("http://snomed.info/sct", "247472004", "Wheal (finding)"),
        HealthRecord.ReactionSeverity.MODERATE);
    allergy.severity = HealthRecord.ReactionSeverity.MODERATE;
    person.record.encounters.get(0).allergies.add(allergy);

    String ccdaXml = CCDAExporter.export(person, System.currentTimeMillis());

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document doc = builder.parse(IOUtils.toInputStream(ccdaXml, "UTF-8"));
    XPath xpath = XPathFactory.newInstance().newXPath();

    // Reaction-level severity must be nested inside its reaction observation (4.9).
    String reactionSeverityPath =
        "//observation[templateId/@root='2.16.840.1.113883.10.20.22.4.9']"
        + "/entryRelationship[@typeCode='SUBJ']"
        + "/observation[templateId/@root='2.16.840.1.113883.10.20.22.4.8']";
    NodeList reactionSeverities =
        (NodeList) xpath.evaluate(reactionSeverityPath, doc, XPathConstants.NODESET);
    assertTrue("Reaction severity must be nested inside its reaction observation",
        reactionSeverities.getLength() > 0);

    // Allergy-level severity must appear exactly once as a direct child of the allergy obs (4.7).
    String allergySeverityPath =
        "//observation[templateId/@root='2.16.840.1.113883.10.20.22.4.7']"
        + "/entryRelationship[@typeCode='SUBJ'][@inversionInd='true']"
        + "/observation[templateId/@root='2.16.840.1.113883.10.20.22.4.8']";
    NodeList allergySeverities =
        (NodeList) xpath.evaluate(allergySeverityPath, doc, XPathConstants.NODESET);
    assertEquals("Allergy-level severity must appear exactly once under the allergy observation",
        1, allergySeverities.getLength());
  }

  @Ignore("Manual test to debug failed CCDA exports.")
  @Test
  public void testFailedCCDAExports() throws Exception {
    System.out.println("Revalidating Failed CCDA Exports...");
    TestHelper.loadTestProperties();
    List<String> validationErrors = new ArrayList<String>();
    List<File> failures = FailedExportHelper.loadFailures("CCDA");
    for (File failure : failures) {
      System.out.println("Validating " + failure.getAbsolutePath() + "...");
      validationErrors.clear();
      String content = new String(Files.readAllBytes(failure.toPath()));
      InputStream inputStream = IOUtils.toInputStream(content, "UTF-8");
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(inputStream);
      sectionExpressions.forEach((section, expression) -> {
        try {
          NodeList nodeList = (NodeList) expression.evaluate(doc, XPathConstants.NODESET);
          if (nodeList.getLength() == 0) {
            validationErrors.add("Unable to find the " + section);
          }
          if (nodeList.getLength() > 1) {
            validationErrors.add("More than one " + section);
          }
        } catch (XPathExpressionException xpe) {
          validationErrors.add("Issue trying to find the " + section + "\n" + xpe.getMessage());
        }
      });
      if (validationErrors.isEmpty()) {
        System.out.println("  No validation errors.");
      } else {
        System.out.println("Validation Errors: " + validationErrors.size());
      }
    }
    System.out.println("Done.");
  }

}
