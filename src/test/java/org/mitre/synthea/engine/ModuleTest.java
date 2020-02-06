package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.File;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.junit.Test;
import org.mitre.synthea.helpers.Utilities;
import org.powermock.reflect.Whitebox;

public class ModuleTest {

  @Test
  public void getModules() {
    List<Module> allModules = Module.getModules();
    List<Module> someModules = Module.getModules(path -> path.contains("ti"));
    
    assertTrue(allModules.containsAll(someModules));
    assertFalse(someModules.containsAll(allModules));
    assertTrue(allModules.size() > someModules.size());
    assertTrue(someModules.size() > 0);

    assertTrue(allModules.stream().anyMatch(filterOnModuleName("COPD")));
    assertTrue(someModules.stream().anyMatch(filterOnModuleName("Dermatitis")));
    assertFalse(someModules.stream().anyMatch(filterOnModuleName("COPD")));
  }

  @Test
  public void getModuleByPath() {
    Module module = Module.getModuleByPath("copd");
    assertNotNull(module);
    assertEquals("COPD Module", module.name);
  }
  
  @Test
  public void addLocalModules() {
    Module.addModules(new File("src/test/resources/module"));
    List<Module> allModules = Module.getModules();
    allModules = Module.getModules();
    assertTrue(allModules.stream().filter(filterOnModuleName("COPD_TEST")).count() == 1);
  }

  @Test
  public void getModuleByPath_badModule() throws Exception {
    IOException fault = new IOException("Deliberate failure");
    try (FaultyModuleScope ignore = injectFaultIntoModuleLoad("bad_module", fault)) {
      Module.getModuleByPath("bad_module");
      fail("Expected getModuleByPath() to fail with a RuntimeException");
    } catch (RuntimeException e) {
      assertSame(fault, e.getCause());
    }
    Module.getModuleByPath("bad_module"); // should not fail now
  }

  @Test
  public void getModuleByPath_missingModule() {
    Module module = Module.getModuleByPath("missing_module");
    assertNull(module);
  }

  /**
   * Injects a fault into the lazy load of the specified module. A FaultyModuleScope object is 
   * returned for convenience with try-with-resources to ensure the module is put back the way it
   * was before we broke it.
   * @param path The module to inject a fault into. It does not need to presently exist.
   * @param fault The fault to inject.
   * @return A FaultyModuleScope that restores the previous state for the specified module.
   * @throws Exception If something goes terribly wrong.
   */
  private static FaultyModuleScope injectFaultIntoModuleLoad(String path, Exception fault)
      throws Exception {
    Field modulesField = Whitebox.getField(Module.class, "modules");
    modulesField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, Module.ModuleSupplier> modules = 
            (Map<String, Module.ModuleSupplier>)modulesField.get(null);
    
    // Store the old supplier and inject our "broken" one.
    Module.ModuleSupplier originalSupplier = modules.get(path);
    Callable<Module> faultyCallable = () -> {
      throw fault;
    };
    Module.ModuleSupplier faultySupplier = new Module.ModuleSupplier(false, path, faultyCallable);
    modules.put(path, faultySupplier);
    
    // A runnable that safely puts everything back the way it was.
    AtomicBoolean runOnce = new AtomicBoolean();
    return () -> {
      if (runOnce.compareAndSet(false, true)) {
        if (originalSupplier == null) {
          modules.remove(path, faultySupplier);
        } else {
          modules.replace(path, faultySupplier, originalSupplier);
        }
      }
    };
  }
  
  private static Predicate<Module> filterOnModuleName(String partialName) {
    return module -> {
      String name = module.name;
      return name != null && name.contains(partialName);
    };
  }
  
  public interface FaultyModuleScope extends AutoCloseable {
    @Override
    void close();
  }

  /*
   * Test that all only "*Onset" states have a "target_encounter" attribute.
   */
  @Test
  public void targetEncounters() throws Exception {
    Utilities.walkAllModules((modulesFolder, t) -> {
          try {
            FileReader fileReader = new FileReader(t.toString());
            JsonReader reader = new JsonReader(fileReader);
            JsonParser parser = new JsonParser();
            JsonObject object = parser.parse(reader).getAsJsonObject();
            JsonObject states = object.getAsJsonObject("states");
            for (String stateName : states.keySet()) {
              JsonObject state = states.getAsJsonObject(stateName);
              if (state.has("target_encounter")) {
                String type = state.get("type").getAsString();
                if (!type.endsWith("Onset")) {
                  System.err.println(t.toString() + " => " + stateName + "(" + type + ")");
                }
                assertTrue(type.endsWith("Onset"));
              }
            }
          } catch (Exception e) {
            fail(e.getMessage());
          }
        });
  }
}