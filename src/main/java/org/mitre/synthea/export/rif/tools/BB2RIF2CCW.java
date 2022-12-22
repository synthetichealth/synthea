package org.mitre.synthea.export.rif.tools;

import static org.mitre.synthea.export.rif.BB2RIFStructure.RIF_FILES;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.mitre.synthea.export.Exporter;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;

/**
 * Utility for converting from BB2 RIF file format to CCW RIF file format.
 */
public class BB2RIF2CCW {

  /**
   * Convert BB2 RIF file to the CCW RIF format.
   * Read in each BB2 RIF file from output/bfd, remove any unmappable columns, rename
   * mappable columns, then write the result to output/ccw.
   * @param args unused
   */
  public static void main(String[] args) {
    File inputDir = Exporter.getOutputFolder("bfd", null);
    File outputDir = Exporter.getOutputFolder("ccw", null);
    outputDir.mkdirs();
    for (Class<?> rifFile: RIF_FILES) {
      String filePrefix = rifFile.getSimpleName().toLowerCase();
      try {
        Map<String, String> nameMap = readMapFile(filePrefix);
        for (File file: getSourceFiles(filePrefix, inputDir)) {
          System.out.println("Converting " + file.toString());
          convertFile(file, outputDir, nameMap);
        }
      } catch (IOException | IllegalArgumentException ex) {
        System.out.println("Warning, skipping " + filePrefix + ": " + ex.getMessage());
      }
    }
  }

  private static void convertFile(File file, File outputDir, Map<String, String> nameMap) {
    try {
      CsvMapper mapper = new CsvMapper();
      CsvSchema schema = CsvSchema.emptySchema().withHeader().withColumnSeparator('|');
      MappingIterator<LinkedHashMap<String, String>> sourceRows = mapper
              .readerFor(LinkedHashMap.class).with(schema).readValues(file);
      boolean firstOutputRow = true;
      SequenceWriter writer = null;
      File outputFile = outputDir.toPath().resolve(file.getName()).toFile();
      while (sourceRows.hasNextValue()) {
        LinkedHashMap<String, String> outputRow = transformRow(sourceRows.next(), nameMap);
        if (firstOutputRow) {
          CsvSchema.Builder schemaBuilder = CsvSchema.builder();
          schemaBuilder.setUseHeader(true).setColumnSeparator('|').disableQuoteChar();
          schemaBuilder.addColumns(outputRow.keySet(), CsvSchema.ColumnType.STRING);
          writer = mapper.writer(schemaBuilder.build()).writeValues(outputFile);
          firstOutputRow = false;
        }
        writer.write(outputRow);
      }
      if (writer != null) {
        writer.close();
      }
      sourceRows.close();
    } catch (IOException ex) {
      System.out.println("Error, skipping " + file.getName() + ": " + ex.getMessage());;
    }
  }

  private static LinkedHashMap<String, String> transformRow(LinkedHashMap<String, String> row,
          Map<String, String> nameMap) {
    LinkedHashMap<String, String> transformedRow = new LinkedHashMap<>();
    row.keySet().forEach(bb2FieldName -> {
      String ccwFieldName = nameMap.get(bb2FieldName);
      if (ccwFieldName != null && ccwFieldName.length() > 0) {
        transformedRow.put(ccwFieldName, row.get(bb2FieldName));
      }
    });
    return transformedRow;
  }

  private static Map<String, String> readMapFile(String filePrefix) throws IOException {
    String csvStr = Utilities.readResource("export/" + filePrefix + "_bb2_ccw.csv");
    List<LinkedHashMap<String,String>> csv = SimpleCSV.parse(csvStr);
    HashMap<String, String> map = new HashMap<>();
    csv.forEach(entry -> {
      map.put(entry.get("BB2"), entry.get("CCW"));
    });
    return map;
  }

  private static File[] getSourceFiles(String filePrefix, File inputDir) {
    if (filePrefix.equals("beneficiary")) {
      return inputDir.listFiles((dir, filename) -> {
        return filename.startsWith(filePrefix) && !filename.contains("history");
      });
    } else {
      return inputDir.listFiles((dir, filename) -> {
        return filename.startsWith(filePrefix);
      });
    }
  }
}
