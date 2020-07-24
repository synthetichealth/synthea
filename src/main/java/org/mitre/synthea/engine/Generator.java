package org.mitre.synthea.engine;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Patient;
import org.mitre.synthea.datastore.DataStore;
import org.mitre.synthea.export.CDWExporter;
import org.mitre.synthea.export.Exporter;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.TransitionMetrics;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.input.FixedRecord;
import org.mitre.synthea.input.RecordGroup;
import org.mitre.synthea.modules.DeathModule;
import org.mitre.synthea.modules.EncounterModule;
import org.mitre.synthea.editors.GrowthDataErrorsEditor;
import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.modules.LifecycleModule;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.Costs;
import org.mitre.synthea.world.concepts.PediatricGrowthTrajectory;
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
  private List<RecordGroup> recordGroups;

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
    /** When Synthea is used as a standalone library, this directory holds
     * any locally created modules. */
    public File localModuleDir;
    public File fixedRecordPath;
    public List<String> enabledModules;
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
    options = o;
    exporterRuntimeOptions = new Exporter.ExporterRuntimeOptions();
    init();
  }
  
  /**
   * Create a Generator, with the given options.
   * @param o Desired configuration options
   * @param ero Desired exporter options
   */
  public Generator(GeneratorOptions o, Exporter.ExporterRuntimeOptions ero) {
    options = o;
    exporterRuntimeOptions = ero;
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
    if (options.localModuleDir != null) {
      Module.addModules(options.localModuleDir);
    }
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

    // Import the fixed patient demographics records file, if a file path is given.
    if (this.options.fixedRecordPath != null) {
      importFixedPatientDemographicsFile();
    }

    ExecutorService threadPool = Executors.newFixedThreadPool(8);

    // Generate patients up to the specified population size.
    for (int i = 0; i < this.options.population; i++) {
      final int index = i;
      final long seed = this.random.nextLong();
      threadPool.submit(() -> generatePerson(index, seed));
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

    Exporter.runPostCompletionExports(this);

    System.out.println(stats);

    if (this.metrics != null) {
      metrics.printStats(totalGeneratedPopulation.get(), Module.getModules(getModulePredicate()));
    }
  }

  /**
   * Imports the fixed demographics records file when using fixed patient
   * demographics.
   * 
   * @return A list of the groups of records imported.
   */
  public List<RecordGroup> importFixedPatientDemographicsFile() {
    Gson gson = new Gson();
      Type listType = new TypeToken<List<RecordGroup>>() {}.getType();
      try {
        System.out.println("Loading fixed patient demographic records file " + this.options.fixedRecordPath);
        this.recordGroups = gson.fromJson(new FileReader(this.options.fixedRecordPath), listType);
        int linkIdStart = 100000;
        for (int i = 0; i < this.recordGroups.size(); i++) {
          this.recordGroups.get(i).linkId = linkIdStart + i;
        }
      } catch (FileNotFoundException e) {
        throw new RuntimeException("Couldn't open the fixed patient demographics records file", e);
      }
      // Update the population paramater to reflect the number of patients in the fixed demographic records file.
      this.options.population = this.recordGroups.size();
      // Return the record groups.
      return recordGroups;
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
    // If generating fixed record patients, then run generatePersonWithFixedRecord(index, personSeed)
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
      int tryNumber = 0; // Number of tries to create these demographics
      Random randomForDemographics = new Random(personSeed);

      Map<String, Object> demoAttributes = randomDemographics(randomForDemographics);
      if(this.recordGroups != null){
        // Pick fixed demographics if a fixed demographics record file is used.
        demoAttributes = pickFixedDemographics(index, random);
      }

      int providerCount = 0;
      int providerMinimum = 1;

      if (this.recordGroups != null) {
        // If fixed records are used, there must be 1 provider for each of this person's records.
        RecordGroup recordGroup = this.recordGroups.get(index);
        providerMinimum = recordGroup.count;
      }
      
      do {
        person = createPerson(personSeed, demoAttributes);
        long finishTime = person.lastUpdated + timestep;

        isAlive = person.alive(finishTime);
        providerCount = person.providerCount();

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

        // For fixed records, the person must have 1 provider per record.
        if (providerCount < providerMinimum) {
          // rotate the seed so the next attempt gets a consistent but different one
          personSeed = new Random(personSeed).nextLong();
          tryNumber++;
          if (tryNumber > 10) {
            System.out.println("Couldn't get enough providers for " + person.attributes.get(Person.FIRST_NAME) + " " +
                person.attributes.get(Person.LAST_NAME));
          }
          continue;
          // skip the other stuff if the patient has less providers than the minimum
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
          || (isAlive && onlyDeadPatients) || (providerCount < providerMinimum));
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
   * Create a set of random demographics.
   * @param seed The random seed to use.
   * @return A map of demographic attributes.
   */
  public Map<String, Object> randomDemographics(Random seed) {
    Demographics city = this.location.randomCity(seed);
    return pickDemographics(seed, city);
  }

  /**
   * Print out the completed person to the consol.
   * @param person The person to print.
   * @param index The number person simulated.
   * @param time The time at which they died/the simulation ended.
   * @param isAlive Whether the person to print is alive.
   */
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

  /**
   * Returns a map of demographics that have been randomly picked based on the given location.
   * @param random The random object to use.
   * @param city The city to base the demographics off of.
   * @return a Map<String, Object> of demographics
   */
  private Map<String, Object> pickDemographics(Random random, Demographics city) {
    // Output map of the generated demographc data.
    Map<String, Object> demographicsOutput = new HashMap<>();

    // Pull the person's location data.
    demographicsOutput.put(Person.CITY, city.city);
    demographicsOutput.put(Person.STATE, city.state);
    demographicsOutput.put("county", city.county);

    // Generate the person's race data based on their location.
    String race = city.pickRace(random);
    demographicsOutput.put(Person.RACE, race);
    String ethnicity = city.pickEthnicity(random);
    demographicsOutput.put(Person.ETHNICITY, ethnicity);
    String language = city.languageFromRaceAndEthnicity(race, ethnicity, random);
    demographicsOutput.put(Person.FIRST_LANGUAGE, language);

    // Generate the person's gender based on their location.
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
    demographicsOutput.put(Person.GENDER, gender);

    // Generate the person's socioeconomic variables of education, income, and occupation based on their location.
    String education = city.pickEducation(random);
    demographicsOutput.put(Person.EDUCATION, education);
    double educationLevel = city.educationLevel(education, random);
    demographicsOutput.put(Person.EDUCATION_LEVEL, educationLevel);

    int income = city.pickIncome(random);
    demographicsOutput.put(Person.INCOME, income);
    double incomeLevel = city.incomeLevel(income);
    demographicsOutput.put(Person.INCOME_LEVEL, incomeLevel);

    double occupation = random.nextDouble();
    demographicsOutput.put(Person.OCCUPATION_LEVEL, occupation);

    double sesScore = city.socioeconomicScore(incomeLevel, educationLevel, occupation);
    demographicsOutput.put(Person.SOCIOECONOMIC_SCORE, sesScore);
    demographicsOutput.put(Person.SOCIOECONOMIC_CATEGORY, city.socioeconomicCategory(sesScore));

    if (this.onlyVeterans) {
      demographicsOutput.put("veteran_population_override", Boolean.TRUE);
    }

    // Generate the person's age data.
    int targetAge;
    if (options.ageSpecified) {
      targetAge = 
          (int) (options.minAge + ((options.maxAge - options.minAge) * random.nextDouble()));
    } else {
      targetAge = city.pickAge(random);
    }
    demographicsOutput.put(TARGET_AGE, targetAge);

    long birthdate = birthdateFromTargetAge(targetAge, random);
    demographicsOutput.put(Person.BIRTHDATE, birthdate);
    
    // Return the generated demographics.
    return demographicsOutput;
  }

  /**
   * Pick a person's demographics from a fixed demographics record before generating random demographics based on the fixed values.
   * @param index The index to use.
   * @param random Random object.
   */
  private Map<String, Object> pickFixedDemographics(int index, Random random) {

    // Load the patient from the current fixed record.
    Patient newPatient = Utilities.loadFixedDemographicPatient(index);

    // Get the current recordGroup
    RecordGroup recordGroup = this.recordGroups.get(index);
    // Pull the first fixed record from the group of fixed records.
    FixedRecord fr = recordGroup.records.get(0);
    // Get the city from the location in the fixed record.
    this.location = new Location(fr.getState(), recordGroup.getSafeCity(0));
    Demographics city = location.randomCity(random);
    // Pick the demographics based on the location of the fixed record.
    Map<String, Object> demoAttributes = pickDemographics(random, city);
    // Overwrite the person's birthdate in demoAttributes with the fixed record birthdate.
    demoAttributes.put(Person.BIRTHDATE, recordGroup.getValidBirthdate(0));
    // Overwrite the person's gender.
    String g = fr.gender;
    if (g.equalsIgnoreCase("None") || StringUtils.isBlank(g)) {
      g = "F";
    }
    demoAttributes.put(Person.GENDER, g);
    // Give the person their groud of FixedRecords.
    demoAttributes.put(Person.RECORD_GROUP, recordGroup);
    demoAttributes.put(Person.LINK_ID, recordGroup.linkId);

    // Return the Demographic Attributes of the current person.
    return demoAttributes;
  }

  private long birthdateFromTargetAge(long targetAge, Random random) {
    long earliestBirthdate = stop - TimeUnit.DAYS.toMillis((targetAge + 1) * 365L + 1);
    long latestBirthdate = stop - TimeUnit.DAYS.toMillis(targetAge * 365L);
    return 
        (long) (earliestBirthdate + ((latestBirthdate - earliestBirthdate) * random.nextDouble()));
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
  
  private Predicate<String> getModulePredicate() {
    if (options.enabledModules == null) {
      return path -> true;
    }
    FilenameFilter filenameFilter = new WildcardFileFilter(options.enabledModules, 
        IOCase.INSENSITIVE);
    return path -> filenameFilter.accept(null, path);
  }
}
