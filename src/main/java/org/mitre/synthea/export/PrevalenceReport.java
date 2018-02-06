package org.mitre.synthea.export;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;

public class PrevalenceReport {

  // These are the name of the columns in the CSV prevalence template
  private static final String DIFFERENCE = "DIFFERENCE";
  private static final String ACTUAL_PREV_PERCENT = "ACTUAL PREVALENCE PERCENT";
  private static final String PREV_RATE = "SYNTHEA PREVALENCE RATE";
  private static final String PREV_PERCENT = "SYNTHEA PREVALENCE PERCENT";
  private static final String ITEM = "ITEM";
  private static final String GENDER = "GENDER";
  private static final String RACE = "RACE";
  private static final String AGE = "AGE GROUP";
  private static final String OCCUR = "SYNTHEA OCCURRENCES";
  private static final String POP = "SYNTHEA POPULATION";
  private static final String GIVEN_CON = "GIVEN CONDITION";

  private static final String ALL = "*";

  public static void export(Generator generator) throws Exception {
    if (generator.database == null) {
      System.err.println(
          "Unable to generate Prevalence Report - No database exists to generate report from.");
      return;
    }

    String csvData = Utilities.readResource("prevalence_template.csv");
    List<LinkedHashMap<String, String>> data = SimpleCSV.parse(csvData);

    try (Connection connection = generator.database.getConnection()) {

      for (LinkedHashMap<String, String> line : data) {

        if (line.get(ITEM).isEmpty()) {
          continue;
        }

        if (line.get(GIVEN_CON).isEmpty()) {
          getPrev(connection, line);
        } else {
          givenCondition(connection, line);
        }

        getPop(connection, line);
        completeSyntheaFields(connection, line);
        completeDifferenceField(connection, line);
      }

      allConditions(connection, data);
    }

    String newCsvData = SimpleCSV.unparse(data);

    File outDirectory = Exporter.getOutputFolder("prevalence", null);

    Path outFilePath = outDirectory.toPath()
        .resolve("prev_data" + System.currentTimeMillis() + ".csv");

    Files.write(outFilePath, Collections.singleton(newCsvData), StandardOpenOption.CREATE_NEW);
  }

  /**
   * Uses a string builder to run a query dependent upon what is on each line of the CSV template.
   * Executes the query after filling in the indexes. Inserts result of query into the occurrences
   * column.
   */
  private static void getPrev(Connection connection, LinkedHashMap<String, String> line)
      throws SQLException {

    StringBuilder sb = new StringBuilder();
    sb.append("SELECT COUNT(*) FROM PERSON p, CONDITION c, ATTRIBUTE a\n" + "WHERE \n"
        + "p.ID = c.PERSON_ID\n" + "AND c.PERSON_ID = a.PERSON_ID\n" + "AND (c.DISPLAY = ?)\n"
        + "AND (p.DATE_OF_DEATH is null)\n" + "");

    String gender = line.get(GENDER);
    if (!gender.equals(ALL)) {
      sb.append("AND (p.GENDER = ?)\n");
    }

    String race = line.get(RACE);
    if (!race.equals(ALL)) {
      sb.append("AND (p.RACE = ?)\n");
    }

    String age = line.get(AGE);
    if (age.equals("adult")) {
      sb.append("AND (a.NAME = 'AGE' AND CAST(a.VALUE AS INT) >= 18)\n");
    } else if (age.equals("child")) {
      sb.append("AND (a.NAME = 'AGE' AND CAST(a.VALUE AS INT) < 18)\n");
    } else if (age.equals("senior")) {
      sb.append("AND (a.NAME = 'AGE' AND CAST(a.VALUE AS INT) >= 65)\n");
    }

    PreparedStatement stmt = connection.prepareStatement(sb.toString());

    int index = 1; // SQL begins at 1 not 0
    stmt.setString(index++, line.get(ITEM));

    if (!gender.equals(ALL)) {
      stmt.setString(index++, gender);
    }

    if (!race.equals(ALL)) {
      stmt.setString(index++, race);
    }

    ResultSet rs = stmt.executeQuery();

    rs.next();

    int countOccur = rs.getInt(1);
    line.put(OCCUR, Integer.toString(countOccur));
  }

