package org.mitre.synthea.world.concepts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

/**
 * BirthStatistics determines when a mother will give birth, the sex of
 * the baby, and the newborn height and weight.
 */
public class BirthStatistics {
  public static final String BIRTH_WEEK = "pregnancy_birth_week";
  public static final String BIRTH_DATE = "pregnancy_birth_date";
  public static final String BIRTH_WEIGHT = "pregnancy_birth_weight";
  public static final String BIRTH_HEIGHT = "pregnancy_birth_height";
  public static final String BIRTH_SEX = "pregnancy_birth_sex";
  
  /** Default birth weight. */
  public static final double DEFAULT_WEIGHT = 3.5; // kilograms (kg)
  /** Default birth height. */
  public static final double DEFAULT_HEIGHT = 51.0; // centimeters (cm)

  private static List<? extends Map<String,String>> WEIGHT_DATA = loadData();

  private static List<? extends Map<String,String>> loadData() {
    String filename = Config.get("generate.birthweights.default_file");
    List<? extends Map<String,String>> csv = null;
    try {
      String resource = Utilities.readResource(filename);
      csv = SimpleCSV.parse(resource);
    } catch (Exception e) {
      System.err.println("Failed to load default birth weight file!");
      e.printStackTrace();
    }
    return csv;
  }

  /**
   * Sets attributes on the mother on when her baby will be born,
   * the baby sex, and the birth height and weight.
   * 
   * These attributes will be overridden on subsequent pregnancies.
   * 
   * @param mother The baby's mother.
   * @param time The time.
   */
  public void setBirthStatistics(Person mother, long time) {
    // Ignore men, they cannot become pregnant.
    if (mother.attributes.get(Person.GENDER) == "M") {
      return;
    }

    // Ignore women who are not pregnant.
    if (!mother.attributes.containsKey("pregnant") ||
        ((Boolean) mother.attributes.get("pregnant")) == false) {
      return;
    }

    // Boy or Girl?
    String baby_sex = null;
    if (mother.random.nextBoolean()) {
      baby_sex = "M";
    } else {
      baby_sex = "F";
    }

    // If there was no weight data, set some default values.
    if (WEIGHT_DATA == null) {
      mother.attributes.put(BIRTH_SEX, baby_sex);
      mother.attributes.put(BIRTH_WEEK, 40);
      mother.attributes.put(BIRTH_HEIGHT, DEFAULT_HEIGHT);
      mother.attributes.put(BIRTH_WEIGHT, DEFAULT_WEIGHT);
      return;
    }

    // Is the mother hispanic?
    boolean hispanic = isHispanic(mother);

    // Set up some temporary variables...
    double max = 0;
    boolean rhispanic;
    String rsex;
    double x;
    
    // Get the max weight of the rows...
    for (Map<String, String> row : WEIGHT_DATA) {
      rhispanic = Boolean.parseBoolean(row.get("hispanic_mother"));
      rsex = row.get("baby_sex");
      x = Double.parseDouble(row.get("sum"));
      
      if (rhispanic == hispanic &&
          rsex.equals(baby_sex) &&
          x > max) {
        max = x;
      }
    }

    // When will the baby be born?
    Map<String, String> data = null;
    double roll = mother.rand(0, max);
    for (Map<String, String> row : WEIGHT_DATA) {
      rhispanic = Boolean.parseBoolean(row.get("hispanic_mother"));
      rsex = row.get("baby_sex");
      x = Double.parseDouble(row.get("sum"));
      
      if (rhispanic == hispanic &&
          rsex.equals(baby_sex) &&
          x < roll) {
        data = row;
      }
    }
    long week = Long.parseLong(data.get("lmp_gestational_age"));
    mother.attributes.put(BIRTH_WEEK, week);
    mother.attributes.put(BIRTH_DATE, (time + Utilities.convertTime("weeks", week)));

    // How much will the baby weigh?
    max = Double.parseDouble(data.get("weight"));
    roll = mother.rand(0, max);
    List<String> weights = new ArrayList<String>(data.keySet());
    weights.remove("hispanic_mother");
    weights.remove("baby_sex");
    weights.remove("lmp_gestational_age");
    weights.remove("weight");
    weights.remove("sum");
    weights.sort(null);

    for (String weight : weights) {
      x = Double.parseDouble(data.get(weight));
      if (roll > x) {
        break;
      }
      x = Double.parseDouble(weight) / 1000; // convert from grams to kilograms
      mother.attributes.put(BIRTH_WEIGHT, x);
    }
    
    // How long will the baby be?
    mother.attributes.put(BIRTH_HEIGHT, DEFAULT_HEIGHT);
  }

  /**
   * Check whether or not the mother is hispanic.
   * @param mother The baby's mother.
   * @return True if the mother is hispanic, otherwise false.
   */
  private boolean isHispanic(Person mother) {
    String race = (String) mother.attributes.get(Person.RACE);
    String ethnicity = (String) mother.attributes.get(Person.ETHNICITY);
    return (race.equalsIgnoreCase("hispanic") || ethnicity.equalsIgnoreCase("hispanic"));
  }
}
