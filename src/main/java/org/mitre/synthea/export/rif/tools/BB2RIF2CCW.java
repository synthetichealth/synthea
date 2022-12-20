package org.mitre.synthea.export.rif.tools;

import static org.mitre.synthea.export.rif.BB2RIFStructure.RIF_FILES;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
          // TODO: convert to streaming approach instead of loading entire file in to memory?
          // This approach works for 10k beneficiaries
          String csvData = new String(Files.readAllBytes(file.toPath()));
          List<LinkedHashMap<String, String>> csv = SimpleCSV.parse(csvData, '|');
          String[] keys = new String[csv.get(0).size()];
          csv.get(0).keySet().toArray(keys); // keySet is live so make copy once
          for (LinkedHashMap<String, String> row: csv) {
            for (String bb2FieldName: keys) {
              String fieldValue = row.remove(bb2FieldName);
              String ccwFieldName = nameMap.get(bb2FieldName);
              if (ccwFieldName != null && ccwFieldName.length() > 0) {
                row.put(ccwFieldName, fieldValue);
              }
            }
          }
          csvData = SimpleCSV.unparse(csv, '|');
          Files.write(outputDir.toPath().resolve(file.getName()), csvData.getBytes());
        }
      } catch (IOException | IllegalArgumentException ex) {
        System.out.println("Warning, skipping " + filePrefix + ": " + ex.getMessage());
      }
    }
  }

  private static Map<String, String> readMapFile(String filePrefix) throws IOException {
    String csvStr = Utilities.readResource("export/" + filePrefix + "_bb2_ccw.csv");
    List<LinkedHashMap<String,String>> csv = SimpleCSV.parse(csvStr);
    HashMap<String, String> map = new HashMap<>();
    for (LinkedHashMap<String,String> entry: csv) {
      map.put(entry.get("BB2"), entry.get("CCW"));
    }
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
