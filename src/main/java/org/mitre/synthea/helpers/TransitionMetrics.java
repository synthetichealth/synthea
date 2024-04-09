package org.mitre.synthea.helpers;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.engine.Module.ModuleSupplier;
import org.mitre.synthea.export.Exporter;

/**
 * Class to track state and transition metrics from the modules.
 * At the end of the simulation this class can print out debugging statistics
 * for each module/state:
 * - How many people hit that state
 * - What states they transitioned to
 * - How long they were in that state (ex, Guard, Delay)
 */
public abstract class TransitionMetrics {
  /**
   * Internal table of (Module,State) -> Metric.
   * Note that a table may not be the most appropriate data structure,
   * but it's a lot cleaner than a Map of Module -> Map of State -> Metric.
   */
  private static final Table<String, String, Metric> metrics =
      Tables.synchronizedTable(HashBasedTable.create());

  public static boolean enabled =
      Config.getAsBoolean("generate.track_detailed_transition_metrics", false);

  /**
   * Track entering a state within a given module.
   * @param module The name of the module.
   * @param state The name of the state.
   * @param firstTime Whether or not this state was previously entered.
   */
  public static void enter(String module, String state, boolean firstTime) {
    if (enabled) {
      getMetric(module, state).enter(firstTime);
    }
  }

  /**
   * Track exiting a state and the resulting destination.
   * @param module The name of the module.
   * @param state The name of the state.
   * @param destination Target state that was transitioned to.
   * @param duration The time in milliseconds spent within the state.
   */
  public static void exit(String module, String state, String destination, long duration) {
    if (enabled) {
      getMetric(module, state).exit(destination, duration);
    }
  }

  /**
   * Get the Metric object for the given State in the given Module.
   *
   * @param moduleName Name of the module
   * @param stateName Name of the state
   * @return Metric object
   */
  static Metric getMetric(String moduleName, String stateName) {
    Metric metric = metrics.get(moduleName, stateName);

    if (metric == null) {
      synchronized (metrics) {
        metric = metrics.get(moduleName, stateName);
        if (metric == null) {
          metric = new Metric();
          metrics.put(moduleName, stateName, metric);
        }
      }
    }

    return metric;
  }

  /**
   * Clears the metrics. Intended for unit tests.
   */
  static void clear() {
    metrics.clear();
  }

  /**
   * Exports the metrics as JSON in the exporter base directory.
   */
  public static void exportMetrics() {
    GsonBuilder builder = new GsonBuilder();
    if (Config.getAsBoolean("exporter.pretty_print", true)) {
      builder.setPrettyPrinting();
    }
    Gson gson = builder.create();

    System.out.println("Saving metrics for " + metrics.rowKeySet().size() + " modules.");

    String baseDir = Config.get("exporter.baseDirectory", "./output/");
    String statsDir = "metrics";
    Path output = Paths.get(baseDir, statsDir);
    output.toFile().mkdirs();

    List<ModuleSupplier> suppliers = Module.getModuleSuppliers(p -> !p.core);
    for (ModuleSupplier supplier : suppliers) {
      // System.out.println("Saving statistics: " + supplier.path);

      Map<String, Metric> moduleMetrics = metrics.row(supplier.get().name);
      String json = gson.toJson(moduleMetrics);

      String filename = supplier.path + ".json";
      Path p = output;

      if (supplier.path.contains(File.separator)) {
        int index = supplier.path.lastIndexOf(File.separator);
        String subfolders = supplier.path.substring(0, index);
        filename = supplier.path.substring(index + 1) + ".json";
        for (String sub : subfolders.split(File.separator)) {
          p = p.resolve(sub);
        }
        p.toFile().mkdirs();
      }

      Exporter.overwriteFile(p.resolve(filename), json);
    }
  }

  /**
   * Helper class to track the metrics of a single State.
   */
  public static class Metric {
    /**
     * Number of times the state was entered.
     */
    public final AtomicInteger entered = new AtomicInteger(0);

    /**
     * Total length of time (ms) people were in this state.
     */
    public final AtomicLong duration = new AtomicLong(0L);

    /**
     * Number of people that ever his this state.
     */
    public final AtomicInteger population = new AtomicInteger(0);

    /**
     * Number of people that are "currently" in that state.
     */
    public final AtomicInteger current = new AtomicInteger(0);

    /**
     * Tracker for what states this state transitions to.
     * Key: state that this state transitioned to.
     * Value: number of times
     */
    public final Map<String, AtomicInteger> destinations = new ConcurrentHashMap<>();

    /**
     * Helper function to increment the count for a destination state.
     *
     * @param destination Target state that was transitioned to.
     * @param duration The time in milliseconds spent within the state.
     */
    public void exit(String destination, long duration) {

      AtomicInteger count = destinations.get(destination);
      if (count == null) {
        synchronized (destinations) {
          count = destinations.get(destination);
          if (count == null) {
            count = new AtomicInteger(0);
            destinations.put(destination, count);
          }
        }
      }
      count.incrementAndGet();
      this.current.decrementAndGet();
      this.duration.addAndGet(duration);
    }

    /**
     * Helper function to increment the counts when a state is entered.
     * @param firstTime Whether or not this state was previously entered.
     */
    public void enter(boolean firstTime) {
      this.entered.incrementAndGet();
      this.current.incrementAndGet();
      if (firstTime) {
        this.population.incrementAndGet();
      }
    }
  }
}
