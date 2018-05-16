package org.mitre.synthea.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.world.concepts.HealthRecord.Code;

public class ConceptsTest {
  private Map<Code,Set<String>> concepts;

  @Before
  public void setup() {
    concepts = new TreeMap<Code,Set<String>>();;
  }
  
  @Test
  public void testConcepts() throws Exception {
    List<String> concepts = Concepts.getConceptInventory();
    // just intended to ensure no exceptions or anything
    // make sure simpleCSV can parse it
    String csv = concepts.stream().collect(Collectors.joining("\n"));
    SimpleCSV.parse(csv);
  }

  @Test
  public void testInventoryModule() throws FileNotFoundException {
    Path modulesFolder = Paths.get("src/test/resources/generic");
    Path modulePath = modulesFolder.resolve("example_module.json");
    
    JsonReader reader = new JsonReader(new FileReader(modulePath.toString()));
    JsonObject module = new JsonParser().parse(reader).getAsJsonObject();
    
    // example_module has 4 codes:
    List<Code> codes = new ArrayList<Code>();
    codes.add(new Code("SNOMED-CT","123","Examplitis"));
    codes.add(new Code("SNOMED-CT","ABC","Examplotomy Encounter"));
    codes.add(new Code("SNOMED-CT","789","Examplotomy"));
    codes.add(new Code("RxNorm","456","Examplitol"));

    Concepts.inventoryModule(concepts, module);

    assertEquals(4, concepts.keySet().size());
    for (Code code : codes) {
      assertTrue(concepts.containsKey(code));
    }
  }

  @Test
  public void testInventoryState() throws FileNotFoundException {
    Path modulesFolder = Paths.get("src/test/resources/generic");
    Path modulePath = modulesFolder.resolve("condition_onset.json");
    
    JsonReader reader = new JsonReader(new FileReader(modulePath.toString()));
    JsonObject module = new JsonParser().parse(reader).getAsJsonObject();
    JsonObject state = module.getAsJsonObject("states").getAsJsonObject("Appendicitis");
    
    Concepts.inventoryState(concepts, state, module.get("name").getAsString());
    
    assertEquals(1, concepts.keySet().size());
    Code code = new Code("SNOMED-CT", "47693006", "Rupture of appendix");
    assertTrue(concepts.containsKey(code));
  }
  
  @Test
  public void testInventoryCodes() {
    List<Code> codes = new ArrayList<Code>();
    
    codes.add(new Code("SNOMED-CT","230690007","Stroke"));
    codes.add(new Code("SNOMED-CT","22298006","Myocardial Infarction"));
    codes.add(new Code("RxNorm","834060","Penicillin V Potassium 250 MG"));
    codes.add(new Code("RxNorm","834060","Penicillin V Potassium 250 MG")); 
    // note duplicate code here!! ex, same code in multiple modules
    
    Concepts.inventoryCodes(concepts, codes, ConceptsTest.class.getSimpleName());
    
    assertEquals(3, concepts.keySet().size());
    for (Code code : codes) {
      assertTrue(concepts.containsKey(code));
    }
  }
}
