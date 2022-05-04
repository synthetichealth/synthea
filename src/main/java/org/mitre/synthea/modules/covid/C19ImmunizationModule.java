package org.mitre.synthea.modules.covid;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Attributes;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.EncounterModule;
import org.mitre.synthea.modules.Immunizations;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.ClinicianSpecialty;
import org.mitre.synthea.world.concepts.HealthRecord;

/**
 * A module to simulate COVID19 Immunizations in the United States.
 * <p>
 * People meeting the criteria for vaccination may get one as soon as it it available. A percentage
 * of the population will opt not to get vaccinated and drop out. For individuals in the simulation
 * who "want" the vaccine, they will have a randomly weighted delay based on their age, as vaccines
 * were typically made available to older age groups before the entire public.
 * </p>
 * <p>
 * Pfizer-BioNTech COVID-19 Vaccine emergency use authorization on December 11, 2020 for people
 * over 16. Expanded to 12+ on May 10, 2021.
 * https://www.fda.gov/emergency-preparedness-and-response/coronavirus-disease-2019-covid-19/pfizer-biontech-covid-19-vaccine
 * </p>
 * <p>
 * Moderna COVID-19 Vaccine emergency use authorization on December 18, 2020 for people over 18
 * https://www.fda.gov/news-events/press-announcements/fda-takes-additional-action-fight-against-covid-19-issuing-emergency-use-authorization-second-covid
 * </p>
 * <p>
 * Johnson &amp; Johnson (Janssen) COVID-19 Vaccine emergency use authorization on February 27, 2021
 * for people over 18.
 * https://www.fda.gov/emergency-preparedness-and-response/coronavirus-disease-2019-covid-19/janssen-covid-19-vaccine
 * </p>
 * <p>
 * From December 11, 2020 through December 17, 2020, only the Pfizer-BioNTech COVID-19 Vaccine is
 * available. From December 18, 2020 through February 26, 2021, the Pfizer-BioNTech COVID-19 Vaccine
 * and Moderna COVID-19 Vaccine will be available, with them being distributed equally.
 * </p>
 * <p>
 * On February 27, 2021 all three vaccines are distributed. Distribution has the following weights:
 * * Pfizer-BioNTech COVID-19 Vaccine - 53.1%
 * * Moderna COVID-19 Vaccine - 39.8%
 * * Johnson &amp; Johnson (Janssen) COVID-19 - 7.1%
 * </p>
 * <p>
 * Distribution ratios were calculated using doses administered information from Our World in Data
 * on June 23, 2021.
 * https://ourworldindata.org/grapher/covid-vaccine-doses-by-manufacturer?country=~USA
 * </p>
 * <p>
 * Doses of two shot vaccines were divided by 1.956 to determine the number of individuals
 * vaccinated. These results were combined with the Janssen vaccine doses to determine ratios.
 * </p>
 * <p>
 * The factor of 1.956 was chosen as a CDC MMWR showed that 95.6% of individuals were returning for
 * their second dose, if they had a two dose vaccine.
 * https://www.cdc.gov/mmwr/volumes/70/wr/mm7011e2.htm
 * </p>
 * <p>
 * This module does not represent the "pause" of the Janssen vaccine.
 * </p>
 * <p>
 * When people get vaccinated is based on data from the CDC COVID Data Tracker.
 * https://covid.cdc.gov/covid-data-tracker
 * </p>
 * <p>
 * Distributions are created for each age group based on actual doses per day. The weight of each
 * day being selected is based on the number of doses given that day divided by the overall number
 * of doses for the age group. This will have the effect of shifting the doses a little later, as
 * the data does not reflect the difference between first and second dose, but it should be close
 * enough to mirror the general trends by age group.
 * </p>
 * <p>
 * The chance that someone gets vaccinated is based on vaccination percentage for age group as of
 * June 25, 2021. There are kids under 12 who have received a dose of COVID Vaccine in the US. The
 * number is small and currently ignored by this module.
 * </p>
 */
public class C19ImmunizationModule extends Module {
  public static final String COVID_CODE = "840539006";

