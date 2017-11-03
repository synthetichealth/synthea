package org.mitre.synthea.helpers;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.dataformat.csv.CsvSchema.ColumnType;

/**
 * Helper class to translate CSV data back and forth between raw string data and a simple data
 * structure.
 */
public class SimpleCSV {
  /**
   * Parse the data from the given CSV file into a List of Map<String,String>, where the key is the
   * column name. Uses a LinkedHashMap specifically to ensure the order of columns is preserved in
   * the resulting maps.
   * 
   * @param csvData
   *          Raw CSV data
   * @return parsed data
   * @throws IOException
   *           if any exception occurs while parsing the data
   */
  public static List<LinkedHashMap<String, String>> parse(String csvData) throws IOException {
    // Read schema from the first line; start with bootstrap instance
    // to enable reading of schema from the first line
    // NOTE: reads schema and uses it for binding
    CsvMapper mapper = new CsvMapper();
    CsvSchema schema = CsvSchema.emptySchema().withHeader(); // use first row as header; otherwise
                                                             // defaults are fine

    MappingIterator<LinkedHashMap<String, String>> it = mapper.readerFor(LinkedHashMap.class)
        .with(schema).readValues(csvData);

    return it.readAll();
  }

  /**
   * Convert the data in the given List<Map<String,String>> to a String of CSV data. Each Map in the
   * List represents one line of the resulting CSV. Uses the keySet from the first Map to populate
   * the set of columns. This means that the first Map must contain all the columns desired in the
   * final CSV. The order of the columns is specified by the order provided by the first Map's
   * keySet, so using an ordered Map implementation (such as LinkedHashMap) is recommended.
   * 
   * @param data
   * @return data formatted as a String containing raw CSV data
   * @throws IOException
   */
  public static String unparse(List<? extends Map<String, String>> data) throws IOException {
    CsvMapper mapper = new CsvMapper();
    CsvSchema.Builder schemaBuilder = CsvSchema.builder();
    schemaBuilder.setUseHeader(true);

    Collection<String> columns = data.get(0).keySet();
    schemaBuilder.addColumns(columns, ColumnType.STRING);

    return mapper.writer(schemaBuilder.build()).writeValueAsString(data);
  }
}
