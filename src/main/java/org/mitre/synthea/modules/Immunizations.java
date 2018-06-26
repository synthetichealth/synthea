package org.mitre.synthea.modules;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;

/**
 * This is a complete, but fairly simplistic approach to synthesizing immunizations. It is encounter
 * driven; whenever an encounter occurs, the doctor checks for due immunizations and gives them. In
 * at least one case (HPV) this means that the immunization schedule isn't strictly followed since
 * the encounter schedule doesn't match the immunization schedule (e.g., 11yrs, 11yrs2mo, 11yrs6mo)
 * -- but in most cases they do line up. This module also assumes perfect doctors and compliant
 * patients. Every patient eventually receives every recommended immunization (unless they die
 * first). This module also does not implement any deviations or contraindications based on patient
 * conditions. For now, we've avoided specific brand names, preferring the general CVX codes.
 */
public class Immunizations {
  public static final String IMMUNIZATIONS = "immunizations";

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private static final Map<String, Map> immunizationSchedule = loadImmunizationSchedule();

  @SuppressWarnings("rawtypes")
  private static Map loadImmunizationSchedule() {
    String filename = "immunization_schedule.json";
    try {
      String json = Utilities.readResource(filename);
      Gson g = new Gson();
      return g.fromJson(json, HashMap.class);
    } catch (Exception e) {
      System.err.println("ERROR: unable to load json: " + filename);
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    }
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static void performEncounter(Person person, long time) {
    Map<String, List<Long>> immunizationsGiven;
    if (person.attributes.containsKey(IMMUNIZATIONS)) {
      immunizationsGiven = (Map<String, List<Long>>) person.attributes.get(IMMUNIZATIONS);
    } else {
      immunizationsGiven = new HashMap<String, List<Long>>();
      person.attributes.put(IMMUNIZATIONS, immunizationsGiven);
    }

    for (String immunization : immunizationSchedule.keySet()) {
      int series = immunizationDue(immunization, person, time, immunizationsGiven);
      if (series > 0) {
        List<Long> history = immunizationsGiven.get(immunization);
        history.add(time);
        HealthRecord.Immunization entry = person.record.immunization(time, immunization);
        Map code = (Map) immunizationSchedule.get(immunization).get("code");
        HealthRecord.Code immCode = new HealthRecord.Code(code.get("system").toString(),
            code.get("code").toString(), code.get("display").toString());
        entry.codes.add(immCode);
        entry.series = series;
      }
    }
  }

  /**
   * Return whether or not the specified immunization is due.
   *
   * @param immunization The immunization to give
   * @param person The person to receive the immunization
   * @param time The time the immunization would be given
   * @param immunizationsGiven The history of immunizations
   * @return -1 if the immunization should not be given, otherwise a positive integer,
   *     where the value is the series. For example, 1 if this is the first time the
   *     vaccine was administered; 2 if this is the second time, et cetera.
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public static int immunizationDue(String immunization, Person person, long time,
      Map<String, List<Long>> immunizationsGiven) {
    int ageInMonths = person.ageInMonths(time);

    List<Long> history = null;
    if (immunizationsGiven.containsKey(immunization)) {
      history = immunizationsGiven.get(immunization);
    } else {
      history = new ArrayList<Long>();
      immunizationsGiven.put(immunization, history);
    }

    // Don't administer if the immunization wasn't historically available at the date of the
    // encounter
    Map schedule = immunizationSchedule.get(immunization);
    Double firstAvailable = (Double) schedule.getOrDefault("first_available", 1900);
    if (time < Utilities.convertCalendarYearsToTime(firstAvailable.intValue())) {
      return -1;
    }

    // Don't administer if all recommended doses have already been given
    List atMonths = new ArrayList((List) schedule.get("at_months"));
    if (history.size() >= atMonths.size()) {
      return -1;
    }

    // See if the patient should receive a dose based on their current age and the recommended dose
    // ages;
    // we can't just see if greater than the recommended age for the next dose they haven't received
    // because i.e. we don't want to administer the HPV vaccine to someone who turns 90 in 2006 when
    // the
    // vaccine is released; we can't just use a simple test of, say, within 4 years after the
    // recommended
    // age for the next dose they haven't received because i.e. PCV13 is given to kids and seniors
    // but was
    // only available starting in 2010, so a senior in 2012 who has never received a dose should get
    // one,
    // but only one; what we do is:

    // 1) eliminate any recommended doses that are not within 4 years of the patient's age
    // at_months = at_months.reject { |am| age_in_months - am >= 48 }
    Predicate<Double> notWithinFourYears = p -> ((ageInMonths - p) >= 48);
    atMonths.removeIf(notWithinFourYears);
    if (atMonths.isEmpty()) {
      return -1;
    }

    // 2) eliminate recommended doses that were actually administered
    for (Long date : history) {
      int ageAtDate = person.ageInMonths(date);
      double recommendedAge = (double) atMonths.get(0);
      if (ageAtDate >= recommendedAge && ((ageAtDate - recommendedAge) < 48)) {
        atMonths.remove(0);
        if (atMonths.isEmpty()) {
          return -1;
        }
      }
    }

    // 3) see if there are any recommended doses remaining that this patient is old enough for
    if (!atMonths.isEmpty() && ageInMonths >= (double) atMonths.get(0)) {
      return history.size() + 1;
    }
    return -1;
  }

  /**
   * Get all of the Codes this module uses, for inventory purposes.
   * 
   * @return Collection of all codes and concepts this module uses
   */
  @SuppressWarnings("rawtypes")
  public static Collection<Code> getAllCodes() {
    List<Map> rawCodes = (List<Map>) immunizationSchedule.values()
        .stream().map(m -> (Map)m.get("code")).collect(Collectors.toList());
    
    List<Code> convertedCodes = new ArrayList<Code>(rawCodes.size());
    
    for (Map m : rawCodes) {
      Code immCode = new Code(m.get("system").toString(),
                              m.get("code").toString(), 
                              m.get("display").toString());
      
      convertedCodes.add(immCode);
    }

    return convertedCodes;
  }

  /**
   * Get the maximum number of vaccine doses for a particular code.
   * @param code The vaccine code.
   * @return The maximum number of doses to be administered.
   */
  @SuppressWarnings("rawtypes")
  public static int getMaximumDoses(String code) {
    for (String immunization : immunizationSchedule.keySet()) {
      Map icode = (Map) immunizationSchedule.get(immunization).get("code");
      if (icode.get("code").equals(code)) {
        List doses = (List) immunizationSchedule.get(immunization).get("at_months");
        return doses.size();
      }
    }
    return 1;
  }
}
