package org.mitre.synthea.engine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.mitre.synthea.datastore.DataStore;
import org.mitre.synthea.editors.GrowthDataErrorsEditor;
import org.mitre.synthea.export.CDWExporter;
import org.mitre.synthea.export.Exporter;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.TransitionMetrics;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.DeathModule;
import org.mitre.synthea.modules.EncounterModule;
import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.modules.LifecycleModule;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.Costs;
import org.mitre.synthea.world.concepts.VitalSign;
import org.mitre.synthea.world.geography.Demographics;
import org.mitre.synthea.world.geography.Location;

/**
 * Generator creates a population by running the generic modules each timestep per Person.
 */
public class Generator {

  public DataStore database;
  public GeneratorOptions options;
  private Random random;
  public long timestep;
  public long stop;
  public Map<String, AtomicInteger> stats;
  public Location location;
  private AtomicInteger totalGeneratedPopulation;
  private String logLevel;
  private boolean onlyAlivePatients;
  private boolean onlyDeadPatients;
  private boolean onlyVeterans;
  public TransitionMetrics metrics;
  public static String DEFAULT_STATE = "Massachusetts";
  private Exporter.ExporterRuntimeOptions exporterRuntimeOptions;

  /**
   * Used only for testing and debugging. Populate this field to keep track of all patients
   * generated, living or dead, during a simulation. Note that this may result in significantly
   * increased memory usage as patients cannot be GC'ed.
   */
  List<Person> internalStore;

  /**
   * A filename predicate used to filter a subset of modules. Helpful when testing a particular
   * module. Use "-m filename" on the command line to filter which modules get loaded.
   */
  Predicate<String> modulePredicate;
  
  private static final String TARGET_AGE = "target_age";

  /**
   * Helper class following the "Parameter Object" pattern.
   * This class provides the default values for Generator, or alternatives may be set.
   */
  public static class GeneratorOptions {
    public int population = Integer.parseInt(Config.get("generate.default_population", "1"));
    public long seed = System.currentTimeMillis();
    public long clinicianSeed = seed;
    /** Population as exclusively live persons or including deceased.
     * True for live, false includes deceased */
    public boolean overflow = true;
    /** Gender to be generated. M for Male, F for Female, null for any. */
    public String gender;
    /** Age range applies. */
    public boolean ageSpecified = false;
    /** Minimum age of people to be generated. Defaults to zero. */
    public int minAge = 0;
    /** Maximum age of people to be generated. Defaults to 140. */
    public int maxAge = 140;
    public String city;
    public String state;
    public List<String> enabledModules;
    /** File used to initialize a population. */
    public File initialPopulationSnapshotPath;
    /** File used to store a population snapshot. */
    public File updatedPopulationSnapshotPath;
    /** Time period in days to evolve the population loaded from initialPopulationSnapshotPath. A
     *  value of -1 will evolve the population to the current system time.
     */
    public int daysToTravelForward = -1;
  }
  
  /**
   * Create a Generator, using all default settings.
   */
  public Generator() {
    this(new GeneratorOptions(), new Exporter.ExporterRuntimeOptions());
  }

  /**
   * Create a Generator, with the given population size.
   * All other settings are left as defaults.
   * 
   * @param population Target population size
   */
  public Generator(int population) {
    this(new GeneratorOptions(), new Exporter.ExporterRuntimeOptions());
    options.population = population;
    init();
  }
  
  /**
   * Create a Generator, with the given population size and seed.
   * All other settings are left as defaults.
   * 
   * @param population Target population size
   * @param seed Seed used for randomness
   */
  public Generator(int population, long seed, long clinicianSeed) {
    this(new GeneratorOptions(), new Exporter.ExporterRuntimeOptions());
    options.population = population;
    options.seed = seed;
    options.clinicianSeed = clinicianSeed;
    init();
  }

  /**
   * Create a Generator, with the given options.
   * @param o Desired configuration options
   */
  public Generator(GeneratorOptions o) {
    this(o, new Exporter.ExporterRuntimeOptions());
  }
  
  /**
   * Create a Generator, with the given options.
   * @param o Desired configuration options
   * @param ero Desired exporter options
   */
  public Generator(GeneratorOptions o, Exporter.ExporterRuntimeOptions ero) {
    options = o;
    exporterRuntimeOptions = ero;
    if (options.updatedPopulationSnapshotPath != null) {
      exporterRuntimeOptions.deferExports = true;
      internalStore = Collections.synchronizedList(new LinkedList<>());
    }
    init();
  }

