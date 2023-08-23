package org.mitre.synthea.export;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * This class isn't intended to be a part of the regular test suite. That is why it is marked as
 * ignore. The purpose of this class is to have a place where an exporter can be run on a ton of
 * people until you can find a case where it breaks. This is also set up to run generation, export
 * and validation concurrently, which hopefully helps identify issues more quickly.
 */
@Ignore
public class ExportBreakerTest {
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void runUntilBreaking() throws Exception {
    int numberOfPeople = 10000;
    Generator generator = new Generator(numberOfPeople);
    generator.options.overflow = false;
    TestHelper.loadTestProperties();
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Massachusetts");
    Config.set("exporter.baseDirectory", tempFolder.newFolder().toString());
    // This is currently set up for CCDA testing, but it should be easy to remove this and do FHIR
    // testing or whatever else needs to be run.
    TestHelper.exportOff();
    Config.set("exporter.ccda.export", "true");
    // I picked 6 because it allows me to run tests on my 8 core machine, but have it still be
    // usable for other things.
    ExecutorService service = Executors.newFixedThreadPool(6);
    for (int i = 0; i < numberOfPeople; i++) {
      final int personIndex = i;
      service.submit(() -> {
        try {
          Person p = generator.generatePerson(personIndex,personIndex);
          // Export work goes here
          String ccdaXml = CCDAExporter.export(p, System.currentTimeMillis());
          InputStream inputStream = IOUtils.toInputStream(ccdaXml, "UTF-8");
          // Validation work goes here
          DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
          DocumentBuilder builder = factory.newDocumentBuilder();
          Document doc = builder.parse(inputStream);
          XPathFactory xpathFactory = XPathFactory.newInstance();
          XPath xpath = xpathFactory.newXPath();
          XPathExpression allergiesSection = xpath.compile("/ClinicalDocument/component"
                  + "/structuredBody"
                  + "/component/section/templateId[@root=\"2.16.840.1.113883.10.20.22.2.6.1\"]");
          NodeList nodeList = (NodeList) allergiesSection.evaluate(doc, XPathConstants.NODESET);
          if (nodeList.getLength() != 1) {
            throw new IllegalStateException("Document should have an allergies section");
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });

    }
    service.shutdown();
    service.awaitTermination(1, TimeUnit.HOURS);

  }
}