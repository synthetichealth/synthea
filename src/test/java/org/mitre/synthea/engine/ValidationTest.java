package org.mitre.synthea.engine;

import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

public class ValidationTest {

  @Test public void testAllModules() {
    for (String moduleName : Module.getModuleNames()) {
      Module module = Module.getModuleByPath(moduleName);
      
      List<String> messages = module.validate();
      
      if (!messages.isEmpty()) {
        String errorMessage = "Modules failed to validate (" + messages.size() + " errors): \n"
            + String.join("\n\n", messages);
        fail(errorMessage);
      }
    }
  }
  
}