  private void init() {
    String dbType = Config.get("generate.database_type");

    switch (dbType) {
      case "in-memory":
        this.database = new DataStore(false);
        break;
      case "file":
        this.database = new DataStore(true);
        break;
      case "none":
        this.database = null;
        break;
      default:
        throw new IllegalArgumentException(
            "Unexpected value for config setting generate.database_type: '" + dbType
                + "' . Valid values are file, in-memory, or none.");
    }

    if (options.state == null) {
      options.state = DEFAULT_STATE;
    }
    int stateIndex = Location.getIndex(options.state);
    if (Boolean.parseBoolean(Config.get("exporter.cdw.export"))) {
      CDWExporter.getInstance().setKeyStart((stateIndex * 1_000_000) + 1);
    }

    this.random = new Random(options.seed);
    this.timestep = Long.parseLong(Config.get("generate.timestep"));
    this.stop = System.currentTimeMillis();

    this.location = new Location(options.state, options.city);

    this.logLevel = Config.get("generate.log_patients.detail", "simple");

    this.onlyDeadPatients = Boolean.parseBoolean(Config.get("generate.only_dead_patients"));
    this.onlyAlivePatients = Boolean.parseBoolean(Config.get("generate.only_alive_patients"));
    //If both values are set to true, then they are both set back to the default
    if (this.onlyDeadPatients && this.onlyAlivePatients) {
      Config.set("generate.only_dead_patients", "false");
      Config.set("generate.only_alive_patients", "false");
      this.onlyDeadPatients = false;
      this.onlyAlivePatients = false;
    }

    this.onlyVeterans = Boolean.parseBoolean(Config.get("generate.veteran_population_override"));
    this.totalGeneratedPopulation = new AtomicInteger(0);
    this.stats = Collections.synchronizedMap(new HashMap<String, AtomicInteger>());
    this.modulePredicate = getModulePredicate();

    stats.put("alive", new AtomicInteger(0));
    stats.put("dead", new AtomicInteger(0));

    if (Boolean.parseBoolean(
          Config.get("generate.track_detailed_transition_metrics", "false"))) {
      this.metrics = new TransitionMetrics();
    }

    // initialize hospitals
    Provider.loadProviders(location, options.clinicianSeed);
    // Initialize Payers
    Payer.loadPayers(location);
    // ensure modules load early
    List<String> coreModuleNames = getModuleNames(Module.getModules(path -> false));
    List<String> moduleNames = getModuleNames(Module.getModules(modulePredicate)); 
    Costs.loadCostData(); // ensure cost data loads early
    
    String locationName;
    if (options.city == null) {
      locationName = options.state;
    } else {
      locationName = options.city + ", " + options.state;
    }
    System.out.println("Running with options:");
    System.out.println(String.format("Population: %d\nSeed: %d\nProvider Seed:%d\nLocation: %s",
        options.population, options.seed, options.clinicianSeed, locationName));
    System.out.println(String.format("Min Age: %d\nMax Age: %d",
        options.minAge, options.maxAge));
    if (options.gender != null) {
      System.out.println(String.format("Gender: %s", options.gender));
    }
    if (options.enabledModules != null) {
      moduleNames.removeAll(coreModuleNames);
      moduleNames.sort(String::compareToIgnoreCase);
      System.out.println("Modules: " + String.join("\n       & ", moduleNames));
      System.out.println(String.format("       > [%d loaded]", moduleNames.size()));
    }

    if (Boolean.parseBoolean(
        Config.get("growtherrors", "false"))) {
      HealthRecordEditors hrm = HealthRecordEditors.getInstance();
      hrm.registerEditor(new GrowthDataErrorsEditor());
    }
  }

  /**
   * Extracts a list of names from the supplied list of modules.
   * @param modules A collection of modules
   * @return A list of module names.
   */
  private List<String> getModuleNames(List<Module> modules) {
    return modules.stream()
            .map(m -> m.name)
            .collect(Collectors.toList());
  }
  
