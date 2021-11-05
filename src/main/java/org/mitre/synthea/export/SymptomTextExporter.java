package org.mitre.synthea.export;

import static org.mitre.synthea.export.ExportHelper.dateFromTimestamp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.text.WordUtils;
import org.mitre.synthea.engine.ExpressedConditionRecord.ConditionWithSymptoms;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

/**
 * Exporter for a simple human-readable text format per person.
 * Sample: <pre>
 * Colleen Wilkinson
 * =================
 * Race:                White
 * Ethnicity:           Non-Hispanic
 * Gender:              M
 * Birth Date:          1966-10-26
 * --------------------------------------------------------------------------------
 * CONDITIONS WITH SYMPTOMS:
 * 10 | 10 | Ottitis | 2 | Symptom1:val1, Symptom2:val2
 * 23 | 25 | Appendicitis | 3 | Symptom3:val3, Symptom4:val4, Symptom5:val5
 * --------------------------------------------------------------------------------
 *
 * </pre>
 */

public class SymptomTextExporter {

  /**
   * Replaces commas and line breaks in the source string with a single space.
   * Null is replaced with the empty string.
   */
  private static String clean(String src) {
    if (src == null) {
      return "";
    } else {
      return src.replaceAll("\\r\\n|\\r|\\n|,", " ").trim();
    }
  }

  /**
   * Produce and export a person's symptom record in the text format.
   *
   * @param person Person to export
   * @param fileTag Tag to add to the filename
   * @param endTime Time the simulation ended
   * @throws IOException if any error occurs writing to the standard export location
   */
  public static void exportAll(Person person, String fileTag, long endTime) throws IOException {

    String personID = (String) person.attributes.get(Person.ID);

    // check if we've already exported this patient demographic data yet,
    // otherwise the "split record" feature could add a duplicate entry.
    if (person.attributes.containsKey("exported_symptoms_to_txt")) {
      return;
    } else {
      person.attributes.put("exported_symptoms_to_txt", personID);
    }


    // now we finally start writing things
    List<String> textRecord = new LinkedList<>();

    basicInfo(textRecord, person, endTime);
    breakline(textRecord);

    textRecord.add("CONDITIONS WITH SYMPTOMS:");
    Map<Long, List<ConditionWithSymptoms>> infos = person.getOnsetConditionRecord(
        ).getConditionSymptoms();
    List<Long> list = new LinkedList<Long>(infos.keySet());
    Collections.sort(list);

    int yearsOfHistory = Integer.parseInt(Config.get("exporter.years_of_history"));

    for (Long time: list) {
      int symptomExporterMode = Integer.parseInt(Config.get("exporter.symptoms.mode"));
      boolean toBeExported = true;
      if (symptomExporterMode == 0) {
        long cutoffDate = endTime - Utilities.convertTime("years", yearsOfHistory);
        toBeExported = time >= cutoffDate;
      }
      if (!toBeExported) {
        continue;
      }
      Integer ageYear = person.ageInYears(time);
      for (ConditionWithSymptoms conditionWithSymptoms: infos.get(time)) {
        String condition = conditionWithSymptoms.getConditionName();
        Map<String, List<Integer>> symptomInfo = conditionWithSymptoms.getSymptoms();
        Long ageEnd = conditionWithSymptoms.getEndTime();
        String ageEndStr = "";
        if (ageEnd != null) {
          ageEndStr = String.valueOf(person.ageInYears(ageEnd));
        }
        StringBuilder s = new StringBuilder();
        s.append(ageYear.toString()).append(" | ");
        s.append(ageEndStr).append(" | ");
        s.append(clean(condition)).append(" | ");
        s.append(clean(String.valueOf(symptomInfo.size())));

        StringBuilder symptomStr = new StringBuilder();
        for (String symptom: symptomInfo.keySet()) {
          List<Integer> values = symptomInfo.get(symptom);
          StringBuilder value = new StringBuilder();
          for (int idx = 0; idx < values.size(); idx++) {
            value.append(String.valueOf(values.get(idx)));
            if (idx < values.size() - 1) {
              value.append(':');
            }
          }
          symptomStr.append(", ").append(clean(symptom)).append(':').append(value);
        }
        String symptomData = symptomStr.toString();
        if (symptomData.length() > 0) {
          symptomData = symptomData.substring(2);
        }
        s.append(" | ").append(symptomData);
        textRecord.add(s.toString());
      }
    }
    breakline(textRecord);

    // finally write to the file
    File outDirectory = Exporter.getOutputFolder("symptoms/text", person);
    Path outFilePath = outDirectory.toPath().resolve(Exporter.filename(person, fileTag, "txt"));
    Files.write(outFilePath, textRecord, StandardOpenOption.CREATE_NEW);
  }

  /**
   * Add the basic information to the record.
   *
   * @param textRecord
   *          Text format record, as a list of lines
   * @param person
   *          The person to export
   * @param endTime
   *          Time the simulation ended (to calculate age/deceased status)
   */
  private static void basicInfo(List<String> textRecord, Person person, long endTime) {
    String name = (String) person.attributes.get(Person.NAME);

    textRecord.add(name);
    textRecord.add(name.replaceAll("[A-Za-z0-9 ]", "=")); // "underline" the characters in the name

    String race = (String) person.attributes.get(Person.RACE);
    String ethnicity = (String) person.attributes.get(Person.ETHNICITY);
    String displayEthnicity;
    if (ethnicity.equals("hispanic")) {
      displayEthnicity = "Hispanic";
    } else {
      displayEthnicity = "Non-Hispanic";
    }
    textRecord.add("Race:                " + WordUtils.capitalize(race));
    textRecord.add("Ethnicity:           " + displayEthnicity);

    textRecord.add("Gender:              " + person.attributes.get(Person.GENDER));

    String birthdate = dateFromTimestamp((long) person.attributes.get(Person.BIRTHDATE));
    textRecord.add("Birth Date:          " + birthdate);

  }



  /**
   * Section separator (80 dashes).
   */
  private static final String SECTION_SEPARATOR = String.join("", Collections.nCopies(80, "-"));

  /**
   * Add a section separator line to the record.
   *
   * @param textRecord
   *          Record to add separator line to
   */
  private static void breakline(List<String> textRecord) {
    textRecord.add(SECTION_SEPARATOR);
  }
}
