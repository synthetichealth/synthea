package org.mitre.synthea.helpers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.mitre.synthea.world.agents.Person;

/**
 * A place for helpers related to telemedicine.
 */
public class Telemedicine {

  /**
   * Odds of an encounter being virtual, a.k.a. telemedicine are taken from the McKinsey report:
   * https://www.mckinsey.com/industries/healthcare-systems-and-services/our-insights/telehealth-a-quarter-trillion-dollar-post-covid-19-reality
   * <p>
   * The report claims that since April 2020, telemedicine accounted for 13 - 17% of visits. 15%
   * was used here for simplicity. It also claims that the current levels of telemedicine are 38
   * times the prepandemic baseline. That would make the prepandemic baseline 0.4%
   * </p>
   */
  public static final double CURRENT_CHANCE = 0.15;
  public static final double PREPANDEMIC_CHANCE = 0.004;

  public static final long TELEMEDICINE_START_DATE = LocalDateTime.of(2016, 1, 1, 12, 0)
      .toInstant(ZoneOffset.UTC).toEpochMilli();
  public static final long TELEMEDICINE_UPTAKE_DATE = LocalDateTime.of(2020, 4, 1, 12, 0)
      .toInstant(ZoneOffset.UTC).toEpochMilli();

  /**
   * Determines whether an encounter should be telemedicine, with different chances before and
   * after the start of the COVID-19 pandemic.
   * @param person source of randomness
   * @param time current time in the simulation
   * @return true if the encounter should be virtual
   */
  public static boolean shouldEncounterBeVirtual(Person person, long time) {
    if (time < TELEMEDICINE_START_DATE) {
      return false;
    }
    if (time < TELEMEDICINE_UPTAKE_DATE) {
      return person.rand() <= PREPANDEMIC_CHANCE;
    } else {
      return person.rand() <= CURRENT_CHANCE;
    }
  }
}