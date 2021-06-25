package org.mitre.synthea.modules;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.world.agents.Person;

/**
 * A module to simulate COVID19 Immunizations in the United States.
 *
 * People meeting the criteria for vaccination may get one as soon as it it available. A percentage
 * of the population will opt not to get vaccinated and drop out. For individuals in the simulation
 * who "want" the vaccine, they will have a randomly weighted delay based on their age, as vaccines
 * were typically made available to older age groups before the entire public.
 *
 * Pfizer-BioNTech COVID-19 Vaccine emergency use authorization on December 11, 2020 for people
 * over 16. Expanded to 12+ on May 10, 2021.
 * https://www.fda.gov/emergency-preparedness-and-response/coronavirus-disease-2019-covid-19/pfizer-biontech-covid-19-vaccine
 *
 * Moderna COVID-19 Vaccine emergency use authorization on December 18, 2020 for people over 18
 * https://www.fda.gov/news-events/press-announcements/fda-takes-additional-action-fight-against-covid-19-issuing-emergency-use-authorization-second-covid
 *
 * Johnson & Johnson (Janssen) COVID-19 Vaccine emergency use authorization on February 27, 2021 for people
 * over 18.
 * https://www.fda.gov/emergency-preparedness-and-response/coronavirus-disease-2019-covid-19/janssen-covid-19-vaccine
 *
 * From December 11, 2020 through December 17, 2020, only the Pfizer-BioNTech COVID-19 Vaccine is
 * available. From December 18, 2020 through February 26, 2021, the Pfizer-BioNTech COVID-19 Vaccine
 * and Moderna COVID-19 Vaccine will be available, with them being distributed equally.
 *
 * On February 27, 2021 all three vaccines are distributed. Distribution has the following weights:
 * * Pfizer-BioNTech COVID-19 Vaccine - 53.1%
 * * Moderna COVID-19 Vaccine - 39.8%
 * * Johnson & Johnson (Janssen) COVID-19 - 7.1%
 *
 * Distribution ratios were calculated using doses administered information from Our World in Data
 * on June 23, 2021.
 * https://ourworldindata.org/grapher/covid-vaccine-doses-by-manufacturer?country=~USA
 *
 * Doses of two shot vaccines were divided by 1.956 to determine the number of individuals
 * vaccinated. These results were combined with the Janssen vaccine doses to determine ratios.
 *
 * The factor of 1.956 was chosen as a CDC MMWR showed that 95.6% of individuals were returning for
 * their second dose, if they had a two dose vaccine.
 * https://www.cdc.gov/mmwr/volumes/70/wr/mm7011e2.htm
 *
 * This module does not represent the "pause" of the Janssen vaccine.
 *
 * When people get vaccinated is based on data from the CDC COVID Data Tracker.
 * https://covid.cdc.gov/covid-data-tracker
 *
 * Distributions are created for each age group based on actual doses per day. The weight of each
 * day being selected is based on the number of doses given that day divided by the overall number
 * of doses for the age group. This will have the effect of shifting the doses a little later, as
 * the data does not reflect the difference between first and second dose, but it should be close
 * enough to mirror the general trends by age group.
 *
 * The chance that someone gets vaccinated is based on vaccination percentage for age group as of
 * June 25, 2021. There are kids under 12 who have received a dose of COVID Vaccine in the US. The
 * number is small and currently ignored by this module.
 */
public class COVID19ImmunizationModule extends Module {
  public static final String COVID_CODE = "840539006";

  // At least one shot rates as of 6/25/2021
  public static final double ONE_SHOT_75_PLUS = 85.2;
  public static final double ONE_SHOT_65_74 = 89.1;
  public static final double ONE_SHOT_50_64 = 72.6;
  public static final double ONE_SHOT_40_49 = 62.4;
  public static final double ONE_SHOT_25_39 = 52.8;
  public static final double ONE_SHOT_18_24 = 47.6;
  public static final double ONE_SHOT_16_17 = 41.9;
  public static final double ONE_SHOT_12_15 = 28.8;
  
  public static boolean currentlyHasCOVID(Person person) {
    return person.record.conditionActive(COVID_CODE);
  }
}