  /**
   * Generate the population, using the currently set configuration settings.
   */
  public void run() {
    ExecutorService threadPool = Executors.newFixedThreadPool(8);

    if (options.initialPopulationSnapshotPath != null) {
      FileInputStream fis = null;
      List<Person> initialPopulation = null;
      try {
        fis = new FileInputStream(options.initialPopulationSnapshotPath);
        ObjectInputStream ois = new ObjectInputStream(fis);
        initialPopulation = (List<Person>) ois.readObject();
        ois.close();
      } catch (Exception ex) {
        System.out.printf("Unable to load population snapshot, error: %s", ex.getMessage());
      }
      if (initialPopulation != null && initialPopulation.size() > 0) {
        // default is to run until current system time.
        if (options.daysToTravelForward > 0) {
          stop = initialPopulation.get(0).lastUpdated 
                  + Utilities.convertTime("days", options.daysToTravelForward);
        }
        for (int i = 0; i < initialPopulation.size(); i++) {
          final int index = i;
          final Person p = initialPopulation.get(i);        
          threadPool.submit(() -> updateRecordExportPerson(p, index));
        }
      }
    } else {
      for (int i = 0; i < this.options.population; i++) {
        final int index = i;
        final long seed = this.random.nextLong();
        threadPool.submit(() -> generatePerson(index, seed));
      }
    }

    try {
      threadPool.shutdown();
      while (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
        System.out.println("Waiting for threads to finish... " + threadPool);
      }
    } catch (InterruptedException e) {
      System.out.println("Generator interrupted. Attempting to shut down associated thread pool.");
      threadPool.shutdownNow();
    }

    // have to store providers at the end to correctly capture utilization #s
    // TODO - de-dup hospitals if using a file-based database?
    if (database != null) {
      database.store(Provider.getProviderList());
    }

    // Save a snapshot of the generated population using Java Serialization
    if (options.updatedPopulationSnapshotPath != null) {
      FileOutputStream fos = null;
      try {
        fos = new FileOutputStream(options.updatedPopulationSnapshotPath);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(internalStore);
        oos.close();
        fos.close();
      } catch (Exception ex) {
        System.out.printf("Unable to save population snapshot, error: %s", ex.getMessage());
      }
    }
    Exporter.runPostCompletionExports(this, exporterRuntimeOptions);

    System.out.printf("Records: total=%d, alive=%d, dead=%d\n", totalGeneratedPopulation.get(),
            stats.get("alive").get(), stats.get("dead").get());

    if (this.metrics != null) {
      metrics.printStats(totalGeneratedPopulation.get(), Module.getModules(getModulePredicate()));
    }
  }
  
  /**
   * Generate a completely random Person. The returned person will be alive at the end of the
   * simulation. This means that if in the course of the simulation the person dies, a new person
   * will be started to replace them. 
   * The seed used to generate the person is randomized as well.
   * 
   * @param index Target index in the whole set of people to generate
   * @return generated Person
   */
  public Person generatePerson(int index) {
    // System.currentTimeMillis is not unique enough
    long personSeed = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    return generatePerson(index, personSeed);
  }
  
  /**
   * Generate a random Person, from the given seed. The returned person will be alive at the end of
   * the simulation. This means that if in the course of the simulation the person dies, a new
   * person will be started to replace them. Note also that if the person dies, the seed to produce
   * them can't be re-used (otherwise the new person would die as well) so a new seed is picked,
   * based on the given seed.
   * 
   * @param index
   *          Target index in the whole set of people to generate
   * @param personSeed
   *          Seed for the random person
   * @return generated Person
   */
  public Person generatePerson(int index, long personSeed) {
    Person person = null;
    try {
      boolean isAlive = true;
      int tryNumber = 0; // number of tries to create these demographics
      Random randomForDemographics = new Random(personSeed);
      Map<String, Object> demoAttributes = randomDemographics(randomForDemographics);

      do {
        person = createPerson(personSeed, demoAttributes);
        long finishTime = person.lastUpdated + timestep;

        isAlive = person.alive(finishTime);

        if (isAlive && onlyDeadPatients) {
          // rotate the seed so the next attempt gets a consistent but different one
          personSeed = new Random(personSeed).nextLong();
          continue;
          // skip the other stuff if the patient is alive and we only want dead patients
          // note that this skips ahead to the while check and doesn't automatically re-loop
        }

        if (!isAlive && onlyAlivePatients) {
          // rotate the seed so the next attempt gets a consistent but different one
          personSeed = new Random(personSeed).nextLong();
          continue;
          // skip the other stuff if the patient is dead and we only want alive patients
          // note that this skips ahead to the while check and doesn't automatically re-loop
        }

        recordPerson(person, index);

        tryNumber++;
        if (!isAlive) {
          // rotate the seed so the next attempt gets a consistent but different one
          personSeed = new Random(personSeed).nextLong();

          // if we've tried and failed > 10 times to generate someone over age 90
          // and the options allow for ages as low as 85
          // reduce the age to increase the likelihood of success
          if (tryNumber > 10 && (int)person.attributes.get(TARGET_AGE) > 90
              && (!options.ageSpecified || options.minAge <= 85)) {
            // pick a new target age between 85 and 90
            int newTargetAge = randomForDemographics.nextInt(5) + 85;
            // the final age bracket is 85-110, but our patients rarely break 100
            // so reducing a target age to 85-90 shouldn't affect numbers too much
            demoAttributes.put(TARGET_AGE, newTargetAge);
            long birthdate = birthdateFromTargetAge(newTargetAge, randomForDemographics);
            demoAttributes.put(Person.BIRTHDATE, birthdate);
          }
        }

        // TODO - export is DESTRUCTIVE when it filters out data
        // this means export must be the LAST THING done with the person
        Exporter.export(person, finishTime, exporterRuntimeOptions);
      } while ((!isAlive && !onlyDeadPatients && this.options.overflow)
          || (isAlive && onlyDeadPatients));
      // if the patient is alive and we want only dead ones => loop & try again
      //  (and dont even export, see above)
      // if the patient is dead and we only want dead ones => done
      // if the patient is dead and we want live ones => loop & try again
      //  (but do export the record anyway)
      // if the patient is alive and we want live ones => done
    } catch (Throwable e) {
      // lots of fhir things throw errors for some reason
      e.printStackTrace();
      throw e;
    }
    return person;
  }
  
