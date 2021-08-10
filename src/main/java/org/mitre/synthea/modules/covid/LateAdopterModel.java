package org.mitre.synthea.modules.covid;

import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

import java.io.Serializable;

/**
 * The COVID-19 Immunization Module uses actual data to predict whether someone will get a vaccine
 * during the time period for which data is available. As of writing, this is December 2020
 * through July 2021. The data provides administration information for particular age ranges. This
 * is used to model the likelihood that an individual will get the vaccine.
 *
 * What is unknown is the likelihood that someone will get the vaccine for the period after which
 * we have data. Just because someone who is 70 years old has not been vaccinated by July of 2021
 * does not mean that they will never be vaccinated.
 *
 * This class models those individuals. Chance of vaccination is again based on age, with the chance
 * of vaccination declining by half every two week. Eventually, the chance of vaccination hits a
 * threshold where the individual will move into a state where they will never attempt to get the
 * vaccine.
 *
 * This model assigns a much higher chance for those 15 and younger to attempt to get the vaccine
 * as that population has not been eligible for very long at the time of writing.
 */
public class LateAdopterModel implements Serializable {
  public static double LOWEST_CHANCE_THRESHOLD = 0.001;

  private double chanceOfGettingShot;
  private long lastUpdated;

  public LateAdopterModel(Person person, long time) {
    int age = person.ageInYears(time);
    // Totally made up guesses as to how likely someone is to get a COVID-19 vaccine in the period
    // of time after we have actual data
    if (age <= 15) {
      this.chanceOfGettingShot = 0.4;
    } else if (age <= 40) {
      this.chanceOfGettingShot = 0.2;
    } else {
      this.chanceOfGettingShot = 0.1;
    }

    this.lastUpdated = time;
  }

  public double getChanceOfGettingShot() {
    return chanceOfGettingShot;
  }

  public void setChanceOfGettingShot(double chanceOfGettingShot) {
    this.chanceOfGettingShot = chanceOfGettingShot;
  }

  public long getLastUpdated() {
    return lastUpdated;
  }

  public void setLastUpdated(long lastUpdated) {
    this.lastUpdated = lastUpdated;
  }

  public boolean willGetShot(Person person, long time) {
    if (time <= (lastUpdated + Utilities.convertTime("weeks", 2))) {
      return false;
    } else {
      if (person.rand() <= chanceOfGettingShot) {
        return true;
      } else {
        chanceOfGettingShot = chanceOfGettingShot / 2;
        lastUpdated = time;
        return false;
      }
    }
  }

  public boolean isNotGettingShot() {
    return chanceOfGettingShot < LOWEST_CHANCE_THRESHOLD;
  }

}
