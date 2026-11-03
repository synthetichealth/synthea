package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonParser;

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
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
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

  @Test
  public void testCarePlanGoalsAndActivities() throws Exception {
    PayerManager.clear();
    PayerManager.loadPayers(new Location(Generator.DEFAULT_STATE, null));
    TestHelper.loadTestProperties();
    TestHelper.exportOff();
    Config.set("exporter.ccda.export", "true");
    Person person = TestHelper.getGeneratedPeople()[0];
    Encounter encounter = person.record.encounters.get(person.record.encounters.size() - 1);
    CarePlan carePlan = person.record.new CarePlan(encounter.start, "999999001");
    carePlan.codes.add(new Code("SNOMED-CT", "999999001", "Synthetic test care plan"));
    carePlan.activities.add(new Code("SNOMED-CT", "160670007", "Diabetic diet"));
    carePlan.activities.add(new Code("SNOMED-CT", "229065009", "Exercise therapy"));
    carePlan.goals.add(JsonParser.parseString(
        "{\"text\": \"Maintain blood pressure below 140/90 mm[Hg]\"}").getAsJsonObject());
    carePlan.goals.add(JsonParser.parseString("{\"observation\": {\"codes\": [{"
        + "\"system\": \"LOINC\", \"code\": \"4548-4\", "
        + "\"display\": \"Hemoglobin A1c/Hemoglobin.total in Blood\"}], "
        + "\"operator\": \"<\", \"value\": 7}}").getAsJsonObject());
    encounter.careplans.add(carePlan);

    String ccdaXml = CCDAExporter.export(person, encounter.start + 1000L);
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    Document doc = factory.newDocumentBuilder()
        .parse(IOUtils.toInputStream(ccdaXml, "UTF-8"));
    XPath xpath = XPathFactory.newInstance().newXPath();
    String section = "/ClinicalDocument/component/structuredBody/component/section"
        + "[templateId/@root=\"2.16.840.1.113883.10.20.22.2.10\"]";
    String plannedAct = section + "/entry/act[templateId/@root=\"2.16.840.1.113883.10.20.22.4.39\""
        + " and code/@code=\"999999001\"]";

    NodeList plannedActs = (NodeList) xpath.evaluate(plannedAct, doc, XPathConstants.NODESET);
    assertEquals(1, plannedActs.getLength());

    NodeList procedures = (NodeList) xpath.evaluate(plannedAct + "/entryRelationship"
        + "/procedure[templateId/@root=\"2.16.840.1.113883.10.20.22.4.41\"]/code/@code",
        doc, XPathConstants.NODESET);
    assertEquals(2, procedures.getLength());
    assertEquals("160670007", procedures.item(0).getNodeValue());
    assertEquals("229065009", procedures.item(1).getNodeValue());

    NodeList goalRefs = (NodeList) xpath.evaluate(plannedAct
        + "/entryRelationship[@typeCode=\"RSON\"]/act/id/@root", doc, XPathConstants.NODESET);
    assertEquals(2, goalRefs.getLength());
    for (int i = 0; i < goalRefs.getLength(); i++) {
      String goalId = goalRefs.item(i).getNodeValue();
      NodeList goal = (NodeList) xpath.evaluate(section + "/entry/observation"
          + "[@moodCode=\"GOL\" and templateId/@root=\"2.16.840.1.113883.10.20.22.4.121\""
          + " and id/@root=\"" + goalId + "\"]", doc, XPathConstants.NODESET);
      assertEquals("Goal Observation " + goalId, 1, goal.getLength());
    }

    String observationGoalCode = xpath.evaluate(section + "/entry/observation[@moodCode=\"GOL\"]"
        + "/code[@code=\"4548-4\"]/@codeSystem", doc);
    assertEquals("2.16.840.1.113883.6.1", observationGoalCode);

    String narrative = xpath.evaluate(section + "/text", doc);
    assertTrue(narrative.contains("Maintain blood pressure below 140/90 mm[Hg]"));
    assertTrue(narrative.contains("Hemoglobin A1c/Hemoglobin.total in Blood < 7"));
    assertTrue(narrative.contains("Exercise therapy"));
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
