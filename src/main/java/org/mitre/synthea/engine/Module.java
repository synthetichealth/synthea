package org.mitre.synthea.engine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;

import org.mitre.synthea.engine.State.Terminal;
import org.mitre.synthea.modules.CardiovascularDiseaseModule;
import org.mitre.synthea.modules.EncounterModule;
import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.modules.LifecycleModule;
import org.mitre.synthea.modules.QualityOfLifeModule;
import org.mitre.synthea.world.agents.Person;

/**
 * Module represents the entry point of a generic module.
 * 
 * <p>The `modules` map is the static list of generic modules. It is loaded once per process, 
 * and the list of modules is shared between the generated population. Because we share modules 
 * across the population, it is important that States are cloned before they are executed. 
 * This keeps the "master" copy of the module clean.
 */
public class Module {

  private static final Map<String, Module> modules = loadModules();

  private static Map<String, Module> loadModules() {
    Map<String, Module> retVal = new ConcurrentHashMap<String, Module>();

    retVal.put("Lifecycle", new LifecycleModule());
    retVal.put("Cardiovascular Disease", new CardiovascularDiseaseModule());
    retVal.put("Quality Of Life", new QualityOfLifeModule());
    retVal.put("Health Insurance", new HealthInsuranceModule());

    try {
      URL modulesFolder = ClassLoader.getSystemClassLoader().getResource("modules");
      Path path = Paths.get(modulesFolder.toURI());
      Files.walk(path, Integer.MAX_VALUE).filter(Files::isReadable).filter(Files::isRegularFile)
          .filter(p -> p.toString().endsWith(".json")).forEach(t -> {
            try {
              Module module = loadFile(t, path);
              String relativePath = relativePath(t, path);
              retVal.put(relativePath, module);
            } catch (Exception e) {
              e.printStackTrace();
              throw new RuntimeException(e);
            }
          });
    } catch (Exception e) {
      e.printStackTrace();
    }

    System.out.format("Loaded %d modules.\n", retVal.size());

    return retVal;
  }

  private static String relativePath(Path filePath, Path modulesFolder) {
    String folderString = Matcher.quoteReplacement(modulesFolder.toString() + File.separator);
    return filePath.toString().replaceFirst(folderString, "").replaceFirst(".json", "")
        .replace("\\", "/");
  }

  public static Module loadFile(Path path, Path modulesFolder) throws Exception {
    System.out.format("Loading %s\n", path.toString());
    boolean submodule = !path.getParent().equals(modulesFolder);
    JsonObject object = null;
    FileReader fileReader = null;
    JsonReader reader = null;
    fileReader = new FileReader(path.toString());
    reader = new JsonReader(fileReader);
    JsonParser parser = new JsonParser();
    object = parser.parse(reader).getAsJsonObject();
    fileReader.close();
    reader.close();
    return new Module(object, submodule);
  }

  public static String[] getModuleNames() {
    return modules.keySet().toArray(new String[modules.size()]);
  }

  /**
   * @return a list of top-level modules. Submodules are not included.
   */
  public static List<Module> getModules() {
    List<Module> list = new ArrayList<Module>();
    modules.forEach((k, v) -> {
      if (!v.submodule) {
        list.add(v);
      }
    });
    return list;
  }

  /**
   * @param path
   *          : the relative path of the module, without the root or ".json" file extension. For
   *          example, "medications/otc_antihistamine" or "appendicitis".
   * @return module : the given module
   */
  public static Module getModuleByPath(String path) {
    return modules.get(path);
  }

  public String name;
  public boolean submodule;
  public List<String> remarks;
  private Map<String, State> states;

  protected Module() {
    // no-args constructor only allowed to be used by subclasses
  }

  public Module(JsonObject definition, boolean submodule) throws Exception {
    name = String.format("%s Module", definition.get("name").getAsString());
    this.submodule = submodule;
    remarks = new ArrayList<String>();
    if (definition.has("remarks")) {
      JsonElement jsonRemarks = definition.get("remarks");
      for (JsonElement value : jsonRemarks.getAsJsonArray()) {
        remarks.add(value.getAsString());
      }
    }

    JsonObject jsonStates = definition.get("states").getAsJsonObject();
    states = new ConcurrentHashMap<String, State>();
    for (Entry<String, JsonElement> entry : jsonStates.entrySet()) {
      State state = State.build(this, entry.getKey(), entry.getValue().getAsJsonObject());
      states.put(entry.getKey(), state);
    }
  }

  /**
   * Process this Module with the given Person at the specified time within the simulation.
   * 
   * @param person
   *          : the person being simulated
   * @param time
   *          : the date within the simulated world
   * @return completed : whether or not this Module completed.
   */
  @SuppressWarnings("unchecked")
  public boolean process(Person person, long time) {
    person.history = null;
    // what current state is this person in?
    if (!person.attributes.containsKey(this.name)) {
      person.history = new LinkedList<State>();
      person.history.add(initialState());
      person.attributes.put(this.name, person.history);
    }
    person.history = (List<State>) person.attributes.get(this.name);
    String activeKey = EncounterModule.ACTIVE_WELLNESS_ENCOUNTER + " " + this.name;
    if (person.attributes.containsKey(EncounterModule.ACTIVE_WELLNESS_ENCOUNTER)) {
      person.attributes.put(activeKey, true);
    }
    State current = person.history.get(0);
    // System.out.println(" Resuming at " + current.name);
    // process the current state,
    // looping until module is finished,
    // probably more than one state
    String nextStateName = null;
    while (current.run(person, time)) {
      Long exited = current.exited;      
      nextStateName = current.transition(person, time);
      // System.out.println(" Transitioning to " + nextStateName);
      current = states.get(nextStateName).clone(); // clone the state so we don't dirty the original
      person.history.add(0, current);
      if (exited != null && exited < time) {
        // This must be a delay state that expired between cycles, so temporarily rewind time
        process(person, exited);
        current = person.history.get(0);
      }
    }
    person.attributes.remove(activeKey);
    return (current instanceof State.Terminal);
  }

  private State initialState() {
    return states.get("Initial"); // all Initial states have name Initial
  }

  public State getState(String name) {
    return states.get(name);
  }

  /**
   * Get a collection of the names of all the states this Module contains.
   * 
   * @return set of all state names, or empty set if this is a non-GMF module
   */
  public Collection<String> getStateNames() {
    if (states == null) {
      // ex, if this is a non-GMF module
      return Collections.emptySet();
    }
    return states.keySet();
  }
  
  public List<String> validate() {
    if (states == null) {
      // ex, if this is a non-GMF module
      return Collections.emptyList();
    }
    
    List<String> messages = new LinkedList<>();
    
    Set<String> reachable = new HashSet<>();
    reachable.add("Initial");
    
    states.forEach((stName, state) -> {
      
      messages.addAll(state.validate(this, Collections.emptyList()));
      
      
      Transition transition = state.getTransition();
      if (transition == null) {
        if (!(state instanceof Terminal)) {
          messages.add(this.name + ": State `" + state + "` has no transition.\n");
        }
      } else {
        reachable.addAll(transition.getAllTransitions());
      }
    });
    
    Set<String> unreachable = new HashSet<String>(states.keySet());
    unreachable.removeAll(reachable);
    
    unreachable.forEach(st -> messages.add(this.name + ": State " + st + " is unreachable"));
    
    return messages;
  }
}
