package org.mitre.synthea.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.mitre.synthea.engine.Module;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.world.agents.Person;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;

/**
 * Class to track state and transition metrics from the modules.
 * At the end of the simulation this class can print out debugging statistics
 * for each module/state:
 * - How many people hit that state
 * - What states they transitioned to
 * - How long they were in that state (ex, Guard, Delay)
 */
public class TransitionMetrics {
  /**
   * Internal table of (Module,State) -> Metric. 
   * Note that a table may not be the most appropriate data structure,
   * but it's a lot cleaner than a Map of Module -> Map of State -> Metric.
   */
  private Table<String, String, Metric> metrics = 
      Tables.synchronizedTable(HashBasedTable.create());

  /**
   * List of all modules. This reference held here so we don't have to get it multiple times.
   */
  private static final List<Module> ALL_MODULES = Module.getModules();

  /**
   * Record all appropriate state transition information from the given person.
   * 
   * @param person
   *          Person that went through the modules
   * @param simulationEnd
   *          Date the simulation ended
   */
  public void recordStats(Person person, long simulationEnd) {
    recordStats(person, simulationEnd, ALL_MODULES);
  }
  
  /**
   * Record all appropriate state transition information from the given person.
   * 
   * @param person
   *          Person that went through the modules
   * @param simulationEnd
   *          Date the simulation ended
   * @param modules
   *          The collection of modules to record stats for
   */
  public void recordStats(Person person, long simulationEnd, Collection<Module> modules) {
    for (Module m : modules) {
      if (!m.getClass().equals(Module.class)) {
        // java module, not GMF. no states to show
        continue;
      }

      List<State> history = (List<State>) person.attributes.get(m.name);
      if (history == null) {
        continue;
      }

      // count basic "counter" stats for this state
      history.forEach(s -> countStateStats(s, getMetric(m.name, s.name), simulationEnd));

      // count this person only once for each distinct state they hit
      history.stream().map(s -> s.name).distinct()
          .forEach(sName -> getMetric(m.name, sName).population.incrementAndGet());

      getMetric(m.name, history.get(0).name).current.incrementAndGet();

      // loop over the states backward (0 = current, n = initial)
      // and track from->to stats in pair
      if (history.size() >= 2) {
        for (int fromIndex = history.size() - 1; fromIndex > 0; fromIndex--) {
          int toIndex = fromIndex - 1;

          State from = history.get(fromIndex);
          State to = history.get(toIndex);

          getMetric(m.name, from.name).incrementDestination(to.name);
        }
      }
    }
  }

  /**
   * Get the Metric object for the given State in the given Module.
   * 
   * @param moduleName Name of the module
   * @param stateName Name of the state
   * @return Metric object
   */
  public Metric getMetric(String moduleName, String stateName) {
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

  private void countStateStats(State state, Metric stateStats, long endDate) {
    stateStats.entered.incrementAndGet();
    long exitTime = (state.exited == null) ? endDate : state.exited; 
    // if they were in the last state when they died or time expired
    long startTime = state.entered;
    // note: the ruby module has a hack for
    // "when the lifecycle module kills people before the initial state"
    // but i dont think that will break anything here if it happens

    stateStats.duration.addAndGet(exitTime - startTime);
  }

  /**
   * Print the statistics that have been gathered.
   * 
   * @param totalPopulation
   *          The total population that was simulated.
   */
  public void printStats(int totalPopulation) {
    printStats(totalPopulation, ALL_MODULES);
  }
  
  /**
   * Print the statistics that have been gathered.
   * 
   * @param totalPopulation
   *          The total population that was simulated.
   * @param modules
   *          The collection of modules to display stats for
   */
  public void printStats(int totalPopulation, Collection<Module> modules) {
    for (Module m : modules) {
      if (!m.getClass().equals(Module.class)) {
        // java module, not GMF. no states to show
        continue;
      }
      System.out.println(m.name.toUpperCase());

      Map<String, Metric> moduleMetrics = metrics.row(m.name);

      for (String stateName : moduleMetrics.keySet()) {
        Metric stats = getMetric(m.name, stateName);
        int entered = stats.entered.get();
        int population = stats.population.get();
        long duration = stats.duration.get();
        int current = stats.current.get();
        
        
        System.out.println(stateName + ":");
        System.out.println(" Total times entered: " + stats.entered);
        System.out.println(" Population that ever hit this state: " + stats.population + " ("
            + decimal(population, totalPopulation) + "%)");
        System.out.println(" Average # of hits per total population: "
            + decimal(entered, totalPopulation));
        System.out.println(" Average # of hits per person that ever hit state: "
            + decimal(entered, population));
        System.out.println(" Population currently in state: " + stats.current + " ("
            + decimal(current, totalPopulation) + "%)");
        State state = m.getState(stateName);
        if (state instanceof State.Guard || state instanceof State.Delay) {
          System.out.println(" Total duration: " + durationOf(duration));
          System.out.println(" Average duration per time entered: "
              + durationOf(duration / entered));
          System.out.println(" Average duration per person that ever entered state: "
              + durationOf(duration / population));
        } else if (state instanceof State.Encounter && ((State.Encounter) state).isWellness()) {
          System.out.println(" (duration metrics for wellness encounter omitted)");
        }

        if (!stats.destinations.isEmpty()) {
          System.out.println(" Transitioned to:");
          long total = stats.destinations.values().stream().mapToLong(ai -> ai.longValue()).sum();
          stats.destinations.forEach((toState, count) -> 
                System.out.println(" --> " + toState + " : " + count + " = " 
                                    + decimal(count.get(), total) + "%"));
        }
        System.out.println();
      }

      List<String> unreached = new ArrayList<>(m.getStateNames());
      // moduleMetrics only includes states actually hit
      unreached.removeAll(moduleMetrics.keySet()); 
      unreached.forEach(state -> System.out.println(state + ": \n Never reached \n\n"));

      System.out.println();
      System.out.println();
    }
  }

  /**
   * Helper function to convert a # of milliseconds into a human-readable string. Results are not
   * necessarily precise, and are intended for general understanding only. The resulting format is
   * not specified and may change at any time.
   * Ex. duration(14*30*24*60*60*1000) may indicate a result of "14 months", "1 year and 2 months",
   * "1.17 years", etc.
   * 
   * @param time time duration in ms
   * @return Human readable description of the time
   */
  public static String durationOf(long time) {
    // this returns something like 15 days 15 hours 24 minutes 26 seconds
    String result = DurationFormatUtils.formatDurationWords(time, true, true);
    
    // if it starts with a large number of days, we can do a little better
    String[] parts = result.split(" ");
    
    if (parts.length > 1 && parts[1].equals("days")) {
      long days = Long.parseLong(parts[0]);
      
      if (days > 365) {
        long years = days / 365;
        long months = (days % 365) / 30;
        return years + " years " + months + " months";
      } else if (days > 30) {
        long months = days / 30;
        days = days % 30;
        return months + " months " + days + " days";
      }
    }
    
    return result;
  }

  /**
   * Helper function to convert a numerator and denominator into a string with a single number and
   * exactly 2 decimal places.
   * 
   * @param num
   *          Numerator
   * @param denom
   *          Denominator
   * @return num/denom rounded to 2 decimal places
   */
  private static String decimal(double num, double denom) {
    // note that this is especially helpful because ints can be passed in without explicit casting
    // and if you want to get a double from integer division you have to cast the input items
    return String.format("%.2f", (100.0 * num / denom));
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
     * @param destination Target state that was transitioned to
     */
    void incrementDestination(String destination) {
      
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
    }
  }
}
