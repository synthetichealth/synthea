package org.mitre.synthea.identity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.mitre.synthea.helpers.Utilities;

/**
 * This class represents a set of desired demographic information about a Person to be simulated.
 * Typically in Synthea, a person's demographic information is made via random weighted selections
 * and an individual stays in the same place for their entire life. An Entity can be used to
 * specify demographic information. Additionally, information can be supplied that allows the
 * simulation to mimic someone moving their primary place of residence.
 *
 * <p>
 *   This class contains basic-level demographic information, such as date of birth and gender.
 *   More detailed information is contained in Seeds. Each Entity is made up of a list of Seeds,
 *   which represent the demographic information for a Person over a specified time range.
 * </p>
 * <p>
 *   As an example, a Person can have a seed to represent their birthplace. 10 years later, their
 *   family moves, so another seed would be added to their record reflecting their new address
 * </p>
 * <p>
 *   Seeds have one or more Variants. This is a representation of have the demographic information
 *   will be when placed in the exported health record. It can be used to represent data errors or
 *   variations typically seen in demographic information, such as nicknames, typos, old addresses,
 *   etc.
 * </p>
 */
public class Entity {
  private List<Seed> seeds;
  private LocalDate dateOfBirth;
  private String gender;
  private String individualId;
  private String housingStatus;

  public Entity() {
    this.seeds = new ArrayList<>();
  }

  public List<Seed> getSeeds() {
    return seeds;
  }

  public void setSeeds(List<Seed> seeds) {
    this.seeds = seeds;
  }

  public Seed seedAt(LocalDate date) {
    return seeds.stream().filter(s -> s.getPeriod().contains(date)).findFirst().orElse(null);
  }

  /**
   * Find the seed at a particular time.
   * @param timestamp the time to find a seed
   * @return The seed that covers the time. If before the first seed, will still return the first
   *     seed
   */
  public Seed seedAt(long timestamp) {
    if (timestamp == Long.MIN_VALUE) {
      return seeds.get(0);
    }
    LocalDate date = Utilities.timestampToLocalDate(timestamp);
    return seedAt(date);
  }

  /**
   * Checks to see whether the seeds for this entity are valid. This means that there are no gaps
   * in time between all seeds. Also ensures that seeds do no overlap in time.
   * @return true if the seed periods are valid, false otherwise
   */
  public boolean validSeedPeriods() {
    boolean valid = true;

    for (int i = 1; i < seeds.size(); i++) {
      Period first = seeds.get(i - 1).getPeriod();
      Period second = seeds.get(i).getPeriod();

      if (! first.getEnd().plusDays(1).equals(second.getStart())) {
        valid = false;
      }
    }

    return valid;
  }

  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public void setDateOfBirth(LocalDate dateOfBirth) {
    this.dateOfBirth = dateOfBirth;
  }

  public String getGender() {
    return gender;
  }

  public void setGender(String gender) {
    this.gender = gender;
  }

  public String getIndividualId() {
    return individualId;
  }

  public void setIndividualId(String individualId) {
    this.individualId = individualId;
  }

  public String getHousingStatus() {
    return housingStatus;
  }

  public void setHousingStatus(String housingStatus) {
    this.housingStatus = housingStatus;
  }
}