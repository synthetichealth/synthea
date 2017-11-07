package org.mitre.synthea.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.world.agents.Person;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public class TransitionMetrics {
	private Table<String, String, Metric> metrics = HashBasedTable.create();
	
	private static final List<Module> ALL_MODULES = Module.getModules(); 
	
	/**
	 * Record all appropriate state transition information from the given person.
	 * @param person Person that went through the modules
	 * @param simulationEnd Date the simulation ended
	 */
	public void recordStats(Person person, long simulationEnd) {
		for (Module m : ALL_MODULES) {
			if (!m.getClass().equals(Module.class)) {
				// java module, not GMF. no states to show
				continue;
			}
			
			List<State> history = (List<State>) person.attributes.get(m.name);
			if (history == null) {
				continue;
			}
			
			// count basic "counter" stats for this state
			history.forEach( s -> countStateStats(s, getMetric(m.name, s.name), simulationEnd) ); 
			
			// count this person only once for each distinct state they hit
			history.stream().map( s -> s.name ).distinct().forEach( sName -> getMetric(m.name, sName).population += 1); 
			
			getMetric(m.name, history.get(0).name ).current += 1;
			
			// loop over the states backward (0 = current, n = initial)
			// and track from->to stats in pair
			if (history.size() >= 2) {
				for (int fromIndex = history.size() - 1 ; fromIndex > 0 ; fromIndex--) {
					int toIndex = fromIndex - 1;
					
					State from = history.get(fromIndex);
					State to = history.get(toIndex);
					
					getMetric(m.name, from.name).incrementDestination(to.name);
				}
			}
		}
	}
	
	private synchronized Metric getMetric(String moduleName, String stateName) {
		Metric metric = metrics.get(moduleName, stateName);
		
		if (metric == null)	{
			metric = new Metric();
			metrics.put(moduleName, stateName, metric);
		}

		return metric;
	}
	
	private void countStateStats(State state, Metric stateStats, long endDate) {
		stateStats.entered += 1;
		long exitTime = (state.exited == null) ? endDate : state.exited; // if they were in the last state when they died or time expired
		long startTime = state.entered; 
		// note: the ruby module has a hack for "when the lifecycle module kills people before the initial state"
		// but i dont think that will break anything here if it happens
		
		stateStats.duration += (exitTime - startTime);
	}
	
	/**
	 * Print the statistics that have been gathered.
	 * 
	 * @param totalPopulation The total population that was simulated.
	 */
	public void printStats(int totalPopulation) {
		for (Module m : ALL_MODULES) {
			if (!m.getClass().equals(Module.class))	{
				// java module, not GMF. no states to show
				continue;
			}
			System.out.println(m.name.toUpperCase());
			
			Map<String,Metric> moduleMetrics = metrics.row(m.name);
			
			for (String stateName : moduleMetrics.keySet())	{
				Metric stats = getMetric(m.name, stateName);
				System.out.println(stateName + ":");
		        System.out.println(" Total times entered: " + stats.entered);
		        System.out.println(" Population that ever hit this state: " + stats.population + " (" +  decimal(stats.population, totalPopulation)  + "%)");
		        System.out.println(" Average # of hits per total population: " + decimal(stats.entered, totalPopulation));
		        System.out.println(" Average # of hits per person that ever hit state: " + decimal(stats.entered, stats.population));
		        System.out.println(" Population currently in state: " + stats.current + " (" +  decimal(stats.current, totalPopulation)  + "%)");
		        State state = m.getState(stateName);
		        if (state instanceof State.Guard || state instanceof State.Delay) {
		          System.out.println(" Total duration: " + duration(stats.duration));
		          System.out.println(" Average duration per time entered: " + duration(stats.duration / stats.entered));
		          System.out.println(" Average duration per person that ever entered state: " + duration(stats.duration / stats.population));
		        } else if (state instanceof State.Encounter && ((State.Encounter)state).isWellness()) {
		          System.out.println(" (duration metrics for wellness encounter omitted)");
		        }
		        
		        if (!stats.destinations.isEmpty()) {
		          System.out.println(" Transitioned to:");
		          long total = stats.destinations.values().stream().mapToLong(Integer::longValue).sum();
		          stats.destinations.forEach((to_state, count) -> 
		            System.out.println(" --> " + to_state + " : " + count + " = " + decimal(count, total) + "%")
		        	);
		        }
		        System.out.println();
			}
			
			List<String> unreached = new ArrayList<>(m.getStateNames());
			unreached.removeAll(moduleMetrics.keySet()); // moduleMetrics only includes states actually hit
			unreached.forEach( state -> System.out.println(state +": \n Never reached \n\n"));
			
			System.out.println();
			System.out.println();
		}
	}
	
	/**
	 * Helper function to convert a # of milliseconds into a human-readable string.
	 * Results are not necessarily precise, and are intended for general understanding only.
	 * The resulting format is not specified and may change at any time.
	 * 
	 * Ex. duration(14*30*24*60*60*1000) may indicate a result of "14 months", "1 year and 2 months", "1.17 years", etc.
	 * 
	 * @param time
	 * @return
	 */
    private static String duration(double time) {
        // augmented version of http://stackoverflow.com/a/1679963
        // note that anything less than days here is generally never going to be used
        double secs = time / 1000.0;
        double mins = secs / 60.0;
        double hours = mins / 60.0;
        double days = hours / 24.0;
        double weeks = days / 7.0;
        double months = days / 30.0; // not intended to be exact here
        double years = days / 365.25;

        if (((long)years) > 0) {
          return String.format("%.2f years (About %d years and %d months)", years, (long)years, ((long)months % 12));
        } else if (((long)months) > 0) {
          return String.format("%.2f months (About %d months and %d days)", months, (long)months, ((long)days % 30));
        } else if (((long)weeks) > 0) {
          return String.format("%.2f weeks (About %d weeks and %d days)", weeks, (long)weeks, ((long)days % 7));
        } else if (((long)days) > 0) {
          return String.format("%.2f days (About %d days and %d hours)", days, (long)days, ((long)hours % 24));
        } else if (((long)hours) > 0) {
          return String.format("%.2f hours (About %d hours and %d mins)", hours, (long)hours, ((long)mins % 60));
        } else if (((long)mins) > 0) {
          return String.format("%.2f minutes (About %d minutes and %d seconds)", mins, (long)mins, ((long)secs % 60));
        } else if (((long)secs) > 0) {
          return String.format("%.1f seconds", secs);
        } else {
          return "0";
        }
      }
    
    /**
     * Helper function to convert a numerator and denominator 
     * into a string with a single number and exactly 2 decimal places.
     * @param num Numerator
     * @param denom Denominator
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
	private static class Metric	{
		int entered; // number of times this state was entered
		long duration; // total length of time people sat in this state
		public int population; // number of people that ever his this state
		int current; // number of people that are "currently" in that state
		Map<String, Integer> destinations = new HashMap<>(); // key: state that this state transitioned to, value: number of times
		
		void incrementDestination(String destination) {
			Integer count = destinations.getOrDefault(destination, 0);
			destinations.put(destination, count + 1);
		}
	}
}
