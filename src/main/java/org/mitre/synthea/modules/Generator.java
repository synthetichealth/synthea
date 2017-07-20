package org.mitre.synthea.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.mitre.synthea.export.Exporter;
import org.mitre.synthea.helpers.Config;

/**
 * Generator creates a population by running the generic modules each timestep per Person.
 */
public class Generator {

	public long ONE_HUNDRED_YEARS = 100l * TimeUnit.DAYS.toMillis(365);
	public List<Person> people;
	public int numberOfPeople;
	public long seed;
	private Random random;
	public long timestep;
	public Map<String,AtomicInteger> stats;
	
	public Generator(int people)
	{
		init(people, System.currentTimeMillis());
	}
	
	public Generator(int people, long seed)
	{
		init(people, seed);
	}
	
	private void init(int people, long seed)
	{
		this.people = Collections.synchronizedList(new ArrayList<Person>());
		this.numberOfPeople = people;
		this.seed = seed;
		this.random = new Random(seed);
		this.timestep = Long.parseLong( Config.get("generate.timestep") );
		this.stats = Collections.synchronizedMap(new HashMap<String,AtomicInteger>());
		stats.put("alive", new AtomicInteger(0));
		stats.put("dead", new AtomicInteger(0));
	}
	
	public void run()
	{
		ExecutorService threadPool = Executors.newFixedThreadPool(8);
		long stop = System.currentTimeMillis();

		for(int i=0; i < numberOfPeople; i++)
		{
			final int index = i;
			threadPool.submit( () -> 
			{
				List<Module> modules = Module.getModules();
				
				long start = stop - (long)(ONE_HUNDRED_YEARS * random.nextDouble());
	//			System.out.format("Born : %s\n", Instant.ofEpochMilli(start).toString());
				Person person = new Person(System.currentTimeMillis());

				LifecycleModule.birth(person, start);
				
				people.add(person);
				
				long time = start;
				while(person.alive(time) && time < stop)
				{
					Iterator<Module> iter = modules.iterator();
					while(iter.hasNext())
					{
						Module module = iter.next();
	//					System.out.format("Processing module %s\n", module.name);
						if(module.process(person, time))
						{
	//						System.out.format("Removing module %s\n", module.name);
							iter.remove(); // this module has completed/terminated.
						}
					}
					// TODO: if CHW policy is enabled for community, possibly add CHW interventions
					// if true
					// then add chw encounter to record
					// and set chw variable(s) on person.attributes.put(KEY, VALUE)
					time += timestep;
				}
				
				try {
					Exporter.export(person, stop);
				} catch (Exception e)
				{
					e.printStackTrace();
					throw e;
				}
				
				String deceased = person.alive(time) ? "" : "DECEASED";
				System.out.format("%d -- %s (%d y/o) %s\n", index+1, person.attributes.get(Person.NAME), person.ageInYears(stop), deceased);
				
				String key = person.alive(time) ? "alive" : "dead";
				
				AtomicInteger count = stats.get(key);
				count.incrementAndGet();
			});
		}

		try 
		{
			threadPool.shutdown();
			while (!threadPool.awaitTermination(30, TimeUnit.SECONDS))
			{
				System.out.println("Waiting for threads to finish... " + threadPool);
			}
		} catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		
		System.out.println(stats);
	}
}