  public static final long FIRST_POSSIBLE_SHOT_TIME = LocalDateTime.of(2020, 12, 11, 12, 0)
      .toInstant(ZoneOffset.UTC).toEpochMilli();
  public static final long MODERNA_APPROVED = LocalDateTime.of(2020, 12, 18, 12, 0)
      .toInstant(ZoneOffset.UTC).toEpochMilli();
  public static final long JANSSEN_APPROVED = LocalDateTime.of(2021, 2, 27, 12, 0)
      .toInstant(ZoneOffset.UTC).toEpochMilli();
  public static final long EXPAND_AGE_TO_TWELVE = LocalDateTime.of(2021, 5, 10, 12, 0)
      .toInstant(ZoneOffset.UTC).toEpochMilli();
  public static final long LATE_ADOPTION_START_TIME = LocalDateTime.of(2021, 9, 14, 12, 0)
      .toInstant(ZoneOffset.UTC).toEpochMilli();

  public static final int FIRST_ELIGIBLE_AGE = 16;
  public static final int EXPANDED_ELIGIBLE_AGE = 12;

  public static final String C19_VACCINE_STATUS = "C19_VACCINE_STATUS";
  // Attribute used to store the vaccine to give to the individual
  public static final String C19_VACCINE = "C19_VACCINE";
  public static final String C19_SCHEDULED_FIRST_SHOT = "C19_SCHEDULED_FIRST_SHOT";
  public static final String C19_SCHEDULED_SECOND_SHOT = "C19_SCHEDULED_SECOND_SHOT";
  // If someone does not get vaccinated in the period of time that they are eligible and Synthea
  // has national statistics on, there is still a chance that they will get vaccinated. The chance
  // is stored in their attributes under this key, where the chance of being vaccinated decays
  // exponentially.
  public static final String C19_LATE_ADOPTER_MODEL = "C19_LATE_ADOPTER_MODEL";

  // This is somewhat redundant given that there is C19_VACCINE_STATUS, but GMF modules can't
  // check attributes set to java enumeration values, so this will just be a simple boolean
  public static final String C19_FULLY_VACCINATED = "C19_FULLY_VACCINATED";

  // Key to use in Person.attributes in the IMMUNIZATIONS entry. Convention used by the
  // Immunizations module is to use lower case.
  public static final String C19_PERSON_ATTRS_KEY = "covid19";

  public enum VaccinationStatus {
    NOT_ELIGIBLE,
    WAITING_FOR_SHOT,
    POTENTIAL_LATE_ADOPTER,
    NEVER_GOING_TO_GET_SHOT,
    FIRST_SHOT,
    FULLY_VACCINATED
  }

  /**
   * Create and initialize an instance of the module.
   */
  public C19ImmunizationModule() {
    C19VaccineAgeDistributions.initialize();
    C19Vaccine.initialize();
    this.name = "COVID-19 Immunization Module";
  }

  public Module clone() {
    return this;
  }

  /**
   * Checks a person's health record to see if they currently have COVID-19.
   *
   * @param person the person to check
   * @return true if there is an active COVID-19 condition on the record.
   */
  public static boolean currentlyHasCOVID(Person person) {
    return person.record.conditionActive(COVID_CODE);
  }

  /**
   * Check to see if a person is eligible for a COVID-19 vaccine at the time in the simulation.
   * The check is based on date (when a vaccine was given an EUA) and age
   *
   * @param person the person to check
   * @param time   current time in the simulation
   * @return true if the person would be eligible for a vaccine at the point in the simulation
   */
  public static boolean eligibleForShot(Person person, long time) {
    int age = person.ageInYears(time);
    if (age >= FIRST_ELIGIBLE_AGE
        || (time >= EXPAND_AGE_TO_TWELVE) && age >= EXPANDED_ELIGIBLE_AGE) {
      return true;
    }
    return false;
  }

  /**
   * Given an individual, randomly select whether they will get vaccinated, with the selection
   * weighted by the proportion of individuals in their age group who have been vaccinated according
   * to national statistics.
   *
   * @param person the person to check
   * @param time   current time in the simulation
   * @return true if the person should proceed to vaccination
   */
  public static boolean decideOnShot(Person person, long time) {
    double chanceOfGettingShot = C19VaccineAgeDistributions.chanceOfGettingShot(person, time);
    return chanceOfGettingShot >= person.rand();
  }

