package org.mitre.synthea.export;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;

/**
 * The CustomSqlReport exporter produces custom CSV reports based on arbitrary queries provided.
 * The queries are run against the DataStore database, either file or in-memory.
 * Queries are defined in a configurable location - specified by config setting 
 * "exporter.custom_report_queries_file".
 * 
 */
public class CustomSqlReport {
  
  /**
   * Run the exporter to produce the custom reports based on the given population.
   * 
   * @param generator
   *          The generator that was used to produce a population
   * @throws Exception
   *           if any exception occurs in reading the queries file, querying the database, or
   *           writing the results to a CSV file
   */
  public static void export(Generator generator) throws Exception {
    if (generator.database == null) {
      System.err.println(
          "Unable to generate Custom Report - No database exists to generate report from.");
      return;
    }

    File outDirectory = Exporter.getOutputFolder("reports", null);

    String queriesFile = Config.get("exporter.custom_report_queries_file");
    String sqlQueries = Utilities.readResource(queriesFile);

    try (Scanner queries = new Scanner(sqlQueries);
        Connection connection = generator.database.getConnection()) {

      int reportNum = 1;
      // TODO: version 1 requires queries to be on one line. 
      // add logic to handle multi-line queries?
      while (queries.hasNextLine()) {
        String query = queries.nextLine().trim().toUpperCase();

        if (query.isEmpty() || query.startsWith("--")) {
          continue; // empty or comment line, ignore it
        }

        // TODO: allow the creation of views?
        if (!query.startsWith("SELECT")) {
          throw new IllegalArgumentException("Custom SQL query must only start with SELECT");
        }

        List<Map<String, String>> results = new LinkedList<>();

        PreparedStatement stmt = connection.prepareStatement(query);

        ResultSet rs = stmt.executeQuery();

        // get the list of column names from metadata
        ResultSetMetaData rsmd = rs.getMetaData();
        List<String> columnNames = new ArrayList<>(rsmd.getColumnCount());
        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
          columnNames.add(rsmd.getColumnName(i));
        }

        while (rs.next()) {
          Map<String, String> line = new HashMap<>();
          for (String column : columnNames) {
            String value = String.valueOf(rs.getObject(column));
            line.put(column, value);
          }
          results.add(line);
        }

        String resultContent;
        if (results.isEmpty()) {
          resultContent = "No results found.";
        } else {
          resultContent = SimpleCSV.unparse(results);
        }
        
        // add the query as the first line so it's easy to see what it was
        String newCsvData = query + System.lineSeparator() + resultContent;
        Path outFilePath = outDirectory.toPath()
            .resolve("custom_query" + reportNum + "_" + System.currentTimeMillis() + ".csv");

        Files.write(outFilePath, Collections.singleton(newCsvData), StandardOpenOption.CREATE_NEW);
        reportNum++;
      }
    }
  }
}
