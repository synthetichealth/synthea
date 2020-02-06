package org.mitre.synthea.engine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.CardiovascularDiseaseModule;
import org.mitre.synthea.modules.EncounterModule;
import org.mitre.synthea.modules.LifecycleModule;
import org.mitre.synthea.modules.QualityOfLifeModule;
import org.mitre.synthea.modules.WeightLossModule;
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

  private static final Configuration JSON_PATH_CONFIG = Configuration.builder()
      .jsonProvider(new GsonJsonProvider())
      .mappingProvider(new GsonMappingProvider())
      .build();

  private static final Map<String, ModuleSupplier> modules = loadModules();
  
  private static Map<String, ModuleSupplier> loadModules() {
    Map<String, ModuleSupplier> retVal = new ConcurrentHashMap<>();
    int submoduleCount = 0;

    retVal.put("Lifecycle", new ModuleSupplier(new LifecycleModule()));
    //retVal.put("Health Insurance", new ModuleSupplier(new HealthInsuranceModule()));
    retVal.put("Cardiovascular Disease", new ModuleSupplier(new CardiovascularDiseaseModule()));
    retVal.put("Quality Of Life", new ModuleSupplier(new QualityOfLifeModule()));
    retVal.put("Weight Loss", new ModuleSupplier(new WeightLossModule()));

    Properties moduleOverrides = getModuleOverrides();

    try {
      URI modulesURI = Module.class.getClassLoader().getResource("modules").toURI();
      fixPathFromJar(modulesURI);
      Path modulesPath = Paths.get(modulesURI);
      submoduleCount = walkModuleTree(modulesPath, retVal, moduleOverrides);
    } catch (Exception e) {
      e.printStackTrace();
    }

    System.out.format("Scanned %d modules and %d submodules.\n", 
                      retVal.size() - submoduleCount, 
                      submoduleCount);

    return retVal;
  }

  private static Properties getModuleOverrides() {
    String moduleOverrideFile = Config.get("module_override");
    Properties overrides = null;
    if (moduleOverrideFile != null && !moduleOverrideFile.trim().equals("")) {
      try {
        overrides = new Properties();
        overrides.load(new FileReader(moduleOverrideFile));
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return overrides;
  }

  private static int walkModuleTree(
          Path modulesPath, Map<String, 
          ModuleSupplier> retVal, 
          Properties overrides)
          throws Exception {
    AtomicInteger submoduleCount = new AtomicInteger();
    Path basePath = modulesPath.getParent();
    Utilities.walkAllModules(modulesPath, t -> {
              String relativePath = relativePath(t, modulesPath);
              boolean submodule = !t.getParent().equals(modulesPath);
              if (submodule) {
                submoduleCount.getAndIncrement();
              }
              retVal.put(relativePath, new ModuleSupplier(submodule,
                  relativePath,
                  () -> loadFile(basePath.relativize(t), submodule, overrides)));
            });
    return submoduleCount.get();
  }

  /**
   * Recursively adds a folder or directory of module files to the static list
   * of modules. This does not need to be executed by the core software. It only
   * is used when the user wants to add another local module folder or during
   * unit tests.
   * @param dir - the folder or directory to add.
   */
  public static void addModules(File dir) {
    int submoduleCount = 0;
    int originalModuleCount = modules.size();
    Properties moduleOverrides = getModuleOverrides();
    try {
      walkModuleTree(dir.toPath(), modules, moduleOverrides);
    } catch (Exception e) {
      e.printStackTrace();
    }

    System.out.format("Scanned %d local modules and %d local submodules.\n", 
                      modules.size() - (originalModuleCount + submoduleCount), 
                      submoduleCount);
  }

  private static void fixPathFromJar(URI uri) throws IOException {
    // this function is a hack to enable reading modules from within a JAR file
    // see https://stackoverflow.com/a/48298758
    if ("jar".equals(uri.getScheme())) {
      for (FileSystemProvider provider: FileSystemProvider.installedProviders()) {
        if (provider.getScheme().equalsIgnoreCase("jar")) {
          try {
            provider.getFileSystem(uri);
          } catch (FileSystemNotFoundException e) {
            // in this case we need to initialize it first:
            provider.newFileSystem(uri, Collections.emptyMap());
          }
        }
      }
    }
  }

  private static String relativePath(Path filePath, Path modulesFolder) {
    String relativeFilePath = modulesFolder.relativize(filePath).toString()
        .replaceFirst(".json", "").replace("\\", "/");
    return relativeFilePath;
  }

  public static Module loadFile(Path path, Path modulesFolder, Properties overrides)
         throws Exception {
    boolean submodule = !path.getParent().equals(modulesFolder);
    return loadFile(path, submodule, overrides);
  }

  private static Module loadFile(Path path, boolean submodule, Properties overrides)
          throws Exception {
    System.out.format("Loading %s %s\n", submodule ? "submodule" : "module", path.toString());
    String jsonString = Utilities.readResource(path.toString());
    if (overrides != null) {
      jsonString = applyOverrides(jsonString, overrides, path.getFileName().toString());
    }
    JsonParser parser = new JsonParser();
    JsonObject object = parser.parse(jsonString).getAsJsonObject();
    return new Module(object, submodule);
  }

  private static String applyOverrides(String jsonString, Properties overrides,
          String moduleFileName) {
    DocumentContext ctx = JsonPath.using(JSON_PATH_CONFIG).parse(jsonString);
    overrides.forEach((key, value) -> {
      // use :: for the separator because filenames cannot contain :
      String[] parts = ((String)key).split("::");
      String module = parts[0];
      if (module.equals(moduleFileName)) {
        String jsonPath = parts[1];
        Double numberValue = Double.parseDouble((String)value);
        ctx.set(jsonPath, numberValue);
      }
    });
    return ctx.jsonString();
  }

  public static String[] getModuleNames() {
    // This will include all known module names, which may be more than are actually loaded.
    return modules.keySet().toArray(new String[modules.size()]);
  }

  /**
   * Get the top-level modules.
   * @return a list of top-level modules. Submodules are not included.
   */
  public static List<Module> getModules() {
    return getModules(p -> true);
  }

  /**
   * Get a list of top-level modules including core and submodules.
   * @return a list of top-level modules, only including core modules and those allowed by the 
   *     supplied predicate. Submodules are loaded, but not included.
   */
  public static List<Module> getModules(Predicate<String> pathPredicate) {
    List<Module> list = new ArrayList<Module>();
    modules.forEach((k, v) -> {
      if (v.submodule) {
        v.get(); // ensure submodules get loaded
      } else if (v.core || pathPredicate.test(v.path)) {
        list.add(v.get());
      }
    });
    return list;
  }

  /**
   * Get a module by path.
   * @param path
   *          : the relative path of the module, without the root or ".json" file extension. For
   *          example, "medications/otc_antihistamine" or "appendicitis".
   * @return module : the given module
   */
  public static Module getModuleByPath(String path) {
    ModuleSupplier supplier = modules.get(path);
    return supplier == null ? null : supplier.get();
  }

  public String name;
  public boolean submodule;
  public List<String> remarks;
  private Map<String, State> states;

  protected Module() {
    // no-args constructor only allowed to be used by subclasses
  }

  /**
   * Create a new Module.
   * @param definition JSON definition of the module.
   * @param submodule Whether or not this is a shared or reusable submodule.
   * @throws Exception when an error occurs inflating the module.
   */
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
    if (!person.alive(time)) {
      return true;
    }
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
        // stop if the patient died in the meantime...
        if (!person.alive(exited)) {
          return true;
        }
        // This must be a delay state that expired between cycles, so temporarily rewind time
        if (process(person, exited)) {
          // if the patient died during the delay, stop
          return true;
        }
        current = person.history.get(0);
      }
    }
    person.attributes.remove(activeKey);
    return (current instanceof State.Terminal);
  }

  private State initialState() {
    return states.get("Initial"); // all Initial states have name Initial
  }

  /**
   * Get a state by name.
   * @param name - case-sensitive state name.
   * @return State if it exists, otherwise null.
   */
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

  /**
   * ModuleSupplier allows for lazy loading of Modules.
   */
  public static class ModuleSupplier implements Supplier<Module> {

    public final boolean core;
    public final boolean submodule;
    public final String path;

    private boolean loaded;
    private Callable<Module> loader;
    private Module module;
    private Throwable fault;

    /**
     * Create a ModuleSupplier.
     * @param submodule Whether or not this is a reusable or shared submodule.
     * @param path The file path of the module being supplied.
     * @param loader The loader that will lazily supply the module on demand.
     */
    public ModuleSupplier(boolean submodule, String path, Callable<Module> loader) {
      this.core = false;
      this.submodule = submodule;
      this.path = Objects.requireNonNull(path);
      this.loader = Objects.requireNonNull(loader);
      loaded = false;
      module = null;
    }

    /**
     * Constructs a Module supplier around a singleton Module instance.
     * @param module The singleton Module instance.
     */
    public ModuleSupplier(Module module) {
      this.core = true;
      this.submodule = module.submodule;
      this.path = "core/" + module.name;
      this.module = Objects.requireNonNull(module);
      loaded = true;
      loader = null;
    }

    @Override
    public synchronized Module get() {
      if (!loaded) {
        try {
          module = loader.call();
        } catch (Throwable e) {
          e.printStackTrace();
          fault = e;
        } finally {
          loaded = true;
          loader = null;
        }
      }
      if (fault != null) {
        throw new RuntimeException(fault);
      }
      return module;
    }
  }
}
