package org.mitre.synthea.helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.io.FileUtils;
import org.mitre.synthea.helpers.physiology.IoMapper;
import org.mitre.synthea.helpers.physiology.PhysiologyGeneratorConfig;
import org.mitre.synthea.helpers.physiology.PreGenerator;
import org.mitre.synthea.helpers.physiology.PreGenerator.PreGeneratorArg;
import org.mitre.synthea.helpers.physiology.SimRunner;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.VitalSign;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;


/**
 * A ValueGenerator for generation of values from a physiology simulation.
 */
public class PhysiologyValueGenerator extends ValueGenerator {
  public static Path GENERATORS_PATH;
  private static ConcurrentMap<String,PhysiologyGeneratorConfig> CONFIG_CACHE
      = new ConcurrentHashMap<String,PhysiologyGeneratorConfig>();
  private SimRunner simRunner;
  private PhysiologyGeneratorConfig config;
  private VitalSign vitalSign;
  private ValueGenerator preGenerator;
  private double outputVariance;

  static {
    try {
      GENERATORS_PATH = Paths.get(ClassLoader.getSystemClassLoader()
          .getResource("physiology/generators").toURI());
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  public static void setGeneratorsPath(Path newPath) {
    GENERATORS_PATH = newPath;
  }

  /**
   * A generator of VitalSign values from a physiology simulation.
   * @param config physiology configuration file
   * @param person Person instance to generate VitalSigns for
   */
  public PhysiologyValueGenerator(PhysiologyGeneratorConfig config, SimRunner runner,
      VitalSign vitalSign,
      Person person, double outputVariance) {
    super(person);
    this.config = config;
    this.vitalSign = vitalSign;
    this.outputVariance = outputVariance;
    this.simRunner = runner;

    // Set any patient attribute default values
    if (config.getPersonAttributeDefaults() != null) {
      for (Entry<String, Object> entry : config.getPersonAttributeDefaults().entrySet()) {
        if (!person.attributes.containsKey(entry.getKey())) {
          person.attributes.put(entry.getKey(), entry.getValue());
        }
      }
    }

    // If pre-simulation generators are being used, instantiate the generator
    if (config.isUsePreGenerators()) {

      // Get the IoMapper for this VitalSign output
      IoMapper outMapper = null;
      for (IoMapper mapper : config.getOutputs()) {
        if (mapper.getType() == IoMapper.IoType.VITAL_SIGN
            && mapper.getVitalSignTarget() == vitalSign) {
          outMapper = mapper;
        }
      }

      // This shouldn't ever happen, since it wouldn't make sense to be instantiating a
      // PhysiologyValueGenerator for a VitalSign that's not in the config
      if (outMapper == null) {
        throw new RuntimeException("Unable to find corresponding IoMapper for " + vitalSign);
      }

      // Check for missing preGenerator configuration
      if (outMapper.getPreGenerator() == null) {
        throw new RuntimeException("usePreGenerators is true but no preGenerator "
            + "is defined for output " + vitalSign);
      }

      preGenerator = outMapper.getPreGenerator().getGenerator(person);
    }
  }

  /**
   * Returns a List of all PhysiologyValueGenerators defined in the configuration directory.
   * @return List of PhysiologyValueGenerator
   */
  public static List<PhysiologyValueGenerator> loadAll(Person person) {
    return loadAll(person, "");
  }

  /**
   * Loads all PhysiologyValueGenerators defined in the given generator configuration subdirectory.
   * @param person Person to generate values for
   * @param subfolder generator sub directory to load configurations from
   * @return List of PhysiologyValueGenerator
   */
  public static List<PhysiologyValueGenerator> loadAll(Person person, String subfolder) {

    String[] configExt = {"yml"};

    // Get all of the configuration files in the generator configuration path and all
    // of its subdirectories
    File baseFolder = new File(GENERATORS_PATH.toString(), subfolder);
    Collection<File> physiologyConfigFiles = FileUtils.listFiles(baseFolder, configExt, true);

    List<PhysiologyValueGenerator> allGenerators = new ArrayList<PhysiologyValueGenerator>();

    // Set the ValueGenerator for each VitalSign output in each configuration
    for (File cfgFile : physiologyConfigFiles) {
      allGenerators.addAll(PhysiologyValueGenerator.fromConfig(cfgFile, person));
    }

    return allGenerators;
  }

  /**
   * Instantiates PhysiologyValueGenerators for each VitalSign output in the generator
   * configuration at the provided path.
   *
   * @param configFile generator configuration file
   * @param person Person to generate VitalSigns for
   * @return List of PhysiologyValueGenerator instances
   */
  public static List<PhysiologyValueGenerator> fromConfig(File configFile, Person person) {
    return fromConfig(getConfig(configFile), person);
  }

  /**
   * Instantiates PhysiologyValueGenerators for each VitalSign output in the generator
   * configuration.
   *
   * @param generatorConfig generator configuration object
   * @param person Person to generate VitalSigns for
   * @return List of PhysiologyValueGenerator instances
   */
  public static List<PhysiologyValueGenerator> fromConfig(
      PhysiologyGeneratorConfig generatorConfig, Person person) {
    List<PhysiologyValueGenerator> generators = new ArrayList<PhysiologyValueGenerator>();

    SimRunner runner = new SimRunner(generatorConfig, person);

    for (IoMapper mapper : generatorConfig.getOutputs()) {
      if (mapper.getType() == IoMapper.IoType.VITAL_SIGN) {
        generators.add(new PhysiologyValueGenerator(
            generatorConfig,
            runner,
            VitalSign.fromString(mapper.getTo()),
            person, mapper.getVariance()));
      }
    }

    return generators;
  }

  /**
   * Retrieves the PhysiologyValueGenerator configuration from the given path.
   * @param configPath path to the generator configuration file
   * @return generator configuration object
   */
  public static PhysiologyGeneratorConfig getConfig(String configPath) {
    File configFile = new File(GENERATORS_PATH.toString(), configPath);
    return getConfig(configFile);
  }

  /**
   * Retrieves the PhysiologyValueGenerator configuration from the given file.
   * @param configFile generator configuration file
   * @return generator configuration object
   */
  public static synchronized PhysiologyGeneratorConfig getConfig(File configFile) {

    String relativePath;
    relativePath = GENERATORS_PATH.toUri().relativize(configFile.toURI()).getPath();

    // key is the path to the config file
    String configKey = relativePath;

    // If it exists in the cache, go ahead and get it
    if (CONFIG_CACHE.containsKey(configKey)) {
      return CONFIG_CACHE.get(configKey);
    }

    // Resource isn't already loaded, so we've got to load the resource
    System.out.println("Loading physiology generator configuration \"" + relativePath + "\"");

    FileInputStream inputStream;

    try {
      inputStream = new FileInputStream(configFile);
    } catch (FileNotFoundException ex) {
      throw new RuntimeException("PhysiologyValueGenerator configuration not found: \""
          + configFile.getPath() + "\".");
    }

    // Add type descriptions so Yaml knows how to instantiate our Lists
    Constructor constructor = new Constructor(PhysiologyGeneratorConfig.class);
    TypeDescription configDescription = new TypeDescription(PhysiologyGeneratorConfig.class);
    configDescription.addPropertyParameters("inputs", IoMapper.class);
    configDescription.addPropertyParameters("outputs", IoMapper.class);
    constructor.addTypeDescription(configDescription);
    configDescription = new TypeDescription(IoMapper.class);
    configDescription.addPropertyParameters("preGenerator", PreGenerator.class);
    constructor.addTypeDescription(configDescription);
    configDescription = new TypeDescription(PreGenerator.class);
    configDescription.addPropertyParameters("args", PreGeneratorArg.class);
    constructor.addTypeDescription(configDescription);

    // Parse the PhysiologyConfig from the yaml file
    Yaml yaml = new Yaml(constructor);
    PhysiologyGeneratorConfig config = (PhysiologyGeneratorConfig) yaml.load(inputStream);

    // Validate the configuration
    config.validate();

    // Add the config to the cache in case there are other PhysiologyValueGenerators
    // that need it
    CONFIG_CACHE.put(configKey, config);

    return config;
  }

  /**
   * Returns the VitalSign this generator targets.
   * @return VitalSign target
   */
  public VitalSign getVitalSign() {
    return vitalSign;
  }

  @Override
  public String toString() {

    final StringBuilder sb = new StringBuilder("PhysiologyValueGenerator {");

    sb.append("model=").append(config.getModel());

    sb.append(", VitalSigns=[");

    boolean firstVital = false;
    for (IoMapper mapper : config.getOutputs()) {
      if (mapper.getType() == IoMapper.IoType.VITAL_SIGN) {
        if (firstVital) {
          sb.append(", ");
        } else {
          firstVital = true;
        }
        sb.append(mapper.getVitalSignTarget().name());
      }
    }

    sb.append("], Attributes=[");

    boolean firstAttr = false;
    for (IoMapper mapper : config.getOutputs()) {
      if (mapper.getType() == IoMapper.IoType.ATTRIBUTE) {
        if (firstAttr) {
          sb.append(", ");
        } else {
          firstAttr = true;
        }
        sb.append(mapper.getTo());
      }
    }

    sb.append("]}");

    return sb.toString();
  }

  @Override
  public double getValue(long time) {
    // setInputs returns true if sufficient changes to the input
    // values has occurred
    if (simRunner.setInputs(time)) {
      simRunner.execute(time);
    }

    double result;

    // If we haven't executed the simulator yet, use the pre-simulation
    // generator values until it does run
    if (!simRunner.hasExecuted()) {
      result = preGenerator.getValue(time);
    } else {
      result = simRunner.getVitalSignValue(vitalSign) + (person.rand() - 0.5) * outputVariance;
    }

    return result;
  }

  /**
   * Sets the amount of variance to generate for the output VitalSign.
   * @param variance amount of variance
   */
  public void setOutputVariance(double variance) {
    outputVariance = variance;
  }

}