  /**
   * Select a vaccine for an individual based on national distribution statistics and whether the
   * vaccine was eligible for the time in simulation.
   *
   * @param person the person to check
   * @param time   current time in the simulation
   * @return a vaccine that should be administered to the person
   */
  public static C19Vaccine.EUASet selectVaccine(Person person, long time) {
    if (time < FIRST_POSSIBLE_SHOT_TIME) {
      throw new IllegalArgumentException("No vaccines were available at time: " + time);
    } else if (time < MODERNA_APPROVED) {
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

  /**
   * Provide a COVID-19 vaccine to the person. Add an Immunization to their health record.
   *
   * @param person the person to check
   * @param time   current time in the simulation
   * @param series 1 - for first shot, 2 - for second shot
   */
  public static void vaccinate(Person person, long time, int series) {
    HealthRecord.Code encounterCode = new HealthRecord.Code("http://snomed.info/sct", "33879002",
        "Administration of vaccine to produce active immunity (procedure)");
    EncounterModule.createEncounter(person, time, HealthRecord.EncounterType.OUTPATIENT,
        ClinicianSpecialty.GENERAL_PRACTICE, encounterCode);
    HealthRecord.Immunization immunization = person.record.immunization(time, "COVID19");
    immunization.series = series;
    C19Vaccine vaccine = C19Vaccine.EUAs.get(person.attributes.get(C19_VACCINE));
    HealthRecord.Code immCode = new HealthRecord.Code("http://hl7.org/fhir/sid/cvx",
        vaccine.getCvx(), vaccine.getDisplay());
    immunization.codes.add(immCode);

    Map<String, List<Long>> immunizationHistory =
        (Map<String, List<Long>>) person.attributes.get(Immunizations.IMMUNIZATIONS);
    if (immunizationHistory == null) {
      immunizationHistory = new HashMap<>();
      person.attributes.put(Immunizations.IMMUNIZATIONS, immunizationHistory);
    }
    List<Long> covidImmunizationHistory = immunizationHistory.get(C19_PERSON_ATTRS_KEY);
    if (covidImmunizationHistory == null) {
      covidImmunizationHistory = new ArrayList<>();
      immunizationHistory.put(C19_PERSON_ATTRS_KEY, covidImmunizationHistory);
    }
    covidImmunizationHistory.add(time);
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
          if (decideOnShot(person, time)) {
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
          vaccinate(person, time, 1);
          C19Vaccine vaccineUsed = C19Vaccine.EUAs.get(person.attributes.get(C19_VACCINE));
          if (vaccineUsed.isTwoDose()) {
            person.attributes.put(C19_VACCINE_STATUS, VaccinationStatus.FIRST_SHOT);
            person.attributes.put(C19_SCHEDULED_SECOND_SHOT,
                vaccineUsed.getTimeBetweenDoses() + time);
          } else {
            person.attributes.put(C19_VACCINE_STATUS, VaccinationStatus.FULLY_VACCINATED);
            person.attributes.put(C19_FULLY_VACCINATED, true);
          }
        }
        break;
      case FIRST_SHOT:
        // Assuming someone doesn't get COVID between first and second shot
        long scheduledSecondShotDate = (long) person.attributes.get(C19_SCHEDULED_SECOND_SHOT);
        if (scheduledSecondShotDate <= time) {
          vaccinate(person, time, 2);
          person.attributes.put(C19_VACCINE_STATUS, VaccinationStatus.FULLY_VACCINATED);
          person.attributes.put(C19_FULLY_VACCINATED, true);
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
            person.attributes.put(C19_VACCINE, selectVaccine(person, shotDate));
          } else if (model.isNotGettingShot()) {
            person.attributes.put(C19_VACCINE_STATUS, VaccinationStatus.NEVER_GOING_TO_GET_SHOT);
          }
        }
        break;
      default:
        // should never get here
        throw new IllegalStateException("COVID-19 Immunization entered an unknown state based on"
            + "a person's vaccination status");
    }

    return false;
  }

  /**
   * Populate the given attribute map with the list of attributes that this
   * module reads/writes with example values when appropriate.
   *
   * @param attributes Attribute map to populate.
   */
  public static void inventoryAttributes(Map<String, Attributes.Inventory> attributes) {
    String m = C19ImmunizationModule.class.getSimpleName();

    Attributes.inventory(attributes, m, C19_VACCINE_STATUS, true, true, "WAITING_FOR_SHOT");
    Attributes.inventory(attributes, m, C19_VACCINE, true, true, "MODERNA");
    Attributes.inventory(attributes, m, C19_SCHEDULED_FIRST_SHOT, true, true, "7/29/2021");
    Attributes.inventory(attributes, m, C19_SCHEDULED_SECOND_SHOT, true, true, "8/19/2021");
    Attributes.inventory(attributes, m, C19_LATE_ADOPTER_MODEL, true, true, null);
    Attributes.inventory(attributes, m, C19_FULLY_VACCINATED, false, true, "true");
  }
}
