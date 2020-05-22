package org.mitre.synthea.world.concepts;

import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.math3.distribution.EnumeratedDistribution;
import org.apache.commons.math3.util.Pair;
import org.mitre.synthea.helpers.Utilities;

/**
 * Representation of a sample of information from NHANES - https://www.cdc.gov/nchs/nhanes/index.htm
 * Synthea contains a file that is a sample of NHANES information from 1999 - 2015 where heights
 * and weights were recorded for individuals between 24 and 36 months old.
 */
public class NHANESSample implements Serializable {
  // NHANES ID
  public int id;
  // Sex
  public int sex;
  // Age in months
  public int agem;
  // Weight
  public double wt;
  // Height
  public double ht;
  // Sample weight as assigned by NHANES
  public double swt;
  // Race code
  public String racec;
  // BMI computed from height and weight
  public double bmi;
  // Weighted probability that this sample should be selected relative to the other selected samples
  public double prob;

  /**
   * Load the NHANES samples from resources.
   * @return A list of samples.
   */
  public static List<NHANESSample> loadSamples() {
    CsvMapper mapper = new CsvMapper();
    List<NHANESSample> samples = new LinkedList();
    CsvSchema schema = CsvSchema.emptySchema().withHeader();
    String filename = "nhanes_two_year_olds_bmi.csv";
    try {
      String rawCSV = Utilities.readResource(filename);
      MappingIterator<NHANESSample> it =
          mapper.readerFor(NHANESSample.class).with(schema).readValues(rawCSV);
      while (it.hasNextValue()) {
        samples.add(it.nextValue());
      }
    } catch (Exception e) {
      System.err.println("ERROR: unable to load CSV: " + filename);
      e.printStackTrace();
      throw new RuntimeException(e);
    }
    return samples;
  }

  /**
   * Load the NHANES samples from resources into an EnumeratedDistribution. The samples,
   * probabilities, which were calculated from the sample weights are used to create the
   * distribution. This means that when sampling values from the distribution, the possibility of
   * getting a particular value will be properly weighted.
   * @return the weighted distribution of NHANES Samples
   */
  public static EnumeratedDistribution<NHANESSample> loadDistribution() {
    List<NHANESSample> samples = loadSamples();
    @SuppressWarnings("rawtypes")
    List sampleWeights = samples.stream().map(i -> new Pair(i, i.prob)).collect(toList());
    return new EnumeratedDistribution(sampleWeights);
  }
}