  /**
   * Update person record to stop time, record the entry and export record.
   */
  public Person updateRecordExportPerson(Person person, int index) {
    updatePerson(person);
    recordPerson(person, index);
    long finishTime = person.lastUpdated + timestep;
    Exporter.export(person, finishTime, exporterRuntimeOptions);
    return person;
  }

  /**
   * Update a previously created person from the time they were last updated until Generator.stop or
   * they die, whichever comes sooner.
   * @param person the previously created person to update
   */
  public void updatePerson(Person person) {
    HealthInsuranceModule healthInsuranceModule = new HealthInsuranceModule();
    EncounterModule encounterModule = new EncounterModule();

    long time = person.lastUpdated;
    while (person.alive(time) && time < stop) {

      healthInsuranceModule.process(person, time + timestep);
      encounterModule.process(person, time);

      Iterator<Module> iter = person.currentModules.iterator();
      while (iter.hasNext()) {
        Module module = iter.next();
        // System.out.format("Processing module %s\n", module.name);
        if (module.process(person, time)) {
          // System.out.format("Removing module %s\n", module.name);
          iter.remove(); // this module has completed/terminated.
        }
      }
      encounterModule.endEncounterModuleEncounters(person, time);
      person.lastUpdated = time;
      HealthRecordEditors.getInstance().executeAll(
              person, person.record, time, timestep, person.random);
      time += timestep;
    }

    DeathModule.process(person, time);
  }
  
  /**
   * Create a new person and update them until until Generator.stop or
   * they die, whichever comes sooner.
   * @param personSeed Seed for the random person
   * @param demoAttributes Demographic attributes for the new person, {@link #randomDemographics}
   * @return the new person
   */
  public Person createPerson(long personSeed, Map<String, Object> demoAttributes) {
    Person person = new Person(personSeed);
    person.populationSeed = this.options.seed;
    person.attributes.putAll(demoAttributes);
    person.attributes.put(Person.LOCATION, location);
    person.lastUpdated = (long) demoAttributes.get(Person.BIRTHDATE);

    LifecycleModule.birth(person, person.lastUpdated);
    person.currentModules = Module.getModules(modulePredicate);

    updatePerson(person);
    
    return person;
  }
  
  /**
   * Create a set of random demographics.
   * @param seed The random seed to use
   * @return demographics
   */
  public Map<String, Object> randomDemographics(Random seed) {
    Demographics city = location.randomCity(seed);
    Map<String, Object> demoAttributes = pickDemographics(seed, city);
    return demoAttributes;
  }
  
  /**
   * Record the person using whatever tracking mechanisms are currently configured.
   * @param person the person to record
   * @param index the index of the person being recorded, e.g. if generating 100 people, the index
   *     would identify which of those 100 is being recorded.
   */
  public void recordPerson(Person person, int index) {
    long finishTime = person.lastUpdated + timestep;
    boolean isAlive = person.alive(finishTime);
    
    if (database != null) {
      database.store(person);
    }

    if (internalStore != null) {
      internalStore.add(person);
    }

    if (this.metrics != null) {
      metrics.recordStats(person, finishTime, Module.getModules(modulePredicate));
    }

    if (!this.logLevel.equals("none")) {
      writeToConsole(person, index, finishTime, isAlive);
    }

    String key = isAlive ? "alive" : "dead";

    AtomicInteger count = stats.get(key);
    count.incrementAndGet();

    totalGeneratedPopulation.incrementAndGet();
  }

