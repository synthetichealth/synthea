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
   *
   * @param args -- format is [includeFields, includeModules, excludeFields, excludeModules]
   */
  public static void main(String[] args) throws Exception {
    String includeFieldsArg = args[0];
    String includeModulesArg = args[1];
    String excludeFieldsArg = args[2];
    String excludeModulesArg = args[3];

    List<String> excludeFields = argToList(excludeFieldsArg);
    List<String> includeFields;
    if (excludeFields == null) {
      includeFields = argToList(includeFieldsArg);
      if (includeFields == null) {
        includeFields = Arrays.asList("distribution");
      }
    } else {
      includeFields = null; // if they exclude something, don't do anything with includes
    }

    List<String> includeModules = argToList(includeModulesArg);
    List<String> excludeModules = argToList(excludeModulesArg);

    System.out.println("Included fields: " + includeFields);
    System.out.println("Excluded fields: " + excludeFields);
    System.out.println("Included modules: " + includeModules);
    System.out.println("Excluded modules: " + excludeModules);

    ModuleOverrides mo =
        new ModuleOverrides(includeFields, includeModules, excludeFields, excludeModules);

    List<String> lines = mo.generateOverrides();

    Path outFilePath = new File("./output/overrides.properties").toPath();

    Files.write(outFilePath, lines);

    System.out.println("Catalogued " + lines.size() + " parameters.");
    System.out.println("Done.");
  }

  private static List<String> argToList(String arg) {
    if (arg == null || arg.isEmpty()) {
      return null;
    }

    List<String> list = new LinkedList<>();

    list.addAll(Arrays.asList(arg.split(",")));
    list.replaceAll(s -> s.trim());

    return list;
  }

  /**
   * Create a ModuleOverrides object which will process the modules according to the given options.
   *
   * @param includeFields - List of field names to include
   * @param includeModulesList - list of module filename rules to include
   * @param excludeFields - list of field names to exclude
   * @param excludeModulesList - list of module filename rules to exclude
   */
  public ModuleOverrides(List<String> includeFields, List<String> includeModulesList,
      List<String> excludeFields, List<String> excludeModulesList) {
    this.includeFields = includeFields;
    this.excludeFields = excludeFields;

    if (includeModulesList != null) {
      this.includeModules = new WildcardFileFilter(includeModulesList, IOCase.INSENSITIVE);
    }
    if (excludeModulesList != null) {
      this.excludeModules = new WildcardFileFilter(excludeModulesList, IOCase.INSENSITIVE);
    }
  }

  /**
   * Perform the actual processing to generate the list of properties, per the given settings.
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
        // note: spaces have to be escaped in properties file key
        String safeFieldName = field.replace(" ", "\\ ");
        JsonElement fieldValue = jo.get(field);
        parameters.addAll(handleElement(path + "['" + safeFieldName + "']", field, fieldValue));
      }

    } else if (element.isJsonPrimitive()) {
      JsonPrimitive jp = element.getAsJsonPrimitive();
      if (jp.isNumber()) {
        if ((includeFields != null && includeFields.contains(currentElementName))
            || (excludeFields != null && !excludeFields.contains(currentElementName))) {
          String newParam = path + " = " + jp.getAsString();
          parameters.add(newParam);
        }
      }
    }

    return parameters;
  }
}
