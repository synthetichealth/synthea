package org.mitre.synthea.export;

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
import org.mitre.synthea.export.BB2RIFStructure.EXPORT_SUMMARY;
import org.mitre.synthea.helpers.SimpleCSV;

import static org.mitre.synthea.export.BB2RIFStructure.EXPORT_SUMMARY.*;

/**
 * Functionality to filter a set of RIF files to leave only enough beneficiaries to cover all
 * claim types.
 */
public class BB2RIFMinimizer {

  private static final EXPORT_SUMMARY ALL_CLAIM_TYPES[] = {
    CARRIER_CLAIMS,
    DME_CLAIMS,
    HHA_CLAIMS,
    HOSPICE_CLAIMS,
    INPATIENT_CLAIMS,
    OUTPATIENT_CLAIMS,
    PDE_CLAIMS,
    SNF_CLAIMS
  };

  public static void main(String args[]) throws IOException {
    File output = Exporter.getOutputFolder("bfd", null);
    Path summaryPath = output.toPath().resolve("export_summary.csv").toAbsolutePath();
    if (!summaryPath.toFile().exists()) {
      throw new FileNotFoundException(String.format(
              "Export summary file (%s) not found - did you run the RIF exporter?",
              summaryPath.toString()));
    }

    String csvData = new String(Files.readAllBytes(summaryPath));
    List<LinkedHashMap<String, String>> csv = SimpleCSV.parse(csvData);
    List<BeneClaims> beneClaims = new ArrayList<>(csv.size());
    for (LinkedHashMap<String, String> csvRow: csv) {
      beneClaims.add(new BeneClaims(csvRow));
    }
    System.out.printf("Summary has %d benes\n", beneClaims.size());

    // Sort list of benes by number of claim types DESC, claimCount ASC
    Comparator<BeneClaims> comp = Comparator
            .comparing(BeneClaims::getClaimTypeCount).reversed()
            .thenComparing(BeneClaims::getTotalClaims);
    List<BeneClaims> sortedBenes = beneClaims.stream()
            .sorted(comp)
            .collect(Collectors.toList());

    // Find set of benes that includes all claim types
    Set<EXPORT_SUMMARY> coveredClaimTypes = new HashSet<>();
    List<String> minimizedBenes = new ArrayList<>(ALL_CLAIM_TYPES.length);
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

    System.out.printf("Benes %s cover all claim types\n", minimizedBenes.toString());
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
