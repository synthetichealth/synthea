// src/main/java/org/mitre/synthea/helpers/ModuleOverrides.java

package org.mitre.synthea.helpers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.WildcardFileFilter;

/**
 * Task class to generate a properties list of "overridable" fields within the modules,
 *  which includes the name of the file, the JSONPath to the field, and the original value.
 * By default this is expected to be the set of all distributions,
 *  within all transitions, in all modules, but there are 4 configuration options:
 *  - includeFields: (defaults to ["distribution"] )
 *      -- numeric fields that match one of the given name will be written to the properties file
 *  - excludeFields: (defaults to null)
 *      -- if provided, all numeric fields except those that match the given names
 *         will be written to the properties file
 *      -- note: if both includeFields and excludeFields are given, includeFields will be ignored
 *  - includeModules: (defaults to null)
 *      -- if provided, only modules that match the given file names will be processed
 *      -- wildcards are allowed, ex "metabolic*"
 *  - excludeModules: (defaults to null)
 *      -- if provided, all modules except those that match the given file names will be processed
 *      -- wildcards are allowed, ex "metabolic*"
 * The format of the properties file is:
 * (module file name)\:\:(JSONPath to numeric field within module) = original value
 * Sample Line:
 * osteoporosis.json\:\:$['states']['Male']['distributed_transition'][1]['distribution'] = 0.02
 */
public class ModuleOverrides {

  private List<String> includeFields;
  private List<String> excludeFields;
  private FilenameFilter includeModules;
  private FilenameFilter excludeModules;

  /**
   * Main method, not to be invoked directly: should always be called via gradle task `overrides`.
   */
  public static void main(String[] args) throws Exception {
    List<String> includeFields = null;
    List<String> excludeFields = null;
    List<String> includeModules = null;
    List<String> excludeModules = null;

    if (args.length > 0) {
      includeFields = Arrays.asList(args[0].split(","));
    }
    if (args.length > 1) {
      excludeFields = Arrays.asList(args[1].split(","));
    }
    if (args.length > 2) {
      includeModules = Arrays.asList(args[2].split(","));
    }
    if (args.length > 3) {
      excludeModules = Arrays.asList(args[3].split(","));
    }

    ModuleOverrides mo = new ModuleOverrides(includeFields, includeModules, excludeFields, excludeModules);
    List<String> lines = mo.generateOverrides();

    for (String line : lines) {
      System.out.println(line);
    }
  }

  /**
   * Generate a properties list of "overridable" fields within the modules.
   * @param includeFields List of field names to include
   * @param includeModules List of module filename patterns to include (supports wildcards)
   * @param excludeFields List of field names to exclude
   * @param excludeModules List of module filename patterns to exclude (supports wildcards)
   */
  public ModuleOverrides(List<String> includeFields, List<String> includeModules,
                         List<String> excludeFields, List<String> excludeModules) {
    this.includeFields = includeFields;
    this.excludeFields = excludeFields;

    if (includeModules != null) {
      String[] patterns = includeModules.toArray(new String[includeModules.size()]);
      this.includeModules = new WildcardFileFilter(patterns, IOCase.INSENSITIVE);
    }

    if (excludeModules != null) {
      String[] patterns = excludeModules.toArray(new String[excludeModules.size()]);
      this.excludeModules = new WildcardFileFilter(patterns, IOCase.INSENSITIVE);
    }
  }

  /**
   * Generate the list of overrides.
   * @return List of strings to be written to file. Strings are of format:
   *         (module file name)\:\:(JSONPath to numeric field within module) = original value
   */
  public List<String> generateOverrides() throws Exception {
    List<String> lines = new LinkedList<>();

    Utilities.walkAllModules((basePath, modulePath) -> processModule(basePath, modulePath, lines));

    return lines;
  }

  private void processModule(Path modulesPath, Path modulePath, List<String> lines) {
    String moduleFilename = modulesPath.relativize(modulePath).toString();

    if ((includeModules != null && !includeModules.accept(null, moduleFilename))
        || (excludeModules != null && excludeModules.accept(null, moduleFilename))) {
      return;
    }

    try {
      String moduleRelativePath = modulesPath.getParent().relativize(modulePath).toString();
      JsonReader reader = new JsonReader(new StringReader(
               Utilities.readResource(moduleRelativePath)));
      JsonObject module = JsonParser.parseReader(reader).getAsJsonObject();

      // Keep module filename clean for JSONPath generation
      String lineStart = moduleFilename + "\\:\\:$";
      lines.addAll(handleElement(lineStart, "$", module));
    } catch (IOException e) {
      throw new RuntimeException("Unable to read modules", e);
    }
  }

  private List<String> handleElement(String path, String currentElementName, JsonElement element) {
    // do a depth-first search through the JSON structure,
    // and add things that are numbers and meet our filter criteria to the list
    List<String> parameters = new LinkedList<>();

    if (element.isJsonArray()) {
      JsonArray ja = element.getAsJsonArray();
      for (int i = 0; i < ja.size(); i++) {
        JsonElement fieldValue = ja.get(i);
        parameters.addAll(handleElement(path + "[" + i + "]", currentElementName, fieldValue));
      }
    } else if (element.isJsonObject()) {
      JsonObject jo = element.getAsJsonObject();

      for (String field : jo.keySet()) {
        // FIXED: Properly escape field names for JSONPath
        JsonElement fieldValue = jo.get(field);
        String cleanJsonPath = path + "[" + escapeFieldNameForJsonPath(field) + "]";
        parameters.addAll(handleElement(cleanJsonPath, field, fieldValue));
      }

    } else if (element.isJsonPrimitive()) {
      JsonPrimitive jp = element.getAsJsonPrimitive();
      if (jp.isNumber()) {
        if ((includeFields != null && includeFields.contains(currentElementName))
            || (excludeFields != null && !excludeFields.contains(currentElementName))) {

          // Apply properties file escaping only at the final output stage
          String escapedPath = escapeForPropertiesFile(path);
          String newParam = escapedPath + " = " + jp.getAsString();
          parameters.add(newParam);
        }
      }
    }

    return parameters;
  }

  /**
   * Escape a field name for use in JSONPath expressions.
   * Handles single quotes and other special characters that could break JSONPath syntax.
   *
   * @param fieldName The raw field name from JSON
   * @return Properly quoted field name for JSONPath
   */
  private String escapeFieldNameForJsonPath(String fieldName) {
    // If field name contains single quotes, use double quotes
    if (fieldName.contains("'")) {
      // Escape any double quotes in the field name and wrap in double quotes
      return "\"" + fieldName.replace("\"", "\\\"") + "\"";
    } else {
      // Safe to use single quotes
      return "'" + fieldName + "'";
    }
  }

  /**
   * Escape a path for use in Java Properties files.
   * This should only be called on the final output, not during JSONPath generation.
   *
   * @param path The path containing module filename and JSONPath
   * @return The escaped path suitable for properties files
   */
  private String escapeForPropertiesFile(String path) {
    // Split the path into filename and JSONPath parts
    String[] parts = path.split("\\\\:\\\\:", 2);
    if (parts.length != 2) {
      // Fallback - escape the whole thing
      return path.replace(" ", "\\ ").replace(":", "\\:");
    }

    String moduleFilename = parts[0];
    String jsonPath = parts[1];

    // Escape only the module filename part for properties file format
    String escapedModuleFilename = moduleFilename.replace(" ", "\\ ").replace(":", "\\:");

    // JSONPath part stays clean - no escaping needed for JSONPath syntax
    // The properties file format will handle this correctly

    return escapedModuleFilename + "\\:\\:" + jsonPath;
  }
}