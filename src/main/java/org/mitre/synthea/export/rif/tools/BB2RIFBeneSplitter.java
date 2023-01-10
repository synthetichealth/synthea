package org.mitre.synthea.export.rif.tools;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility for converting from original BB2 RIF 3 file bene output to one bene file per year.
 */
public class BB2RIFBeneSplitter {

  static final String[] inputFiles = {
    "beneficiary.csv",
    "beneficiary_interim.csv",
    "beneficiary_final.csv"
  };

  static final String[] yearFields = {
    "RFRNC_YR", // BFD
    "BENE_ENROLLMT_REF_YR" // CCW
  };

  /**
   * Split original 3 bene files into one file per year.Read in original beneficiary.csv,
   * beneficiary_interim.csv and beneficiary_final.csv from the current directory and output one
   * or more beneficiary_YYYY.csv files where YYYY is the numerical year.
   * Works with either BFD or CCW format files and preserves whatever format exists in the output
   * files
   * @param args unused
   * @throws java.io.IOException if something goes wrong
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.out.println("Usage ./gradlew rifBeneSplit -Pargs=\"inputDir\"");
      System.exit(-1);
    }
    Map<String, SequenceWriter> writers = new HashMap<>();
    for (String inputFileName: inputFiles) {
      File inputFile = Path.of(args[0]).resolve(inputFileName).toFile();
      CsvMapper mapper = new CsvMapper();
      CsvSchema schema = CsvSchema.emptySchema().withHeader().withColumnSeparator('|');
      MappingIterator<LinkedHashMap<String, String>> sourceRows = mapper
              .readerFor(LinkedHashMap.class).with(schema).readValues(inputFile);
      while (sourceRows.hasNextValue()) {
        LinkedHashMap<String, String> inputRow = sourceRows.next();
        String year = getYear(inputRow);
        if (year == null) {
          throw new IOException(
                  String.format("Error: %s does not contain a year field", inputFileName));
        }
        if (!writers.containsKey(year)) {
          writers.put(year, getWriter(year, mapper, args[0], inputRow));
        }
        writers.get(year).write(inputRow);
      }
      sourceRows.close();
    }
    for (SequenceWriter writer: writers.values()) {
      writer.close();
    }
  }

  private static String getYear(LinkedHashMap<String, String> row) {
    String year = null;
    for (String yearField: yearFields) {
      year = row.get(yearField);
      if (year != null) {
        break;
      }
    }
    return year;
  }

  private static SequenceWriter getWriter(String year, CsvMapper mapper, String outputDir,
          LinkedHashMap<String, String> inputRow) throws IOException {
    File outputFile = Path.of(outputDir).resolve("beneficiary_" + year + ".csv").toFile();
    CsvSchema.Builder schemaBuilder = CsvSchema.builder();
    schemaBuilder.setUseHeader(true).setColumnSeparator('|').disableQuoteChar();
    schemaBuilder.addColumns(inputRow.keySet(), CsvSchema.ColumnType.STRING);
    return mapper.writer(schemaBuilder.build()).writeValues(outputFile);
  }
}
