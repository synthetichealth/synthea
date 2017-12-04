package org.mitre.synthea.helpers;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.world.concepts.HealthRecord.Code;

public class ConceptsTest {
  private Table<String,String,String> concepts;
  
  /**
   * Temporary folder for any exported files, guaranteed to be deleted at the end of the test.
   */
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Before
  public void setup() {
    concepts = HashBasedTable.create();
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
    // Examplitis condition
    // Examplitol medication
    // Examplotomy_Encounter
    // Examplotomy procedure
    
    
    Concepts.inventoryModule(concepts, module);
    
    assertEquals(4, concepts.cellSet().size());
    assertEquals("Examplitis", concepts.get("SNOMED-CT", "123"));
    assertEquals("Examplitol", concepts.get("RxNorm", "456"));
    assertEquals("Examplotomy Encounter", concepts.get("SNOMED-CT", "ABC"));
    assertEquals("Examplotomy", concepts.get("SNOMED-CT", "789"));
  }

  @Test
  public void testInventoryState() throws FileNotFoundException {
    Path modulesFolder = Paths.get("src/test/resources/generic");
    Path modulePath = modulesFolder.resolve("condition_onset.json");
    
    JsonReader reader = new JsonReader(new FileReader(modulePath.toString()));
    JsonObject module = new JsonParser().parse(reader).getAsJsonObject();
    JsonObject state = module.getAsJsonObject("states").getAsJsonObject("Appendicitis");
    
    Concepts.inventoryState(concepts, state);
    
    assertEquals(1, concepts.cellSet().size());
    assertEquals("Rupture of appendix", concepts.get("SNOMED-CT", "47693006"));
  }
  
  @Test
  public void testInventoryCodes() {
    List<Code> codes = new ArrayList<Code>();
    
    codes.add(new Code("SNOMED-CT","230690007","Stroke"));
    codes.add(new Code("SNOMED-CT","22298006","Myocardial Infarction"));
    codes.add(new Code("RxNorm","834060","Penicillin V Potassium 250 MG"));
    codes.add(new Code("RxNorm","834060","Penicillin V Potassium 250 MG")); 
    // note duplicate code here!! ex, same code in multiple modules
    
    Concepts.inventoryCodes(concepts, codes);
    
    assertEquals(3, concepts.cellSet().size());
    assertEquals("Stroke", concepts.get("SNOMED-CT", "230690007"));
    assertEquals("Myocardial Infarction", concepts.get("SNOMED-CT", "22298006"));
    assertEquals("Penicillin V Potassium 250 MG", concepts.get("RxNorm", "834060"));
  }
}
