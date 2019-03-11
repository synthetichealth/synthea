package org.mitre.synthea.helpers;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import guru.nidi.graphviz.attribute.Shape;
import guru.nidi.graphviz.attribute.Style;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.engine.Rasterizer;
import guru.nidi.graphviz.model.Factory;
import guru.nidi.graphviz.model.Graph;
import guru.nidi.graphviz.model.Link;
import guru.nidi.graphviz.model.Node;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.mitre.synthea.modules.CardiovascularDiseaseModule;
import org.mitre.synthea.modules.DeathModule;
import org.mitre.synthea.modules.EncounterModule;
import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.modules.Immunizations;
import org.mitre.synthea.modules.LifecycleModule;
import org.mitre.synthea.modules.QualityOfLifeModule;
import org.mitre.synthea.world.agents.Person;

/**
 * Task class to export a report of all Person attributes
 * that Synthea is aware of. Does not include Clinician or
 * Provider specific attributes.
 */
public class Attributes {
  
  public class Inventory {
    /** Key: Module Name, Values: State names that read this attribute. */
    public Map<String,Set<String>> read;
    /** Key: Module Name, Values: State names that write to this attribute. */
    public Map<String,Set<String>> write;
    /** List of example values as strings. */
    public Set<String> exampleValues;

    /**
     * Create a new Inventory object with instantiated read, write, and exampleValues collections.
     */
    public Inventory() {
      this.read = new TreeMap<String,Set<String>>();
      this.write = new TreeMap<String,Set<String>>();
      this.exampleValues = new TreeSet<String>();
    }

    /**
     * Mark that the given "module" reads this attribute from the given "state".
     * @param module The reading module.
     * @param state The reading state.
     */
    public void read(String module, String state) {
      Set<String> states = this.read.computeIfAbsent(module, f -> new TreeSet<String>());
      if (state != null) {
        states.add(state);        
      }
    }

    /**
     * Mark that the given "module" writes this attribute from the given "state".
     * Non-null example values are recorded as examples.
     *
     * @param module The writing module.
     * @param state The writing state.
     * @param example A single example value as a string.
     */
    public void write(String module, String state, String example) {
      Set<String> states = this.write.computeIfAbsent(module, f -> new TreeSet<String>());
      if (state != null) {
        states.add(state);
      }
      if (example != null) {
        exampleValues.add(example);
      }
    }
  }

  /**
   * Generate an output file containing all Person attributes used in Synthea.
   * Attributes of Clinicians and Providers are not included.
   * 
   * @param args unused
   * @throws Exception if any error occurs in reading the module files
   */
  public static void main(String[] args) throws Exception {
    System.out.println("Performing an inventory of attributes into `output/attributes.json`...");
    
    Map<String,Inventory> output = getAttributeInventory();
    
    String outFilePath = new File("./output/attributes.json").toPath().toString();
    Writer writer = new FileWriter(outFilePath);
    Gson gson = new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .setPrettyPrinting().create();
    gson.toJson(output, writer);
    writer.flush();
    writer.close();
    
    graph(output, "attributes_all", false);
    graph(output, "attributes_readwrite", true);
    
    System.out.println("Catalogued " + output.size() + " attributes.");
    System.out.println("Done.");
  }
  
  /**
   * Get the list of all Person attributes in Synthea, as a list of CSV strings.
   * @return list of CSV strings
   * @throws Exception if any exception occurs in reading the modules.
   */
  public static Map<String,Inventory> getAttributeInventory() throws Exception {
    Map<String,Inventory> attributes = new TreeMap<String,Inventory>();

    URL modulesFolder = ClassLoader.getSystemClassLoader().getResource("modules");
    Path path = Paths.get(modulesFolder.toURI());
    Files.walk(path).filter(Files::isReadable).filter(Files::isRegularFile)
        .filter(f -> f.toString().endsWith(".json")).forEach(modulePath -> {
          try (JsonReader reader = new JsonReader(new FileReader(modulePath.toString()))) {
            JsonObject module = new JsonParser().parse(reader).getAsJsonObject();
            inventoryModule(attributes, module);
          } catch (IOException e) {
            throw new RuntimeException("Unable to read modules", e);
          }
        });

    CardiovascularDiseaseModule.inventoryAttributes(attributes);
    DeathModule.inventoryAttributes(attributes);
    EncounterModule.inventoryAttributes(attributes);
    HealthInsuranceModule.inventoryAttributes(attributes);
    Immunizations.inventoryAttributes(attributes);
    LifecycleModule.inventoryAttributes(attributes);
    QualityOfLifeModule.inventoryAttributes(attributes);
    
    return attributes;
  }
  
