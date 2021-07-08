package org.mitre.synthea.helpers;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.engine.Module.ModuleSupplier;
import org.mitre.synthea.helpers.Attributes;
import org.mitre.synthea.helpers.Attributes.Inventory;

/* Task class to export a report of all Modules 
 * that Synthea can access
 */

public class Modules {
  public static Map<String, ModuleSupplier> moduleList; 
  public static Map<String, Inventory> inventoryList;
  //public static List<Module> moduleNameList; 
  public static Map<String, Set<String> > outputList = new TreeMap<String, Set<String> >();
  public static List<String> moduleNameList;
  
  // public class 
  public static void main(String[] args) throws Exception {
    System.out.println("Printing list of modules into output/modules.json");

    // Find list of modules
    moduleNameList = Arrays.asList(Module.getModuleNames());
    // Get Attribute list for dependency information
    inventoryList = Attributes.getAttributeInventory();
    // First, put all module names in the list
    for ( String inst : moduleNameList) {
        String inst_name = inst.replace(' ','_').toLowerCase();
        // Skip submodules
        if (inst_name.indexOf("/") > 0) { continue; }
        outputList.putIfAbsent(inst_name, new HashSet<String>());
    }
    for ( String inst: inventoryList.keySet()) {
        String inst_name = inst.replace(' ','_').toLowerCase();
        if (moduleNameList.indexOf(inst_name) > 0 ) {
            // Loop throught READ to match dependencies
            for ( String reqModule: inventoryList.get(inst).read.keySet()) {
                String name = reqModule.replace(' ','_').toLowerCase();
                // No depending upon yourself
                if (name.equals(inst_name)) {continue;}
                if (moduleNameList.indexOf(name) > 0) {
                        Set<String> updated = outputList.get(name);
                        updated.add(inst_name);
                        outputList.put(name, updated);
                }
            }
            for ( String reqModule: inventoryList.get(inst).write.keySet()) {
                String name = reqModule.replace(' ','_').toLowerCase();
                // No depending upon yourself
                if (name.equals(inst_name)) { continue;}
                if (moduleNameList.indexOf(name) > 0) {
                    Set<String> updated = outputList.get(inst_name);
                    updated.add(name);
                    outputList.put(inst_name, updated);
                }
            }
        };
    }
    // Write out information
    String outFilePath = new File("./output/modules.json").toPath().toString();
    Writer writer = new FileWriter(outFilePath);
    Gson gson = new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .setPrettyPrinting().create();
    gson.toJson(outputList, writer);
    writer.flush();
    writer.close();


    System.out.println("Done.");
    

  }
}

