package org.mitre.synthea.engine;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class ValidationTest {

  @Test public void testAllModules() {
    List<String> messages = new ArrayList<>();
    for (String moduleName : Module.getModuleNames()) {
      Module module = Module.getModuleByPath(moduleName);
      
      messages.addAll(module.validate());
    }
    
    if (!messages.isEmpty()) {
      String errorMessage = "Modules failed to validate (" + messages.size() + " errors): \n"
          + String.join("\n\n", messages);
      fail(errorMessage);
    }
  }
    
  @Test public void testValidValues() {
    
  }
  
  @Test public void testIgnore() {
    
  }
  
  @Test public void testRequired() {
    
  }
  
  @Test public void testMinMax() {
    
  }
  
  @Test public void testReferenceToStateType() {
    
  }
  
}
