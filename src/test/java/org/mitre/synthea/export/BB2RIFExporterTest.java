package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.TestHelper;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.export.BB2RIFExporter.CodeMapper;
import org.mitre.synthea.export.BB2RIFExporter.HICN;
import org.mitre.synthea.export.BB2RIFExporter.MBI;
import org.mitre.synthea.export.BB2RIFExporter.StaticFieldConfig;
import org.mitre.synthea.export.BB2RIFStructure.BENEFICIARY;
import org.mitre.synthea.export.BB2RIFStructure.CARRIER;
import org.mitre.synthea.export.BB2RIFStructure.DME;
import org.mitre.synthea.export.BB2RIFStructure.INPATIENT;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

public class BB2RIFExporterTest {
  /**
   * Temporary folder for any exported files, guaranteed to be deleted at the end of the test.
   */
  @ClassRule
  public static TemporaryFolder tempFolder = new TemporaryFolder();
  
  private static File exportDir;

  /**
   * Global setup for export tests.
   * @throws Exception if something goes wrong
   */
  @BeforeClass
  public static void setUpExportDir() throws Exception {
    TestHelper.exportOff();
    TestHelper.loadTestProperties();
    Generator.DEFAULT_STATE = Config.get("test_state.default", "Massachusetts");
    Config.set("exporter.bfd.export", "true");
    Config.set("exporter.bfd.require_code_maps", "false");
    exportDir = tempFolder.newFolder();
    Config.set("exporter.baseDirectory", exportDir.toString());
    BB2RIFExporter.getInstance().prepareOutputFiles();
  }
  
  @Test
  public void testBB2Export() throws Exception {
    int numberOfPeople = 10;
    Exporter.ExporterRuntimeOptions exportOpts = new Exporter.ExporterRuntimeOptions();
    Generator.GeneratorOptions generatorOpts = new Generator.GeneratorOptions();
    generatorOpts.population = numberOfPeople;
    Generator generator = new Generator(generatorOpts, exportOpts);
    generator.options.overflow = false;
    for (int i = 0; i < numberOfPeople; i++) {
      generator.generatePerson(i);
    }
    // Adding post completion exports to generate organizations and providers CSV files
    Exporter.runPostCompletionExports(generator, exportOpts);

    // if we get here we at least had no exceptions

    Path expectedExportPath = exportDir.toPath().resolve("bfd");
    File expectedExportFolder = expectedExportPath.toFile();
    assertTrue(expectedExportFolder.exists() && expectedExportFolder.isDirectory());

    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**/beneficiary_*.csv");
    List<File> beneFiles = Files.list(expectedExportPath)
                .filter(Files::isRegularFile)
                .filter((path) -> matcher.matches(path))
                .map(Path::toFile)
                .collect(Collectors.toList());
    assertTrue("Expected at least one beneficiary file", beneFiles.size() > 0);
    for (File beneficiaryFile: beneFiles) {
      assertTrue(beneficiaryFile.exists() && beneficiaryFile.isFile());
      String csvData = new String(Files.readAllBytes(beneficiaryFile.toPath()));

      // the BB2 exporter doesn't use the SimpleCSV class to write the data,
      // so we can use it here for a level of validation
      List<LinkedHashMap<String, String>> rows = SimpleCSV.parse(csvData, '|');
      assertTrue(
              "Expected at least 1 row in the beneficiary file, found " + rows.size(),
              rows.size() >= 1);
      rows.forEach(row -> {
        assertTrue("Expected non-zero length surname", 
                row.containsKey("BENE_SRNM_NAME") && row.get("BENE_SRNM_NAME").length() > 0);
      });
    }
    
    File inpatientFile = expectedExportFolder.toPath().resolve("inpatient.csv").toFile();
    assertTrue(inpatientFile.exists() && inpatientFile.isFile());
    // TODO: more meaningful testing of contents (e.g. count of inpatient claims)

    File outpatientFile = expectedExportFolder.toPath().resolve("outpatient.csv").toFile();
    assertTrue(outpatientFile.exists() && outpatientFile.isFile());
    // TODO: more meaningful testing of contents (e.g. count of outpatient claims)

    File carrierFile = expectedExportFolder.toPath().resolve("carrier.csv").toFile();
    assertTrue(carrierFile.exists() && carrierFile.isFile());
    // TODO: more meaningful testing of contents

    File beneficiaryHistoryFile = expectedExportFolder.toPath()
            .resolve("beneficiary_history.csv").toFile();
    assertTrue(beneficiaryHistoryFile.exists() && beneficiaryHistoryFile.isFile());
    // TODO: more meaningful testing of contents

    File dmeFile = expectedExportFolder.toPath().resolve("dme.csv").toFile();
    assertTrue(dmeFile.exists() && dmeFile.isFile());
    // TODO: more meaningful testing of contents
  }

