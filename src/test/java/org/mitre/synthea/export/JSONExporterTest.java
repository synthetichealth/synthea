package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.ParallelTestingService;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

public class JSONExporterTest {
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void export() throws Exception {
    TestHelper.loadTestProperties();
    List<String> errors = ParallelTestingService.runInParallel((person) -> {
      List<String> validationErrors = new ArrayList<>();
      TestHelper.exportOff();
      Config.set("exporter.json.export", "true");
      boolean moduleExport = person.randBoolean();
      Config.set("exporter.json.include_module_history", String.valueOf(moduleExport));
      String personJson = JSONExporter.export(person);
      assertNotNull(personJson);
      JsonElement parsedPerson = JsonParser.parseString(personJson);
      JsonObject attributes = parsedPerson.getAsJsonObject()
          .get("attributes").getAsJsonObject();
      assertTrue(parsedPerson.getAsJsonObject().has("symptoms"));
      String gender = attributes.get("gender").getAsString();
      assertEquals(person.attributes.get(Person.GENDER), gender);
      if (moduleExport) {
        attributes.keySet().forEach((attributeName) -> {
          if (!attributeName.startsWith("active_wellness_encounter")
              && attributeName.endsWith("Module")) {
            Object obj = attributes.get(attributeName);
            attributes.get(attributeName).getAsJsonArray().forEach((stateElement) -> {
              if (stateElement.getAsJsonObject().get("state_name") == null) {
                validationErrors.add(String.format("Module %s does not have a state name in "
                        + "exported module history", attributeName));
              }
              if (stateElement.getAsJsonObject().get("entered") == null) {
                validationErrors.add(String.format("Module %s does not have an entered time in "
                        + "exported module history", attributeName));
              }
            });
          }
        });
      }
      return validationErrors;
    });
    assertTrue("Validation of exported JSON bundle failed: "
        + String.join("|", errors), errors.size() == 0);
  }
}