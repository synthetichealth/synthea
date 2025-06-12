// src/test/java/org/mitre/synthea/helpers/ModuleOverridesDebugger.java
package org.mitre.synthea.helpers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.jayway.jsonpath.internal.Path;
import com.jayway.jsonpath.internal.path.PathCompiler;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import org.mitre.synthea.helpers.Utilities;

/**
 * Debug script to identify the exact JSONPath expression causing the CI failure.
 * This will help us pinpoint which module file and field name is problematic.
 */
public class ModuleOverridesDebugger {

  public static void main(String[] args) throws Exception {
    System.out.println("=== JSONPath Debug Session ===");
    System.out.println("Searching for problematic JSONPath expressions...\n");
    
    // Test the exclude fields scenario that's failing
    List<String> includeFields = null;
    List<String> includeModules = null;
    List<String> excludeFields = Arrays.asList("distribution");
    List<String> excludeModules = null;
    
    ModuleOverridesDebugger debugger = new ModuleOverridesDebugger();
    debugger.debugModuleOverrides(includeFields, includeModules, excludeFields, excludeModules);
  }
  
  public void debugModuleOverrides(List<String> includeFields, List<String> includeModules,
                                  List<String> excludeFields, List<String> excludeModules) throws Exception {
    
    System.out.println("Configuration:");
    System.out.println("  Include fields: " + includeFields);
    System.out.println("  Exclude fields: " + excludeFields);
    System.out.println("  Include modules: " + includeModules);
    System.out.println("  Exclude modules: " + excludeModules);
    System.out.println();
    
    Utilities.walkAllModules((basePath, modulePath) -> {
      String moduleFilename = basePath.relativize(modulePath).toString();
      System.out.println("Processing module: " + moduleFilename);
      
      try {
        String moduleRelativePath = basePath.getParent().relativize(modulePath).toString();
        JsonReader reader = new JsonReader(new StringReader(
                 Utilities.readResource(moduleRelativePath)));
        JsonObject module = JsonParser.parseReader(reader).getAsJsonObject();
        
        String lineStart = moduleFilename + "\\:\\:$";
        debugElement(lineStart, "$", module, includeFields, excludeFields, moduleFilename);
        
      } catch (Exception e) {
        System.err.println("ERROR processing module " + moduleFilename + ": " + e.getMessage());
        e.printStackTrace();
      }
    });
  }
  
  private void debugElement(String path, String currentElementName, JsonElement element,
                           List<String> includeFields, List<String> excludeFields, 
                           String moduleFilename) {
    
    if (element.isJsonArray()) {
      for (int i = 0; i < element.getAsJsonArray().size(); i++) {
        JsonElement fieldValue = element.getAsJsonArray().get(i);
        debugElement(path + "[" + i + "]", currentElementName, fieldValue, 
                    includeFields, excludeFields, moduleFilename);
      }
    } else if (element.isJsonObject()) {
      JsonObject jo = element.getAsJsonObject();
      
      for (String field : jo.keySet()) {
        System.out.println("  Found field: '" + field + "'");
        
        // Show the original problematic escaping logic
        String safeFieldName = field.replace(" ", "\\ ").replace(":", "\\:");
        String problematicPath = path + "['" + safeFieldName + "']";
        
        // Show what we're trying to generate
        System.out.println("    Original field name: '" + field + "'");
        System.out.println("    Escaped field name:  '" + safeFieldName + "'");
        System.out.println("    Generated JSONPath:   '" + problematicPath + "'");
        
        // Test if this JSONPath is valid
        try {
          Path jsonPath = PathCompiler.compile(problematicPath);
          System.out.println("    JSONPath validation:  ✅ VALID");
        } catch (Exception e) {
          System.err.println("    JSONPath validation:  ❌ INVALID - " + e.getMessage());
          System.err.println("    ERROR INDEX: " + extractErrorIndex(e.getMessage()));
          
          // Show what the correct path should be
          String correctPath = path + "['" + field + "']";
          System.out.println("    Correct JSONPath:     '" + correctPath + "'");
          
          try {
            Path correctJsonPath = PathCompiler.compile(correctPath);
            System.out.println("    Correct validation:   ✅ VALID");
          } catch (Exception e2) {
            System.err.println("    Correct validation:   ❌ STILL INVALID - " + e2.getMessage());
          }
          
          System.out.println("    Module: " + moduleFilename);
          System.out.println("    Full context: " + problematicPath);
          System.out.println();
        }
        
        JsonElement fieldValue = jo.get(field);
        debugElement(problematicPath, field, fieldValue, includeFields, excludeFields, moduleFilename);
      }
      
    } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
      // This is where we'd generate a property line
      boolean shouldInclude = (includeFields != null && includeFields.contains(currentElementName))
                           || (excludeFields != null && !excludeFields.contains(currentElementName));
      
      if (shouldInclude) {
        System.out.println("    Would generate property: " + path + " = " + element.getAsString());
        
        // Test the properties parsing logic
        try {
          String[] keyParts = path.split("::");
          if (keyParts.length == 2) {
            String jsonPathPart = keyParts[1];
            System.out.println("    Extracted JSONPath: '" + jsonPathPart + "'");
            
            Path jsonPath = PathCompiler.compile(jsonPathPart);
            System.out.println("    Final JSONPath test: ✅ VALID");
          }
        } catch (Exception e) {
          System.err.println("    Final JSONPath test: ❌ INVALID - " + e.getMessage());
        }
      }
    }
  }
  
  private String extractErrorIndex(String errorMessage) {
    // Extract the index from error messages like "... at index 20"
    if (errorMessage.contains("at index ")) {
      String[] parts = errorMessage.split("at index ");
      if (parts.length > 1) {
        return parts[1].split(" ")[0];
      }
    }
    return "unknown";
  }
}