  @Test
  public void testCodeMapper() {
    // these tests depend on the presence of the code map file and will not be run in CI
    try {
      Utilities.readResource("condition_code_map.json");
    } catch (IOException | IllegalArgumentException e) {
      return;
    }
    Exporter.ExporterRuntimeOptions exportOpts = new Exporter.ExporterRuntimeOptions();
    Generator.GeneratorOptions generatorOpts = new Generator.GeneratorOptions();
    Generator generator = new Generator(generatorOpts, exportOpts);
    CodeMapper mapper = new CodeMapper("condition_code_map.json");
    assertTrue(mapper.canMap("10509002"));
    assertEquals("J20.9", mapper.map("10509002", generator));
    assertEquals("J209", mapper.map("10509002", generator, true));
    assertFalse(mapper.canMap("not a code"));
  }
  
  @Test
  public void testMBI() {
    MBI mbi = new MBI(MBI.MIN_MBI);
    assertEquals("1S00A00AA00", mbi.toString());
    mbi = new MBI(MBI.MAX_MBI);
    assertEquals("9SY9YY9YY99", mbi.toString());
    mbi = MBI.parse("1S00A00AA00");
    assertEquals("1S00A00AA00", mbi.toString());
    mbi = MBI.parse("1S00-A00-AA00");
    assertEquals("1S00A00AA00", mbi.toString());
    mbi = MBI.parse("9SY9YY9YY99");
    assertEquals("9SY9YY9YY99", mbi.toString());
    mbi = MBI.parse("9SY9-YY9-YY99");
    assertEquals("9SY9YY9YY99", mbi.toString());
  }
  
  @Test
  public void testHICN() {
    HICN hicn = new HICN(HICN.MIN_HICN);
    assertEquals("T00000000A", hicn.toString());
    hicn = new HICN(HICN.MAX_HICN);
    assertEquals("T99999999A", hicn.toString());
    hicn = HICN.parse("T01001001A");
    assertEquals("T01001001A", hicn.toString());
    hicn = HICN.parse("T99999999A");
    assertEquals("T99999999A", hicn.toString());
  }
  
  @Test
  public void testStaticFieldConfig() throws IOException, NoSuchMethodException {
    RandomNumberGenerator rand = new Person(System.currentTimeMillis());
    
    assertEquals("foo", StaticFieldConfig.processCell("foo", rand));
    String randomVal = StaticFieldConfig.processCell("1, 2, 3", rand);
    assertTrue(randomVal.equalsIgnoreCase("1") || randomVal.equalsIgnoreCase("2")
            || randomVal.equalsIgnoreCase("3"));
    
    StaticFieldConfig config = new StaticFieldConfig();
    assertEquals("INSERT", config.getValue("DML_IND", INPATIENT.class));
    assertEquals("82 (DMEPOS)", config.getValue("NCH_CLM_TYPE_CD", DME.class));
    assertEquals("71 (local carrier, non-DME)",
            config.getValue("NCH_CLM_TYPE_CD", CARRIER.class));
    
    HashMap<BENEFICIARY, String> values = new HashMap<>();
    config.setValues(values, BENEFICIARY.class, rand);
    assertEquals("INSERT", values.get(BENEFICIARY.DML_IND));
    String sexIdent = values.get(BENEFICIARY.BENE_SEX_IDENT_CD);
    assertTrue(sexIdent.equals("1") || sexIdent.equals("2"));
  }
  
