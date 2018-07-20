package org.mitre.synthea.helpers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.mitre.synthea.modules.CardiovascularDiseaseModule;
import org.mitre.synthea.modules.DeathModule;
import org.mitre.synthea.modules.EncounterModule;
import org.mitre.synthea.modules.Immunizations;
import org.mitre.synthea.modules.LifecycleModule;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.Terminology;

/**
 * Task class to export a report of all clinical concepts
 * (aka Codes) that Synthea is aware of.
 * Format is "system,code,display".
 */
public class Concepts {
  /**
   * Generate an output file containing all clinical concepts used in Synthea.
   * 
   * @param args unused
   * @throws Exception if any error occurs in reading the module files
   */
  public static void main(String[] args) throws Exception {
    System.out.println("Performing an inventory of concepts into `output/concepts.csv`...");
    
    List<String> output = getConceptInventory();
    
    Path outFilePath = new File("./output/concepts.csv").toPath();
    
    Files.write(outFilePath, output, StandardOpenOption.CREATE);
    
    System.out.println("Catalogued " + output.size() + " concepts.");
    System.out.println("Done.");
  }
  
  /**
   * Get the list of all concepts in Synthea, as a list of CSV strings.
   * @return list of CSV strings
   * @throws Exception if any exception occurs in reading the modules.
   */
  public static List<String> getConceptInventory() throws Exception {
    Map<Code,Set<String>> concepts = new TreeMap<Code,Set<String>>();

    URL modulesFolder = ClassLoader.getSystemClassLoader().getResource("modules");
    Path path = Paths.get(modulesFolder.toURI());
    Files.walk(path).filter(Files::isReadable).filter(Files::isRegularFile)
        .filter(f -> f.toString().endsWith(".json")).forEach(modulePath -> {
          try (JsonReader reader = new JsonReader(new FileReader(modulePath.toString()))) {
            JsonObject module = new JsonParser().parse(reader).getAsJsonObject();
            inventoryModule(concepts, module);
          } catch (IOException e) {
            throw new RuntimeException("Unable to read modules", e);
          }
        });

    inventoryCodes(concepts, CardiovascularDiseaseModule.getAllCodes(),
        CardiovascularDiseaseModule.class.getSimpleName());
    inventoryCodes(concepts, DeathModule.getAllCodes(), DeathModule.class.getSimpleName());
    inventoryCodes(concepts, EncounterModule.getAllCodes(), EncounterModule.class.getSimpleName());
    // HealthInsuranceModule has no codes
    inventoryCodes(concepts, Immunizations.getAllCodes(), Immunizations.class.getSimpleName());
    inventoryCodes(concepts, LifecycleModule.getAllCodes(), LifecycleModule.class.getSimpleName());
    // QualityOfLifeModule adds no new codes to patients
    
    List<String> conceptList = new ArrayList<>();
    
    for (Code code : concepts.keySet()) {
      Set<String> modules = concepts.get(code);
      String display = code.display;
      display = display.replaceAll("\\r\\n|\\r|\\n|,", " ").trim();
      String mods = modules.toString().replaceAll("\\[|\\]", "").replace(", ", "|").trim();
      String concept = code.system + ',' + code.code + ',' + display + ',' + mods;
      conceptList.add(concept);
    }
    
    return conceptList;
  }
  
  /**
   * Catalog all concepts from the given module into the given Table.
   * 
   * @param concepts Table of concepts to add to
   * @param module Module to parse for concepts and codes
   */
  public static void inventoryModule(Map<Code,Set<String>> concepts, JsonObject module) {
    JsonObject states = module.get("states").getAsJsonObject();
    for (Entry<String, JsonElement> entry : states.entrySet()) {
      JsonObject state = entry.getValue().getAsJsonObject();
      inventoryState(concepts, state, module.get("name").getAsString());
    }
  }
  
  /**
   * Catalog all concepts from the given state into the given Table.
   * 
   * @param concepts Table of concepts to add to
   * @param state State to parse for concepts and codes
   */
  public static void inventoryState(Map<Code,Set<String>> concepts, JsonObject state,
      String module) {

    // TODO - how can we make this more generic
    // and not have to remember to update this if we add new codes in another field?
    if (state.has("valueSet")) {
      Terminology.Session s = new Terminology.Session();
      List<Code> codes = s.getAllCodes(state.get("valueSet").getAsString());
      inventoryCodes(concepts,codes,module);
    }
    if (state.has("codes")) {
      List<Code> codes = Code.fromJson(state.getAsJsonArray("codes"));
      inventoryCodes(concepts, codes, module);
    }

    if (state.has("activities")) {
      List<Code> codes = Code.fromJson(state.getAsJsonArray("activities"));
      inventoryCodes(concepts, codes, module);
    }

    if (state.has("prescription")) {
      JsonObject prescription = state.getAsJsonObject("prescription");
      if (prescription.has("instructions")) {
        List<Code> codes = Code.fromJson(prescription.getAsJsonArray("instructions"));
        inventoryCodes(concepts, codes, module);
      }
    }

    if (state.has("discharge_disposition")) {
      Code code = new Code(state.getAsJsonObject("discharge_disposition"));
      inventoryCodes(concepts, Collections.singleton(code), module);
    }
  }
  
  /**
   * Add the Codes in the given Collection to the given inventory of concepts.
   * @param concepts Table of concepts to add to
   * @param codes Collection of codes to add
   */
  public static void inventoryCodes(Map<Code,Set<String>> concepts,
      Collection<Code> codes, String module) {
    codes.forEach(code -> {
      if (!concepts.containsKey(code)) {
        concepts.put(code, new HashSet<String>());
      }
      concepts.get(code).add(module);
    });
  }
}
