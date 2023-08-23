package org.mitre.synthea.export.rif.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.mitre.synthea.export.Exporter;
import org.mitre.synthea.export.rif.BB2RIFStructure.EXPORT_SUMMARY;
import org.mitre.synthea.helpers.SimpleCSV;

/**
 * Functionality to filter a set of RIF files to leave only enough beneficiaries to cover all
 * claim types.
 */
public class BB2RIFMinimizer {

  private static final EXPORT_SUMMARY[] ALL_CLAIM_TYPES = {
    EXPORT_SUMMARY.CARRIER_CLAIMS,
    EXPORT_SUMMARY.DME_CLAIMS,
    EXPORT_SUMMARY.HHA_CLAIMS,
    EXPORT_SUMMARY.HOSPICE_CLAIMS,
    EXPORT_SUMMARY.INPATIENT_CLAIMS,
    EXPORT_SUMMARY.OUTPATIENT_CLAIMS,
    EXPORT_SUMMARY.PDE_CLAIMS,
    EXPORT_SUMMARY.SNF_CLAIMS
  };

  /**
   * Filters a set of RIF files to leave only enough beneficiaries to cover all
   * claim types. Reads from SYNTHEA_HOME/output/bfd, writes to SYNTHEA_HOME/output/bfd_min.
   * @param args not used
   * @throws IOException if something goes wrong
   */
  public static void main(String[] args) throws IOException {
    File inputDir = Exporter.getOutputFolder("bfd", null);
    File outputDir = Exporter.getOutputFolder("bfd_min", null);
    outputDir.mkdirs();
    List<String> minimalBenes = getMinimalSetOfBenes(inputDir);
    System.out.printf("Benes %s cover all claim types\n", minimalBenes.toString());
    filterOutputFiles(inputDir, outputDir, minimalBenes);
  }

  private static void filterOutputFiles(File inputDir, File outputDir, List<String> minimalBenes)
          throws IOException {
    for (File f: inputDir.listFiles((file, name) -> name.endsWith(".csv"))) {
      char columnSeparator = '|';
      if (f.getName().equals("export_summary.csv")) {
        columnSeparator = ',';
      }
      String csvData = new String(Files.readAllBytes(f.toPath()));
      List<LinkedHashMap<String, String>> csv = SimpleCSV.parse(csvData, columnSeparator);
      csv.removeIf((row) -> !minimalBenes.contains(row.get(EXPORT_SUMMARY.BENE_ID.toString())));
      if (csv.size() > 0) {
        Files.write(outputDir.toPath().resolve(f.getName()),
                SimpleCSV.unparse(csv, columnSeparator).getBytes());
      } else {
        // this shoudn't happen since the minimum set of benes should cover all claim types
        System.out.printf("Unexpectedly empty: %s", f.getName());
        System.exit(-1);
      }
    }
  }

  private static List<String> getMinimalSetOfBenes(File inputDir)
          throws FileNotFoundException, IOException {
    Path summaryPath = inputDir.toPath().resolve("export_summary.csv").toAbsolutePath();
    if (!summaryPath.toFile().exists()) {
      throw new FileNotFoundException(String.format(
              "Export summary file (%s) not found - did you run the RIF exporter?",
              summaryPath.toString()));
    }

    String csvData = new String(Files.readAllBytes(summaryPath));
    return getMinimalSetOfBenes(csvData);
  }

  static List<String> getMinimalSetOfBenes(String csvData) throws IOException {
    List<LinkedHashMap<String, String>> csv = SimpleCSV.parse(csvData);
    List<BeneClaims> beneClaims = new ArrayList<>(csv.size());
    for (LinkedHashMap<String, String> csvRow: csv) {
      beneClaims.add(new BeneClaims(csvRow));
    }

    // Find set of benes that includes all claim types
    Set<EXPORT_SUMMARY> coveredClaimTypes = new HashSet<>();
    List<String> minimizedBenes = new ArrayList<>(ALL_CLAIM_TYPES.length);
    // First, sort list of benes by number of claim types DESC, claimCount ASC
    Comparator<BeneClaims> comp = Comparator
            .comparing(BeneClaims::getClaimTypeCount).reversed()
            .thenComparing(BeneClaims::getTotalClaims);
    List<BeneClaims> sortedBenes = beneClaims.stream()
            .sorted(comp)
            .collect(Collectors.toList());
    // Add the first bene - the one with with most coverage and least number of claims
    BeneClaims firstBene = sortedBenes.get(0);
    minimizedBenes.add(firstBene.getBeneId());
    coveredClaimTypes.addAll(firstBene.getClaimTypes());
    // Resort list of benes by number of claim types ASC, claimCount ASC
    comp = Comparator
            .comparing(BeneClaims::getClaimTypeCount)
            .thenComparing(BeneClaims::getTotalClaims);
    sortedBenes = beneClaims.stream()
            .sorted(comp)
            .collect(Collectors.toList());
    // Add benes that include a not-already-covered claim type
    for (BeneClaims bene: sortedBenes) {
      if (!coveredClaimTypes.containsAll(bene.claimTypes)) {
        minimizedBenes.add(bene.getBeneId());
        coveredClaimTypes.addAll(bene.getClaimTypes());
        if (coveredClaimTypes.size() == ALL_CLAIM_TYPES.length) {
          break;
        }
      }
    }
    if (coveredClaimTypes.size() != ALL_CLAIM_TYPES.length) {
      System.out.println("Current set of benes does not cover all claim types");
      System.exit(-1);
    }
    return minimizedBenes;
  }

  private static class BeneClaims {
    private String beneId;
    private long totalClaims;
    private Set<EXPORT_SUMMARY> claimTypes;

    public BeneClaims(Map<String, String> csvRow) {
      beneId = csvRow.get(EXPORT_SUMMARY.BENE_ID.toString());
      totalClaims = 0;
      claimTypes = new HashSet<>();

      for (EXPORT_SUMMARY field: ALL_CLAIM_TYPES) {
        long claimCount = Long.parseLong(csvRow.get(field.toString()));
        if (claimCount > 0) {
          totalClaims += claimCount;
          claimTypes.add(field);
        }
      }
    }

    public String getBeneId() {
      return beneId;
    }

    public long getTotalClaims() {
      return totalClaims;
    }

    public Set<EXPORT_SUMMARY> getClaimTypes() {
      return claimTypes;
    }

    public int getClaimTypeCount() {
      return getClaimTypes().size();
    }
  }
}