  @Test
  public void testPartDContractPeriod() {
    RandomNumberGenerator rand = new Person(System.currentTimeMillis());
    LocalDate start = LocalDate.of(2020, Month.MARCH, 15);
    LocalDate end = LocalDate.of(2021, Month.JUNE, 15);
    BB2RIFExporter.PartDContractHistory history
            = new BB2RIFExporter.PartDContractHistory(
                    rand, java.time.Instant.now().toEpochMilli(), 10);
    BB2RIFExporter.PartDContractHistory.PartDContractPeriod period
            = history.new PartDContractPeriod(start, end, null);
    assertNull(period.getContractID());
    
    assertFalse(period.coversYear(2019));
    assertEquals(0, period.getCoveredMonths(2019).size());
    
    assertTrue(period.coversYear(2020));
    List<Integer> twentyTwentyMonths = period.getCoveredMonths(2020);
    assertEquals(10, twentyTwentyMonths.size());
    assertFalse(twentyTwentyMonths.contains(1));
    assertFalse(twentyTwentyMonths.contains(2));
    assertTrue(twentyTwentyMonths.contains(3));
    assertTrue(twentyTwentyMonths.contains(4));
    assertTrue(twentyTwentyMonths.contains(5));
    assertTrue(twentyTwentyMonths.contains(6));
    assertTrue(twentyTwentyMonths.contains(7));
    assertTrue(twentyTwentyMonths.contains(8));
    assertTrue(twentyTwentyMonths.contains(9));
    assertTrue(twentyTwentyMonths.contains(10));
    assertTrue(twentyTwentyMonths.contains(11));
    assertTrue(twentyTwentyMonths.contains(12));
    
    assertTrue(period.coversYear(2021));
    List<Integer> twentyTwentyOneMonths = period.getCoveredMonths(2021);
    assertEquals(6, twentyTwentyOneMonths.size());
    assertTrue(twentyTwentyOneMonths.contains(1));
    assertTrue(twentyTwentyOneMonths.contains(2));
    assertTrue(twentyTwentyOneMonths.contains(3));
    assertTrue(twentyTwentyOneMonths.contains(4));
    assertTrue(twentyTwentyOneMonths.contains(5));
    assertTrue(twentyTwentyOneMonths.contains(6));
    assertFalse(twentyTwentyOneMonths.contains(7));
    assertFalse(twentyTwentyOneMonths.contains(8));
    assertFalse(twentyTwentyOneMonths.contains(9));
    assertFalse(twentyTwentyOneMonths.contains(10));
    assertFalse(twentyTwentyOneMonths.contains(11));
    assertFalse(twentyTwentyOneMonths.contains(12));
    
    assertFalse(period.coversYear(2022));
    assertEquals(0, period.getCoveredMonths(2022).size());
    
    LocalDate pointInTime = start;
    Instant instant = pointInTime.atStartOfDay(ZoneId.systemDefault()).toInstant();
    long timeInMillis = instant.toEpochMilli();
    assertTrue(period.covers(timeInMillis));
    
    pointInTime = start.minusDays(1); // previous day (start is middle of month)
    instant = pointInTime.atStartOfDay(ZoneId.systemDefault()).toInstant();
    timeInMillis = instant.toEpochMilli();
    assertTrue(period.covers(timeInMillis));
    
    pointInTime = start.minusDays(15); // previous month
    instant = pointInTime.atStartOfDay(ZoneId.systemDefault()).toInstant();
    timeInMillis = instant.toEpochMilli();
    assertFalse(period.covers(timeInMillis));
    
    pointInTime = end;
    instant = pointInTime.atStartOfDay(ZoneId.systemDefault()).toInstant();
    timeInMillis = instant.toEpochMilli();
    assertTrue(period.covers(timeInMillis));
    
    pointInTime = end.plusDays(1); // next day (end is middle of month)
    instant = pointInTime.atStartOfDay(ZoneId.systemDefault()).toInstant();
    timeInMillis = instant.toEpochMilli();
    assertTrue(period.covers(timeInMillis));

    pointInTime = end.plusDays(16); // next month (end is middle of month)
    instant = pointInTime.atStartOfDay(ZoneId.systemDefault()).toInstant();
    timeInMillis = instant.toEpochMilli();
    assertFalse(period.covers(timeInMillis));
  }
}
