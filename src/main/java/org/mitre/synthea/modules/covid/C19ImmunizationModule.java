package org.mitre.synthea.modules.covid;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
 * Johnson &amp; Johnson (Janssen) COVID-19 Vaccine emergency use authorization on February 27, 2021 for people
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
 * * Johnson &amp; Johnson (Janssen) COVID-19 - 7.1%
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
public class C19ImmunizationModule extends Module {
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

  public static final long FIRST_POSSIBLE_SHOT_TIME = LocalDateTime.of(2020, 12, 11, 12, 0)
      .toInstant(ZoneOffset.UTC).toEpochMilli();
  public static final long MODERNA_APPROVED = LocalDateTime.of(2020, 12, 18, 12, 0)
      .toInstant(ZoneOffset.UTC).toEpochMilli();
  public static final long JANSSEN_APPROVED = LocalDateTime.of(2021, 2, 27, 12, 0)
      .toInstant(ZoneOffset.UTC).toEpochMilli();
  public static final long EXPAND_AGE_TO_TWELVE = LocalDateTime.of(2021, 5, 10, 12, 0)
      .toInstant(ZoneOffset.UTC).toEpochMilli();
  public static final long LATE_ADOPTION_START_TIME = LocalDateTime.of(2021, 7, 23, 12, 0)
      .toInstant(ZoneOffset.UTC).toEpochMilli();

  public static final int FIRST_ELIGIBLE_AGE = 16;
  public static final int EXPANDED_ELIGIBLE_AGE = 12;

  public static final String C19_VACCINE_STATUS = "C19_VACCINE_STATUS";
  public static final String C19_VACCINE = "C19_VACCINE";
  public static final String C19_SCHEDULED_FIRST_SHOT = "C19_SCHEDULED_FIRST_SHOT";
  public static final String C19_SCHEDULED_SECOND_SHOT = "C19_SCHEDULED_SECOND_SHOT";
  public static final String C19_LATE_ADOPTER_MODEL = "C19_LATE_ADOPTER_MODEL";

  public enum VaccinationStatus {
    NOT_ELIGIBLE,
    WAITING_FOR_SHOT,
    POTENTIAL_LATE_ADOPTER,
    NEVER_GOING_TO_GET_SHOT,
    FIRST_SHOT,
    FULLY_VACCINATED
  }

  public C19ImmunizationModule() {
    C19VaccineAgeDistributions.loadRawDistribution();
    C19VaccineAgeDistributions.populateDistributions();
    C19Vaccine.initialize();
  }

  public static boolean currentlyHasCOVID(Person person) {
    return person.record.conditionActive(COVID_CODE);
  }

  public static boolean eligibleForShot(Person person, long time) {
    int age = person.ageInYears(time);
    if (age >= FIRST_ELIGIBLE_AGE || (time >= EXPAND_AGE_TO_TWELVE) && age >= EXPANDED_ELIGIBLE_AGE) {
      return true;
    }
    return false;
  }

  public static boolean decideOnShot(Person person, long time) {
    double chanceOfGettingShot = 0;
    int age = person.ageInYears(time);
    if (age >= 75) {
      chanceOfGettingShot = ONE_SHOT_75_PLUS;
    } else if (age >= 65) {
      chanceOfGettingShot = ONE_SHOT_65_74;
    } else if (age >= 50) {
      chanceOfGettingShot = ONE_SHOT_50_64;
    } else if (age >= 40) {
      chanceOfGettingShot = ONE_SHOT_40_49;
    } else if (age >= 25) {
      chanceOfGettingShot = ONE_SHOT_25_39;
    } else if (age >= 18) {
      chanceOfGettingShot = ONE_SHOT_18_24;
    } else if (age >= 16) {
      chanceOfGettingShot = ONE_SHOT_16_17;
    } else if (age >= 12) {
      chanceOfGettingShot = ONE_SHOT_12_15;
    }

    return chanceOfGettingShot <= person.rand();
  }

  public static C19Vaccine.EUASet selectVaccine(Person person, long time) {
    if (time < MODERNA_APPROVED) {
      return C19Vaccine.EUASet.PFIZER;
    } else if (time >= MODERNA_APPROVED && time <= JANSSEN_APPROVED) {
      // Randomly select, with equal chance
      if (person.rand() < 0.5) {
        return C19Vaccine.EUASet.PFIZER;
      } else {
        return C19Vaccine.EUASet.MODERNA;
      }
    } else if (time > JANSSEN_APPROVED) {
        return C19Vaccine.selectShot(person);
    }

    return null;
  }

