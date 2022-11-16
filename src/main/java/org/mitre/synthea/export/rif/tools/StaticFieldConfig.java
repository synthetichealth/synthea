package org.mitre.synthea.export.rif.tools;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mitre.synthea.export.rif.BB2RIFStructure;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;

/**
 * Class to manage mapping values in the static BFD TSV writer to the exported writers.
 */
public class StaticFieldConfig {

  List<LinkedHashMap<String, String>> config;
  Map<String, LinkedHashMap<String, String>> configMap;

  /**
   * Default constructor that parses the TSV config writer.
   * @throws IOException if the writer can't be read.
   */
  public StaticFieldConfig() throws IOException {
    String tsv = Utilities.readResource("export/bfd_field_values.tsv");
    config = SimpleCSV.parse(tsv, '\t');
    configMap = new HashMap<>();
    for (LinkedHashMap<String, String> row : config) {
      configMap.put(row.get("Field"), row);
    }
  }

  /**
   * Only used for unit tests.
   * @param <E> the type parameter
   * @param field the name of a value in the supplied enum class (e.g. DML_IND).
   * @param tableEnum one of the exporter enums (e.g. InpatientFields or OutpatientFields).
   * @return the cell value in the TSV where field identifies the row and tableEnum is the column.
   */
  <E extends Enum<E>> String getValue(String field, Class<E> tableEnum) {
    return configMap.get(field).get(tableEnum.getSimpleName());
  }

  /**
   * Validate the TSV file.
   * @return a list of issues found in the TSV
   */
  public Set<String> validateTSV() {
    LinkedHashSet tsvIssues = new LinkedHashSet<>();
    for (Class tableEnum : BB2RIFStructure.RIF_FILES) {
      String columnName = tableEnum.getSimpleName();
      Method valueOf;
      try {
        valueOf = tableEnum.getMethod("valueOf", String.class);
      } catch (NoSuchMethodException | SecurityException ex) {
        // this should never happen since tableEnum has to be an enum which will have a valueOf
        // method but the compiler isn't clever enought to figure that out
        throw new IllegalArgumentException(ex);
      }
      for (LinkedHashMap<String, String> row : config) {
        String cellContents = stripComments(row.get(columnName));
        if (cellContents.equalsIgnoreCase("N/A")
                || cellContents.equalsIgnoreCase("Coded")
                || cellContents.equalsIgnoreCase("[Blank]")) {
          continue; // Skip fields that aren't used are required to be blank or are hand-coded
        } else if (isMacro(cellContents)) {
          tsvIssues.add(String.format("Skipping macro in TSV line %s [%s] for %s",
                  row.get("Line"), row.get("Field"), columnName));
          continue; // Skip unsupported macro's in the TSV
        } else if (cellContents.isEmpty()) {
          // tsvIssues.add(String.format(
          //        "Empty cell in TSV line %s [%s] for %s",
          //        row.get("Line"), row.get("Field"), columnName));
          continue; // Skip empty cells
        }
        try {
          Enum enumVal = (Enum) valueOf.invoke(null, row.get("Field"));
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
          // This should only happen if the TSV contains a value for a field when the
          // columnName enum does not contain that field value.
          tsvIssues.add(String.format("Error in TSV line %s [%s] for %s (field not in enum): %s",
                  row.get("Line"), row.get("Field"), columnName, ex.toString()));
        }
      }
    }
    return tsvIssues;
  }

  /**
   * Set the configured values from the BFD TSV into the supplied map.
   * @param <E> the type parameter.
   * @param values the map that will receive the TSV-configured values.
   * @param tableEnum the enum class for the BFD table (e.g. InpatientFields or OutpatientFields).
   * @param rand source of randomness
   */
  public <E extends Enum<E>> void setValues(HashMap<E, String> values, Class<E> tableEnum,
          RandomNumberGenerator rand) {
    // Get the name of the columnName to populate. This must match a column name in the
    // config TSV.
    String columnName = tableEnum.getSimpleName();
    // Get the valueOf method for the supplied enum using reflection.
    // We'll use this to convert the string field name to the corresponding enum value
    Method valueOf;
    try {
      valueOf = tableEnum.getMethod("valueOf", String.class);
    } catch (NoSuchMethodException | SecurityException ex) {
      // this should never happen since tableEnum has to be an enum which will have a valueOf
      // method but the compiler isn't clever enought to figure that out
      throw new IllegalArgumentException(ex);
    }
    // Iterate over all of the rows in the TSV
    for (LinkedHashMap<String, String> row : config) {
      String cellContents = stripComments(row.get(columnName));
      String value = null;
      if (cellContents.equalsIgnoreCase("N/A") || cellContents.equalsIgnoreCase("Coded")) {
        continue; // Skip fields that aren't used or are hand-coded
      } else if (cellContents.equalsIgnoreCase("[Blank]")) {
        value = " "; // Literally blank
      } else if (isMacro(cellContents)) {
        continue; // Skip unsupported macro's in the TSV
      } else if (cellContents.isEmpty()) {
        continue; // Skip empty cells
      } else {
        value = processCell(cellContents, rand);
      }
      try {
        E enumVal = (E) valueOf.invoke(null, row.get("Field"));
        values.put(enumVal, value);
      } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
        // This should only happen if the TSV contains a value for a field when the
        // columnName enum does not contain that field value.
      }
    }
  }

  /**
   * Remove comments (that consist of text in braces like this) and white space. Note that
   * this is greedy so "(foo) bar (baz)" would yield "".
   * @param str the string to strip
   * @return str with comments and white space removed.
   */
  private static String stripComments(String str) {
    str = str.replaceAll("\\(.*\\)", "");
    return str.trim();
  }

  private static boolean isMacro(String cellContents) {
    return cellContents.startsWith("[");
  }

  /**
   * Process a TSV cell. Content should be either a single value or a comma separated list of
   * value. Single values will be returned unchanged, if a list of values is supplied then one
   * is chosen at random and returned with any leading or trailing white space removed.
   * @param cellContents TSV cell contents.
   * @param rand a source of randomness.
   * @return the selected value.
   */
  static String processCell(String cellContents, RandomNumberGenerator rand) {
    String retval = cellContents;
    if (cellContents.contains(",")) {
      List<String> values = Arrays.asList(retval.split(","));
      int index = rand.randInt(values.size());
      retval = values.get(index).trim();
    }
    return retval;
  }
}
