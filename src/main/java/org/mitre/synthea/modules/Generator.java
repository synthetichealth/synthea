package org.mitre.synthea.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.mitre.synthea.export.Exporter;
import org.mitre.synthea.world.Location;

import com.github.javafaker.Faker;

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
		this.timestep = 1000 * 60 * 60 * 24 * 7;
	}
	
	public void run()
	{
		ExecutorService threadPool = Executors.newFixedThreadPool(8);
		long stop = System.currentTimeMillis();

        final Faker faker = new Faker();

		for(int i=0; i < numberOfPeople; i++)
		{
			final int index = i;
			threadPool.submit( () -> 
			{
				List<Module> modules = Module.getModules();
				
				long start = stop - (long)(ONE_HUNDRED_YEARS * random.nextDouble());
	//			System.out.format("Born : %s\n", Instant.ofEpochMilli(start).toString());
				Person person = new Person(System.currentTimeMillis());
				person.attributes.put(Person.ID,  UUID.randomUUID().toString());
				person.attributes.put(Person.BIRTHDATE, start);
				person.events.create(start, Event.BIRTH, "Generator.run", true);
				person.attributes.put(Person.NAME, faker.name().name());
				person.attributes.put(Person.SOCIOECONOMIC_CATEGORY, "Middle"); // High Middle Low
				person.attributes.put(Person.RACE, "White"); // "White", "Native" (Native American), "Hispanic", "Black", "Asian", and "Other"
				person.attributes.put(Person.GENDER, ThreadLocalRandom.current().nextBoolean() ? "M" : "F");

				Location.PointWrapper pw = Location.selectPoint(null);
				person.attributes.put(Person.ADDRESS, faker.address().streetAddress(ThreadLocalRandom.current().nextBoolean()));
				person.attributes.put(Person.CITY, pw.city);
				person.attributes.put(Person.STATE, "MA");
				person.attributes.put(Person.ZIP, Location.getZipCode(pw.city));
				person.attributes.put(Person.COORDINATE, pw.point);

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
	}
}