  public static void vaccinate(Person person, long time, int series) {
    person.record.encounterStart(time, HealthRecord.EncounterType.OUTPATIENT);
    HealthRecord.Immunization immunization = person.record.immunization(time, "COVID19");
    immunization.series = series;
    C19Vaccine vaccine = C19Vaccine.EUAs.get(person.attributes.get(C19_VACCINE));
    HealthRecord.Code immCode = new HealthRecord.Code("http://hl7.org/fhir/sid/cvx",
        vaccine.getCvx(), vaccine.getDisplay());
    immunization.codes.add(immCode);
    person.record.encounterEnd(time + Utilities.convertTime("minutes", 15),
        HealthRecord.EncounterType.OUTPATIENT);
  }

  @Override
  public boolean process(Person person, long time) {
    if (time < FIRST_POSSIBLE_SHOT_TIME) {
      return false;
    }

    VaccinationStatus status = (VaccinationStatus) person.attributes.get(C19_VACCINE_STATUS);

    if (status == null) {
      status = VaccinationStatus.NOT_ELIGIBLE;
      person.attributes.put(C19_VACCINE_STATUS, status);
    }

    switch (status) {
      case NOT_ELIGIBLE:
        if (eligibleForShot(person, time)) {
          if(decideOnShot(person, time)) {
            person.attributes.put(C19_VACCINE_STATUS, VaccinationStatus.WAITING_FOR_SHOT);
            long shotDate = C19VaccineAgeDistributions.selectShotTime(person, time);
            if (shotDate < time) {
              // TODO: Figure out what to do when the person should receive their shot in the
              // current time step
              shotDate = time;
            }
            person.attributes.put(C19_SCHEDULED_FIRST_SHOT, shotDate);
            person.attributes.put(C19_VACCINE, selectVaccine(person, shotDate));
          } else {
            person.attributes.put(C19_VACCINE_STATUS, VaccinationStatus.POTENTIAL_LATE_ADOPTER);
          }
        } else {
          person.attributes.put(C19_VACCINE_STATUS, VaccinationStatus.NOT_ELIGIBLE);
        }
        break;
      case WAITING_FOR_SHOT:
        long scheduledShotDate = (long) person.attributes.get(C19_SCHEDULED_FIRST_SHOT);
        if (scheduledShotDate <= time) {
          if (currentlyHasCOVID(person)) {
            // wait for COVID to resolve before getting shot
            return false;
          }
        }
        vaccinate(person, time, 1);
        C19Vaccine vaccineUsed = C19Vaccine.EUAs.get(person.attributes.get(C19_VACCINE));
        if (vaccineUsed.isTwoDose()) {
          person.attributes.put(C19_VACCINE_STATUS, VaccinationStatus.FIRST_SHOT);
          person.attributes.put(C19_SCHEDULED_SECOND_SHOT, vaccineUsed.getTimeBetweenDoses() + time);
        } else {
          person.attributes.put(C19_VACCINE_STATUS, VaccinationStatus.FULLY_VACCINATED);
        }
        break;
      case FIRST_SHOT:
        // Assuming someone doesn't get COVID between first and second shot
        long scheduledSecondShotDate = (long) person.attributes.get(C19_SCHEDULED_SECOND_SHOT);
        if (scheduledSecondShotDate <= time) {
          vaccinate(person, time, 2);
          person.attributes.put(C19_VACCINE_STATUS, VaccinationStatus.FULLY_VACCINATED);
        }
        break;
      case FULLY_VACCINATED:
        // do nothing
        break;
      case NEVER_GOING_TO_GET_SHOT:
        // do nothing
        break;
      case POTENTIAL_LATE_ADOPTER:
        // TODO: set up age group specific chances that people will get vaccinated beyond the actual
        // data we have. Particularly relevant for 12-15 year olds as demand is still somewhat high
        // in that group
        if (time >= LATE_ADOPTION_START_TIME) {
          LateAdopterModel model = (LateAdopterModel) person.attributes.get(C19_LATE_ADOPTER_MODEL);
          if (model == null) {
            model = new LateAdopterModel(person, time);
            person.attributes.put(C19_LATE_ADOPTER_MODEL, model);
          }
          boolean willGetShot = model.willGetShot(person, time);
          if (willGetShot) {
            person.attributes.put(C19_VACCINE_STATUS, VaccinationStatus.WAITING_FOR_SHOT);
            long shotDate = time + Utilities.convertTime("weeks", 1);
            person.attributes.put(C19_SCHEDULED_FIRST_SHOT, shotDate);
          } else if (model.isNotGettingShot()) {
            person.attributes.put(C19_VACCINE_STATUS, VaccinationStatus.NEVER_GOING_TO_GET_SHOT);
          }
        }
        break;
      default:
        // should never get here
        throw new IllegalStateException("COVID-19 Immunization entered an unknown state based on" +
            "a person's vaccination status");
    }

    return false;
  }
}