  /**
   * Catalog all attributes from the given module into the given Table.
   * 
   * @param attributes Table of attributes to add to
   * @param module Module to parse for attributes and codes
   */
  private static void inventoryModule(Map<String,Inventory> attributes, JsonObject module) {
    String moduleName = module.get("name").getAsString();
    JsonObject states = module.get("states").getAsJsonObject();
    Set<String> stateNames = new HashSet<String>();
    for (Entry<String, JsonElement> entry : states.entrySet()) {
      stateNames.add(entry.getKey());
    }
    for (Entry<String, JsonElement> entry : states.entrySet()) {
      String stateName = entry.getKey();
      JsonObject state = entry.getValue().getAsJsonObject();
      inventoryState(attributes, moduleName, stateName, state, stateNames);
    }
  }
  
  /**
   * Catalog all attributes from the given state into the given Table.
   * 
   * @param attributes Table of attributes to add to
   * @param moduleName The name of the module.
   * @param stateName The name of the state.
   * @param state State to parse for attributes and codes
   * @param stateNames Set of state names in the current module.
   */
  private static void inventoryState(Map<String,Inventory> attributes, String moduleName,
      String stateName, JsonObject state, Set<String> stateNames) {

    String type = state.get("type").getAsString();

    if (state.has("reason")) {
      String reason = state.get("reason").getAsString();
      if (!reason.isEmpty()) {
        if (stateNames.contains(reason)) {
          // reason is a prior state -- ignore, prior state is not an attribute.
        } else {
          // reason is another attribute
          Inventory data = attributes.computeIfAbsent(reason,
              f -> new Attributes().new Inventory());
          data.read(moduleName, stateName);    
        }        
      }
    }

    if (state.has("assign_to_attribute")) {
      String attribute = state.get("assign_to_attribute").getAsString();
      if (!attribute.isEmpty()) {
        Inventory data = attributes.computeIfAbsent(attribute,
            f -> new Attributes().new Inventory());
        data.write(moduleName, stateName, type);        
      }
    }

    if (state.has("referenced_by_attribute")) {
      String attribute = state.get("referenced_by_attribute").getAsString();
      if (!attribute.isEmpty()) {
        Inventory data = attributes.computeIfAbsent(attribute,
            f -> new Attributes().new Inventory());
        data.read(moduleName, stateName);        
      }
    }

    if (state.has("attribute")) {
      String attribute = state.get("attribute").getAsString();
      if (!attribute.isEmpty()) {
        Inventory data = attributes.computeIfAbsent(attribute,
            f -> new Attributes().new Inventory());
        if (type.equalsIgnoreCase("SetAttribute")) {
          String value = null;
          try {
            value = state.get("value").getAsJsonPrimitive().toString();
          } catch (Exception e) {
            // Missing value. Do nothing, this attribute is basically a :symbol
          }
          data.write(moduleName, stateName, value);
        } else if (type.equalsIgnoreCase("Counter")) {
          data.write(moduleName, stateName, "Integer");
        } else if (type.equalsIgnoreCase("Observation")) {
          data.read(moduleName, stateName);
        } else {
          System.out.println("Unhandled State: " + type);
        }        
      }
    }

    inventoryTransition(attributes, moduleName, stateName, state);
  }