  /**
   * Uses a string builder to run a query dependent upon what is on each line of the CSV template.
   * Executes the query after filling in the indexes. Inserts result of query into the population
   * column.
   */
  private static void getPop(Connection connection, LinkedHashMap<String, String> line)
      throws SQLException {

    StringBuilder sb = new StringBuilder();
    sb.append("SELECT COUNT(*) FROM PERSON p, ATTRIBUTE a\n" + "WHERE \n" + "p.ID = a.PERSON_ID\n"
        + "AND (p.DATE_OF_DEATH is null)\n" + "");

    String gender = line.get(GENDER);
    if (!gender.equals(ALL)) {
      sb.append("AND (p.GENDER = ?)\n");
    }

    String race = line.get(RACE);
    if (!race.equals(ALL)) {
      sb.append("AND (p.RACE = ?)\n");
    }

    String age = line.get(AGE);
    if (age.equals("adult")) {
      sb.append("AND (a.NAME = 'AGE' AND CAST(a.VALUE AS INT) >= 18)\n");
    } else if (age.equals("child")) {
      sb.append("AND (a.NAME = 'AGE' AND CAST(a.VALUE AS INT) < 18)\n");
    } else if (age.equals("senior")) {
      sb.append("AND (a.NAME = 'AGE' AND CAST(a.VALUE AS INT) > 65)\n");
    }

    PreparedStatement stmt = connection.prepareStatement(sb.toString());

    int index = 1; // SQL begins at 1 not 0

    if (!gender.equals(ALL)) {
      stmt.setString(index++, gender);
    }

    if (!race.equals(ALL)) {
      stmt.setString(index++, race);
    }

    ResultSet rs = stmt.executeQuery();
    rs.next();

    int countPop = rs.getInt(1);
    line.put(POP, Integer.toString(countPop));
  }

  /**
   * Calculates the prevalence rate and percent based on what is on that line of the report. Inserts
   * result of calculation into the prevalence rate and percent columns.
   */
  private static void completeSyntheaFields(Connection connection,
      LinkedHashMap<String, String> line) throws SQLException {

    if ((line.get(OCCUR).isEmpty()) || (line.get(POP).isEmpty())) {
      line.put(PREV_RATE, (null));
      line.put(PREV_PERCENT, (null));
    } else {
      double occurr = Double.parseDouble(line.get(OCCUR));
      double pop = Double.parseDouble(line.get(POP));

      if (pop != 0) {
        double prevRate = occurr / pop;
        double prevPercent = prevRate * 100;
        line.put(PREV_RATE, Double.toString(prevRate));
        line.put(PREV_PERCENT, Double.toString(prevPercent));
      } else {
        line.put(PREV_RATE, Double.toString(0));
        line.put(PREV_PERCENT, Double.toString(0));
      }
    }
  }

  /**
   * Calculates the difference between the Synthea prevalence percent and actual percent based on
   * what is on that line of the report. Inserts result of calculation into the difference column.
   */
  private static void completeDifferenceField(Connection connection,
      LinkedHashMap<String, String> line) throws SQLException {
    if (line.get(ACTUAL_PREV_PERCENT).isEmpty()) {
      line.put(DIFFERENCE, (null));
    } else {
      double actualPrev = Double.parseDouble(line.get(ACTUAL_PREV_PERCENT));
      double prevPercent = Double.parseDouble(line.get(PREV_PERCENT));
      double diff = (prevPercent - actualPrev);
      line.put(DIFFERENCE, Double.toString(diff));
    }
  }

  /**
   * Uses a string builder to run a query dependent upon what is on each line of the CSV template.
   * Calculates the prevalence rate of one disease given another disease. Inserts result of query
   * into the occurrences column.
   */
  private static void givenCondition(Connection connection, LinkedHashMap<String, String> line)
      throws SQLException {
    String query = "SELECT COUNT(*) FROM PERSON p, CONDITION c1, CONDITION c2 "
        + "WHERE  p.ID = c1.PERSON_ID " + "AND c1.PERSON_ID = c2.PERSON_ID "
        + "AND (p.DATE_OF_DEATH is null) " + "AND (c1.DISPLAY = ?) " + "AND (c2.DISPLAY = ?) ";

    PreparedStatement stmt = connection.prepareStatement(query);
    stmt.setString(1, line.get(ITEM));
    stmt.setString(2, line.get(GIVEN_CON));

    ResultSet rs = stmt.executeQuery();
    rs.next();

    int givenCondition = rs.getInt(1);
    line.put(OCCUR, Integer.toString(givenCondition));
  }

  /**
   * Calculates the unique number of patients who have a distinct disease. Inserts result of query
   * into the occurrences column. Calculates the total living population of patients. Inserts result
   * into the population column. Calls for completeSyntheaFields to calculate the prevalence rate
   * and percent.
   */
  private static void allConditions(Connection connection, List<LinkedHashMap<String, String>> data)
      throws SQLException {

    PreparedStatement stmt = connection
        .prepareStatement("select count(*) from person where person.DATE_OF_DEATH is null");
    ResultSet rs = stmt.executeQuery();
    rs.next();
    int totalPopulation = rs.getInt(1);

    stmt = connection.prepareStatement(
        "select distinct c.display as DistinctDisplay, "
        + "count(distinct c.person_id) as CountDisplay \n"
        + "from condition c, person p\n" 
        + "where c.person_id = p.id\n"
        + "and p.date_of_death is null\n" 
        + "group by c.display\n" 
        + "order by c.display ASC");
    rs = stmt.executeQuery();
    while (rs.next()) {
      String disease = rs.getString("DistinctDisplay");
      int count = rs.getInt("CountDisplay");
      LinkedHashMap<String, String> line = new LinkedHashMap<String, String>();
      line.put(ITEM, disease);
      line.put(OCCUR, Integer.toString(count));
      line.put(POP, Integer.toString(totalPopulation));
      data.add(line);
      completeSyntheaFields(connection, line);
    }
  }
}
