package org.mitre.synthea.world.concepts;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.bean.MappingStrategy;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SnomedConversion {
  // frequently used constants for coding systems.  ICD-10 kept here for modularity
  public static final String SNOMED = "SNOMED-CT";
  public static final String ICD10 = "ICD-10";

  private Map<String, Map<String, String>> snomedMap;

  // map to store ICD-10 codes and their descriptions
  private Map<String, String> icd10DisplayMap;

  public SnomedConversion() {
    snomedMap = new HashMap<>();
  }

  /**
   * Loads a SNOMED to ICD-10 mapping text file and ICD-10 display values CSV file into maps.
   *
   * <p>For the SNOMED conversion map, the keys are SNOMED Codes.
   * The Values are map rules and ICD-10 codes. The text file uses tabs as a separator.  If
   * no file is found, an error will be sent to console and boolean of false is returned. </p>
   *
   * <p>For the ICD-10 display map, the ICD-10 codes are the keys, and the description is the
   * value. If no file is found, an error will be sent to the console and a boolean of false
   * will be returned.</p>
   *
   * @param snomedToIcdMapFile the file location of the Snomed mapping text file.
   * @param icdDisplayFile the file location of the ICD-10 display mapping CSV file.
   * @return boolean indicating the success snomed mapping and ICD-10 file loads (true for success).
   */
  public boolean loadSnomedMap(String snomedToIcdMapFile, String icdDisplayFile) {
    try {
      CSVParser snomedConversionParser = new CSVParserBuilder().withSeparator('\t').build();
      CSVParser icd10DisplayParser = new CSVParserBuilder().withSeparator(',').build();

      BufferedReader snomedConversionBr = Files.newBufferedReader(Paths.get(snomedToIcdMapFile),
          StandardCharsets.UTF_8);
      BufferedReader icd10DisplayBr = Files.newBufferedReader(Paths.get(icdDisplayFile),
          StandardCharsets.UTF_8);

      CSVReader snomedConversionCsvReader = new CSVReaderBuilder(snomedConversionBr)
          .withCSVParser(snomedConversionParser)
          .build();
      CSVReader icd10DisplayCsvReader = new CSVReaderBuilder(icd10DisplayBr)
          .withCSVParser(icd10DisplayParser).build();

      CsvToBean<SnomedMapRecord> snomedMapRecords =
          new CsvToBeanBuilder<SnomedMapRecord>(snomedConversionCsvReader)
              .withSeparator('\t')
              .withMappingStrategy(getMappingStrategy())
              .withType(SnomedMapRecord.class)
              .build();
      CsvToBean<Icd10Display> icd10Displays =
          new CsvToBeanBuilder<Icd10Display>(icd10DisplayCsvReader)
              .withSeparator(',')
              .withMappingStrategy(getDisplayMappingStrategy())
              .withType(Icd10Display.class).build();

      for (SnomedMapRecord snomedMapRecord : snomedMapRecords) {
        Map<String, String> icd10Mapping = snomedMap.getOrDefault(
            snomedMapRecord.getReferencedComponentId(),
            new HashMap<>());

        // This updates the inner map without having to update the outer map if it already exists
        icd10Mapping.put(snomedMapRecord.getMapRule(), snomedMapRecord.getMapTarget());

        snomedMap.putIfAbsent(snomedMapRecord.getReferencedComponentId(), icd10Mapping);
      }

      icd10DisplayMap = new HashMap<>();

      // Iterate over list of Displays and add to Display map for later use
      for (Icd10Display icd10Display : icd10Displays) {
        icd10DisplayMap.put(icd10Display.getCode(), icd10Display.getDisplay());
      }
      return true;
    } catch (IOException e) {
      System.err.println("Warning: no file found for snomed mapping: " + e.getMessage()
          + ". ICD-10 translations from SNOMED will not be able to be performed.");
      // e.printStackTrace();
      return false;
    }
  }

  /**
   * Finds corresponding ICD-10 code for the input SNOMED code or display.
   * If an ICD-10 translation is found for a given SNOMED code, the resulting
   * code will be ICD-10.  Then, an ICD-10 display description is looked for.
   * If one is found, the display description will be ICD-10.  If not, the
   * description will be the source Code's description.
   * If no ICD-10 translation is found, the original Code is returned.
   *
   * @param snomedCode the SNOMED Code that needs to be translated to ICD-10 Code
   * @return the corresponding ICD-10 code if found, otherwise the original code
   */
  public HealthRecord.Code findIcd10Code(HealthRecord.Code snomedCode) {
    Map<String, String> icd10Map = snomedMap.getOrDefault(snomedCode.code, Collections.emptyMap());

    // If a snomed code has any connected icd10 code then
    // grab the first icd10 code that matches any conditions
    // This has a high chance of obtaining the correct icd10 code,
    // otherwise returns a closely related icd10 code
    if (!icd10Map.isEmpty()) {
      for (String rule : icd10Map.keySet()) {
        if (rule.contains(snomedCode.display) || rule.contains("TRUE") || rule.contains("ALWAYS")) {

          // Occasionally, a code will have a trailing question mark. This is removed.
          String icd10Code = icd10Map.get(rule).replace("?", "");

          // The Display CSV file uses ICD-10 codes without the period.  This must be removed.
          String searchCodeFormat = icd10Code.replace(".", "");

          // Attempt to find the display value for a given code
          String icd10Display = icd10DisplayMap.getOrDefault(searchCodeFormat, "");

          // If a display wasn't found, use the first few code numbers to attempt to find a
          // more general code description
          if (icd10Display.isEmpty() && !icd10Code.isEmpty()) {
            icd10Display = icd10DisplayMap.getOrDefault(searchCodeFormat.substring(0,3), "");
          }

          /*
           * A few SNOMED translations do not have ICD-10 codes associated.  If one is blank, then
           * the original Code info is returned.  If an ICD-10 code translation is found for a
           * SNOMED code but a display description is not found, the ICD-10 code is returned with
           * the SNOMED description.  The code system would be set to ICD-10 and the display
           * system would be set to SNOMED.
           */
          return new HealthRecord.Code((icd10Code.isEmpty() ?  snomedCode.system : ICD10),
              (icd10Code.isEmpty() ? snomedCode.code : icd10Code),
              (icd10Display.isEmpty() ? snomedCode.display : icd10Display),
              (icd10Display.isEmpty() ? snomedCode.displaySystem : ICD10));
        }
      }
    }
    // Otherwise there is no match and no related icd10 code. return original code
    return snomedCode;
  }

  private MappingStrategy<SnomedMapRecord> getMappingStrategy() {
    MappingStrategy<SnomedMapRecord> mappingStrategy = new HeaderColumnNameMappingStrategy<>();
    mappingStrategy.setType(SnomedMapRecord.class);
    return mappingStrategy;
  }

  private MappingStrategy<Icd10Display> getDisplayMappingStrategy() {
    MappingStrategy<Icd10Display> mappingStrategy = new HeaderColumnNameMappingStrategy<>();
    mappingStrategy.setType(Icd10Display.class);
    return mappingStrategy;
  }

  public static class SnomedMapRecord {

    @CsvBindByName(column = "referencedComponentId", required = true)
    String referencedComponentId;

    @CsvBindByName(column = "mapRule", required = true)
    String mapRule;

    @CsvBindByName(column = "mapTarget")
    String mapTarget;

    String getReferencedComponentId() {
      return referencedComponentId;
    }

    String getMapRule() {
      return mapRule;
    }

    String getMapTarget() {
      return mapTarget;
    }
  }

  public static class Icd10Display {
    @CsvBindByName(column = "Code", required = true)
    String code;

    @CsvBindByName(column = "Short Description", required = true)
    String display;

    String getCode() {
      return code;
    }

    String getDisplay() {
      return display;
    }
  }
}
