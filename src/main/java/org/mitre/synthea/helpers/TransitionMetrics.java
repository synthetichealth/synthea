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

	private Table<String, String, Metric> metricsa = HashBasedTable.create();
	
	private static final List<Module> ALL_MODULES = Module.getModules(); 
	
	public void recordStats(Person person, long simulationEnd)
	{
		for (Module m : ALL_MODULES)
		{
			if (!m.getClass().equals(Module.class))
			{
				// java module, not GMF. no states to show
				continue;
			}
			
			List<State> history = (List<State>) person.attributes.get(m.name);
			
			if (history == null)
			{
				continue;
			}
			
			history.forEach( s -> countStateStats(s, getMetric(m.name, s.name), simulationEnd) ); 
			
			// count this person only once for each distinct state they hit
			history.stream().map( s -> s.name ).distinct().forEach( sName -> getMetric(m.name, sName).population += 1); 
			
			getMetric(m.name, history.get(0).name ).current += 1;
			
			// loop over the states backward (0 = current, n = initial)
			// and track from->to stats in pairs
			
			if (history.size() >= 2)
			{
				for (int fromIndex = history.size() - 1 ; fromIndex > 0 ; fromIndex--)
				{
					int toIndex = fromIndex - 1;
					
					State from = history.get(fromIndex);
					State to = history.get(toIndex);
					
					getMetric(m.name, from.name).incrementDestination(to.name);
				}
			}
		}
	}
	
	private synchronized Metric getMetric(String moduleName, String stateName)
	{
		Metric metric = metricsa.get(moduleName, stateName);
		
		if (metric == null)
		{
			metric = new Metric();
			metricsa.put(moduleName, stateName, metric);
		}

		return metric;
	}
	
	private void countStateStats(State state, Metric stateStats, long endDate)
	{
		stateStats.entered += 1;
		long exitTime = (state.exited == null) ? endDate : state.exited; // if they were in the last state when they died or time expired
		long startTime = state.entered; // note: the ruby module has a hack for "when the lifecycle module kills people before the initial state"
		// but i dont think that will break anything here if it happens
		
		stateStats.duration += (exitTime - startTime);
	}
	

	public void printStats(int totalPopulation) 
	{
		for (Module m : ALL_MODULES)
		{
			if (!m.getClass().equals(Module.class))
			{
				// java module, not GMF. no states to show
				continue;
			}
			System.out.println(m.name.toUpperCase());
			
			Map<String,Metric> moduleMetrics = metricsa.row(m.name);
			
			for (String stateName : moduleMetrics.keySet())
			{
				Metric state_stats = getMetric(m.name, stateName);
				System.out.println(stateName + ":");
		        System.out.println(" Total times entered: " + state_stats.entered);
		        System.out.println(" Population that ever hit this state: " + state_stats.population + " (" +  percent(state_stats.population, totalPopulation, true)  + ")");
		        System.out.println(" Average # of hits per total population: " + percent(state_stats.entered, totalPopulation, false));
		        System.out.println(" Average # of hits per person that ever hit state: " + percent(state_stats.entered, state_stats.population, false));
		        System.out.println(" Population currently in state: " + state_stats.current + " ( " +  percent(state_stats.current, totalPopulation, true)  + ")");
		        State state = m.getState(stateName);
		        if (state instanceof State.Guard || state instanceof State.Delay)
		        {
		          System.out.println(" Total duration: " + duration(state_stats.duration));
		          System.out.println(" Average duration per time entered: " + duration(state_stats.duration / state_stats.entered));
		          System.out.println(" Average duration per person that ever entered state: " + duration(state_stats.duration / state_stats.population));
		          // System.out.println(" Average duration per entire population: " + duration(state_stats.duration / population));
		        }
		        else if (state instanceof State.Encounter && ((State.Encounter)state).isWellness())
		        {
		          System.out.println(" (duration metrics for wellness encounter omitted)");
		        }
		        if (!state_stats.destinations.isEmpty())
		        {
		          System.out.println(" Transitioned to:");
		          long total_transitions = state_stats.destinations.values().stream().mapToLong(Integer::intValue).sum();
		          state_stats.destinations.forEach((to_state, count) -> {
		            System.out.println(" --> " + to_state + " : " + count + " = " +  percent(count, total_transitions, true));
		        	});
		        }
		        System.out.println();
			}
			
			List<String> unreached = new ArrayList<>(m.getStateNames());
			
			unreached.removeAll(moduleMetrics.keySet());

			unreached.forEach( state -> System.out.println(state +": \n Never reached \n\n"));
			
			System.out.println();
			System.out.println();
		}
	}
	
    private static String duration(double time){
        // augmented version of http://stackoverflow.com/a/1679963
        // note that anything less than days here is generally never going to be used
        double secs = time / 1000.0;
        double mins = secs / 60.0;
        double hours = mins / 60.0;
        double days = hours / 24.0;
        double weeks = days / 7.0;
        double months = days / 30.0; // not intended to be exact here
        double years = days / 365.25;

        if (((long)years) > 0)
          return String.format("%.2f years (About %d years and %d months)", years, (long)years, ((long)months % 12));
        else if (((long)months) > 0)
          return String.format("%.2f months (About %d months and %d days)", months, (long)months, ((long)days % 30));
        else if (((long)weeks) > 0)
          return String.format("%.2f weeks (About %d weeks and %d days)", weeks, (long)weeks, ((long)days % 7));
        else if (((long)days) > 0)
          return String.format("%.2f days (About %d days and %d hours)", days, (long)days, ((long)hours % 24));
        else if (((long)hours) > 0)
          return String.format("%.2f hours (About %d hours and %d mins)", hours, (long)hours, ((long)mins % 60));
        else if (((long)mins) > 0)
          return String.format("%.2f minutes (About %d minutes and %d seconds)", mins, (long)mins, ((long)secs % 60));
        else if (((long)secs) > 0)
          return String.format("%.1f seconds", secs);
        else
          return "0";
        
      }
	
	private static String percent(double num, double denom, boolean addPercentSign)
	{
		return String.format("%.2f %s", (100.0 * num / denom), (addPercentSign ? "%" : ""));
	}
	
	public static class Metric
	{
		public int entered; // number of times this state was entered
		public long duration; // total length of time people sat in this state
		public int population; // number of people that ever his this state
		public int current; // number of people that are "currently" in that state
		public Map<String, Integer> destinations = new HashMap<>();
		
		public void incrementDestination(String destination)
		{
			Integer count = destinations.getOrDefault(destination, 0);
			
			destinations.put(destination, count + 1);
		}
	}
}