  /**
   * Catalog all attributes from the given state transition into the given Table.
   *
   * @param attributes Table of attributes to add to
   * @param moduleName The name of the module.
   * @param stateName The name of the state.
   * @param state State to parse for attributes and codes
   */
  private static void inventoryTransition(Map<String,Inventory> attributes, String moduleName,
      String stateName, JsonObject state) {

    if (state.has("distributed_transition")) {
      // TODO
    } else if (state.has("conditional_transition")) {
      JsonArray transitions = state.get("conditional_transition").getAsJsonArray();
      for (JsonElement element : transitions) {
        JsonObject transition = (JsonObject) element;
        if (transition.has("condition")) {
          JsonObject condition = transition.get("condition").getAsJsonObject();
          inventoryLogic(attributes, moduleName, stateName, condition);
        }
      }
    } else if (state.has("complex_transition")) {
      JsonArray transitions = state.get("complex_transition").getAsJsonArray();
      for (JsonElement element : transitions) {
        JsonObject transition = (JsonObject) element;
        if (transition.has("condition")) {
          JsonObject condition = transition.get("condition").getAsJsonObject();
          inventoryLogic(attributes, moduleName, stateName, condition);
        }
      }
    }
  }

  /**
   * Catalog all attributes from the given logic statement into the given Table.
   *
   * @param attributes Table of attributes to add to
   * @param moduleName The name of the module.
   * @param stateName The name of the state.
   * @param logic Logic to parse for attributes
   */
  private static void inventoryLogic(Map<String,Inventory> attributes, String moduleName,
      String stateName, JsonObject logic) {

    String type = logic.get("condition_type").getAsString();

    if (type.equalsIgnoreCase("Attribute")) {
      String attribute = logic.get("attribute").getAsString();
      if (!attribute.isEmpty()) {
        Inventory data = attributes.computeIfAbsent(attribute,
            f -> new Attributes().new Inventory());
        data.read(moduleName, stateName);
      }
    } else if (type.equalsIgnoreCase("Age")) {
      Inventory data = attributes.computeIfAbsent(Person.BIRTHDATE,
          f -> new Attributes().new Inventory());
      data.read(moduleName, stateName);
    } else if (type.equalsIgnoreCase("Gender")) {
      Inventory data = attributes.computeIfAbsent(Person.GENDER,
          f -> new Attributes().new Inventory());
      data.read(moduleName, stateName);
    } else if (type.equalsIgnoreCase("Socioeconomic Status")) {
      Inventory data = attributes.computeIfAbsent(Person.SOCIOECONOMIC_CATEGORY,
          f -> new Attributes().new Inventory());
      data.read(moduleName, stateName);
    } else if (type.equalsIgnoreCase("Race")) {
      Inventory data = attributes.computeIfAbsent(Person.RACE,
          f -> new Attributes().new Inventory());
      data.read(moduleName, stateName);
    } else if (type.equalsIgnoreCase("Symptom")) {
      String attribute = logic.get("symptom").getAsString();
      if (!attribute.isEmpty()) {
        Inventory data = attributes.computeIfAbsent(attribute,
            f -> new Attributes().new Inventory());
        data.read(moduleName, stateName);
      }
    } else if (type.equalsIgnoreCase("Vital Sign")) {
      // Vital signs are not truly attributes, but we want to included them anyway
      String attribute = logic.get("vital_sign").getAsString();
      if (!attribute.isEmpty()) {
        Inventory data = attributes.computeIfAbsent(attribute,
            f -> new Attributes().new Inventory());
        data.read(moduleName, stateName);
      }
    } else if (type.equalsIgnoreCase("Observation") ||
        type.equalsIgnoreCase("Active Condition") ||
        type.equalsIgnoreCase("Active Medication") ||
        type.equalsIgnoreCase("Active CarePlan")) {
      if (logic.has("referenced_by_attribute")) {
        String attribute = logic.get("referenced_by_attribute").getAsString();
        if (!attribute.isEmpty()) {
          Inventory data = attributes.computeIfAbsent(attribute,
              f -> new Attributes().new Inventory());
          data.read(moduleName, stateName);
        }
      }
    } else if (type.equalsIgnoreCase("And") ||
        type.equalsIgnoreCase("Or") ||
        type.equalsIgnoreCase("Not") ||
        type.equalsIgnoreCase("AtLeast") ||
        type.equalsIgnoreCase("At Most")) {
      if (logic.has("conditions")) {
        JsonArray conditions = logic.get("conditions").getAsJsonArray();
        for (JsonElement element : conditions) {
          JsonObject condition = (JsonObject) element;
          inventoryLogic(attributes, moduleName, stateName, condition);
        }
      }
    }
  }