  private synchronized void writeToConsole(Person person, int index, long time, boolean isAlive) {
    // this is synchronized to ensure all lines for a single person are always printed 
    // consecutively
    String deceased = isAlive ? "" : "DECEASED";
    System.out.format("%d -- %s (%d y/o %s) %s, %s %s\n", index + 1,
        person.attributes.get(Person.NAME), person.ageInYears(time),
        person.attributes.get(Person.GENDER),
        person.attributes.get(Person.CITY), person.attributes.get(Person.STATE),
        deceased);

    if (this.logLevel.equals("detailed")) {
      System.out.println("ATTRIBUTES");
      for (String attribute : person.attributes.keySet()) {
        System.out.format("  * %s = %s\n", attribute, person.attributes.get(attribute));
      }
      System.out.format("SYMPTOMS: %d\n", person.symptomTotal());
      System.out.println(person.record.textSummary());
      System.out.println("VITAL SIGNS");
      for (VitalSign vitalSign : person.vitalSigns.keySet()) {
        System.out.format("  * %25s = %6.2f\n", vitalSign,
            person.getVitalSign(vitalSign, time).doubleValue());
      }
      System.out.println("-----");
    }
  }

  private Map<String, Object> pickDemographics(Random random, Demographics city) {
    Map<String, Object> out = new HashMap<>();
    out.put(Person.CITY, city.city);
    out.put(Person.STATE, city.state);
    out.put("county", city.county);
    
    String race = city.pickRace(random);
    out.put(Person.RACE, race);
    String ethnicity = city.pickEthnicity(random);
    out.put(Person.ETHNICITY, ethnicity);
    String language = city.languageFromRaceAndEthnicity(race, ethnicity, random);
    out.put(Person.FIRST_LANGUAGE, language);

    String gender;
    if (options.gender != null) {
      gender = options.gender;
    } else {
      gender = city.pickGender(random);
      if (gender.equalsIgnoreCase("male") || gender.equalsIgnoreCase("M")) {
        gender = "M";
      } else {
        gender = "F";
      }
    }
    out.put(Person.GENDER, gender);

    // Socioeconomic variables of education, income, and education are set.
    String education = city.pickEducation(random);
    out.put(Person.EDUCATION, education);
    double educationLevel = city.educationLevel(education, random);
    out.put(Person.EDUCATION_LEVEL, educationLevel);

    int income = city.pickIncome(random);
    out.put(Person.INCOME, income);
    double incomeLevel = city.incomeLevel(income);
    out.put(Person.INCOME_LEVEL, incomeLevel);

    double occupation = random.nextDouble();
    out.put(Person.OCCUPATION_LEVEL, occupation);

    double sesScore = city.socioeconomicScore(incomeLevel, educationLevel, occupation);
    out.put(Person.SOCIOECONOMIC_SCORE, sesScore);
    out.put(Person.SOCIOECONOMIC_CATEGORY, city.socioeconomicCategory(sesScore));

    if (this.onlyVeterans) {
      out.put("veteran_population_override", Boolean.TRUE);
    }

    int targetAge;
    if (options.ageSpecified) {
      targetAge = 
          (int) (options.minAge + ((options.maxAge - options.minAge) * random.nextDouble()));
    } else {
      targetAge = city.pickAge(random);
    }
    out.put(TARGET_AGE, targetAge);

    long birthdate = birthdateFromTargetAge(targetAge, random);
    out.put(Person.BIRTHDATE, birthdate);
    
    return out;
  }
  
  private long birthdateFromTargetAge(long targetAge, Random random) {
    long earliestBirthdate = stop - TimeUnit.DAYS.toMillis((targetAge + 1) * 365L + 1);
    long latestBirthdate = stop - TimeUnit.DAYS.toMillis(targetAge * 365L);
    return 
        (long) (earliestBirthdate + ((latestBirthdate - earliestBirthdate) * random.nextDouble()));
  }
  
  private Predicate<String> getModulePredicate() {
    if (options.enabledModules == null) {
      return path -> true;
    }
    FilenameFilter filenameFilter = new WildcardFileFilter(options.enabledModules, 
        IOCase.INSENSITIVE);
    return path -> filenameFilter.accept(null, path);
  }
}
