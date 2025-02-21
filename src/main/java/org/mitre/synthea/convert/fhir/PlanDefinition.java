package org.mitre.synthea.convert.fhir;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mitre.synthea.helpers.Utilities;

/**
 * Import a FHIR Plan Definition and convert to a Synthea module.
 * The resulting module is likely to require additional human intervention\
 * prior to use.
 */
public class PlanDefinition {
  /**
   * Convert a set of FHIR resources into a Synthea module.
   * @param args URL of top-level PlanDefinition resource and optional path
   *     to where the resources can be read (uses HTTP if not provided).
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println(
              "Usage: ./gradlew convertPlanDef --args=\"url [directory-path]\"");
      return;
    }

    Map<String, Map> resources = null;
    if (args.length == 2) {
      String directoryPath = args[1];
      File directory = new File(directoryPath);

      if (!directory.isDirectory()) {
        System.out.println("The provided path is not a directory.");
        return;
      }
      resources = readResources(directory);
    } else {
      // TODO read resources from FHIR server
    }
    if (resources == null || resources.isEmpty()) {
      System.out.println("No resources found");
      return;
    } else if (!resources.containsKey(args[0])) {
      System.out.printf("Top-level PlanDefinition (%s) not found\n",
              args[0]);
      return;
    }
    PlanDefinition plan = new PlanDefinition(args[0], resources);
    plan.exportModule(System.out);
  }

  private static Map<String, Map> readResources(File directory) {
    Map<String, Map> resources = new HashMap<>();
    File[] resourceFiles = directory.listFiles(
      (dir, name) -> name.toLowerCase().endsWith(".json")
    );
    if (resourceFiles == null || resourceFiles.length == 0) {
      System.out.println("No .json files found in the directory.");
    } else {
      for (File resourceFile : resourceFiles) {
        try {
          String json = Files.readString(resourceFile.toPath());
          Gson g = new Gson();
          HashMap resource = g.fromJson(json, HashMap.class);
          if (resource.containsKey("resourceType")
                  && resource.containsKey("url")) {
            resources.put(resource.get("url").toString(), resource);
            System.out.printf(
                    "Adding %s: %s\n",
                    resourceFile.getName(),
                    resource.get("resourceType"));
          } else {
            System.out.printf(
                    "%s lacks a FHIR resourceType or url, skipping\n",
                    resourceFile.getPath());
          }
        } catch (IOException ex) {
          System.out.printf("Unable to read %s, skipping\n",
                  resourceFile.getPath());
        }
      }
    }
    return resources;
  }

  private static class Encounter {
    private final Map encounter;
    private final Map relatedAction;
    private final long relativeOffset;
    private long absoluteOffset = 0;

    public enum OffsetUnit {
      DAYS,
      WEEKS,
      MONTHS
    }

    public Encounter(Map encounter, Map relatedAction) {
      this.encounter = encounter;
      this.relatedAction = relatedAction;
      this.relativeOffset = calculateOffset(relatedAction);
    }

    public String getId() {
      return this.encounter.get("id").toString();
    }

    public long getAbsoluteOffset() {
      return absoluteOffset;
    }

    public double getOffsetFrom(Encounter other, OffsetUnit unit) {
      double millis = absoluteOffset - other.absoluteOffset;
      switch(unit) {
        case DAYS:
          return millis / (1000 * 60 * 60 * 24);
        case WEEKS:
          return millis / (1000 * 60 * 60 * 24 * 7);
        case MONTHS:
          return millis / (1000 * 60 * 60 * 24 * 30);
      }
      return millis;
    }

    public long calculateAbsoluteOffset(Map<String, Encounter> all) {
      if (absoluteOffset != 0) {
        return absoluteOffset; // already calculated
      }
      Encounter relatedEncounter = all.get(relatedAction.get("actionId").toString());
      if (relatedEncounter == null) {
        absoluteOffset = relativeOffset; // this is a baseline
      } else {
        absoluteOffset = relatedEncounter.calculateAbsoluteOffset(all) + relativeOffset;
      }
      return absoluteOffset;
    }

    private static long calculateOffset(Map relatedAction) {
      if (relatedAction == null) {
        return 0;
      }
      long sign = 1;
      if ("before".equals(relatedAction.get("relationship").toString())) {
        sign = -1;
      }
      Map offsetDuration = (Map)relatedAction.get("offsetDuration");
      if (offsetDuration == null) {
        return 1 * sign; // at least 1ms before or after referenced encounter
      }
      Object value = offsetDuration.get("value");
      if (value == null) {
        return 1 * sign;
      }
      String unit = offsetDuration.get("code").toString();
      if (value instanceof Long) {
        return Utilities.convertUcumTime(unit, (Long)value) * sign;
      } else if (value instanceof Double) {
        return Utilities.convertUcumTime(unit, (Double)value) * sign;
      } else if (value instanceof Float) {
        return Utilities.convertUcumTime(unit, (Float)value) * sign;
      } else {
        System.out.printf("Unsupported offsetDuration value type: %s\n",
                value.getClass().getCanonicalName());
        return 1 * sign;
      }
    }
  }

  private final List<Encounter> orderedEncounters;

  public PlanDefinition(String planUrl, Map<String, Map> resources) {
    this.orderedEncounters = extractEncounters(planUrl, resources);
  }

  public void exportModule(PrintStream out) {
    Encounter prev = null;
    for (Encounter current: orderedEncounters) {
      if (prev != null) {
        System.out.printf("Delay: %f days\n", current.getOffsetFrom(prev, Encounter.OffsetUnit.DAYS));
      }
      System.out.printf("Encounter: %s\n", current.getId());
      prev = current;
    }
  }

  private static List<Encounter> extractEncounters(String planUrl, Map<String, Map> resources) {
    Map<String, Encounter> encounters = new HashMap<>();
    Map plan = resources.get(planUrl);
    List<Map> actions = (List<Map>)plan.get("action");
    for (Map action: actions) {
      String encounterPlanUrl = action.get("definitionCanonical").toString();
      if (!resources.containsKey(encounterPlanUrl)) {
        System.out.printf("Missing encounter plan %s, skipping\n",
                encounterPlanUrl);
        continue;
      }
      System.out.printf("Processing encounter %s\n", encounterPlanUrl);
      Map encounterPlan = resources.get(encounterPlanUrl);
      // TODO support multiple relatedAction entries?
      List<Map> relatedActions = (List<Map>)action.get("relatedAction");
      Map relatedAction = relatedActions.isEmpty() ? null : relatedActions.get(0);
      Encounter encounter = new Encounter(encounterPlan, relatedAction);
      encounters.put(encounter.getId(), encounter);
    }

    // Calculate each encounter's absolute offset from the start of the plan
    // based on its relative offset to another encounters. Sort the resulting
    // list from earliest to final encounter.
    List<Encounter> orderedEncounters = new ArrayList<>(encounters.values());
    Collections.sort(orderedEncounters,
            Comparator.comparingLong(e -> e.calculateAbsoluteOffset(
                    encounters)));

    return orderedEncounters;
  }
}