  /**
   * Inventory an attribute with read, write, and example values.
   * @param attributes The inventory.
   * @param module The module name.
   * @param attribute The attribute name.
   * @param read Whether the module reads the attribute or not.
   * @param write Whether the module writes to the attribute or not.
   * @param example Example values the module writes.
   */
  public static void inventory(Map<String,Inventory> attributes,
      String module, String attribute, boolean read, boolean write, String example) {
    Inventory data = attributes.computeIfAbsent(attribute, f -> new Attributes().new Inventory());
    if (read) {
      data.read(module, null);
    }
    if (write) {
      data.write(module, null, example);
    }
  }

  /**
   * Output a graphviz image of the inventoried attributes.
   *
   * @param output The results of getAttributeInventory()
   * @param filename The filename of the image (without a path or extension) to write.
   * @param readAndWrite If false, all nodes are rendered. If true, only attributes that are
   *     both read from and written to are considered.
   */
  public static void graph(Map<String,Inventory> output, String filename, boolean readAndWrite) {
    Map<String,Node> modules = new TreeMap<String,Node>();
    Map<String,List<Link>> writeLinks = new TreeMap<String,List<Link>>();
    
    Graph graph = Factory.graph().directed();
    
    for (String attribute : output.keySet()) {
      Inventory inventory = output.get(attribute);
      if (readAndWrite && (inventory.read.isEmpty() || inventory.write.isEmpty())) {
        continue;
      }
      Node attributeNode = Factory.node(attribute).with(Shape.RECTANGLE);

      for (String module : inventory.read.keySet()) {
        String key = module + "\nModule";
        Node moduleNode = null;
        if (modules.containsKey(key)) {
          moduleNode = modules.get(key);
        } else {
          moduleNode = Factory.node(key);
          modules.put(key, moduleNode);
        }
        attributeNode = attributeNode.link(Factory.to(moduleNode).with(Style.DASHED));
      }
      for (String module : inventory.write.keySet()) {
        String key = module + "\nModule";
        Node moduleNode = null;
        if (modules.containsKey(key)) {
          moduleNode = modules.get(key);
        } else {
          moduleNode = Factory.node(key);
          modules.put(key, moduleNode);
        }
        
        List<Link> moduleWriteLinks = null;
        if (writeLinks.containsKey(key)) {
          moduleWriteLinks = writeLinks.get(key);
        } else {
          moduleWriteLinks = new ArrayList<Link>();
          writeLinks.put(key, moduleWriteLinks);
        }
        Link link = Factory.to(attributeNode);
        moduleWriteLinks.add(link);
      }
      graph = graph.with(attributeNode);
    }
    
    for (String key : modules.keySet()) {
      Node node = modules.get(key);
      List<Link> moduleWriteLinks = writeLinks.get(key);
      if (moduleWriteLinks != null && !moduleWriteLinks.isEmpty()) {
        graph = graph.with(node.link(moduleWriteLinks.toArray(new Link[0])));        
      } else {
        graph = graph.with(node);
      }
    }
    
    File graphFile = new File("./output/" + filename + ".png");
    try {
      Graphviz.fromGraph(graph).rasterizer(Rasterizer.BATIK).render(Format.PNG).toFile(graphFile);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
