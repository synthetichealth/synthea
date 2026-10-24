package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;

/** Regression coverage for CSV headers and resource file rotation. */
public class CSVFileRotationTest {
  private static final String RESOURCE = CSVConstants.OBSERVATION_KEY;
  private static final String[] CONFIG_KEYS = {
    "exporter.baseDirectory", "exporter.csv.folder_per_run", "exporter.csv.included_files",
    "exporter.csv.excluded_files", "exporter.csv.max_lines_per_file",
    "exporter.csv.append_mode", "exporter.csv.file_number_digits", "exporter.encoding"
  };

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  private final Map<String, String> previousConfig = new HashMap<>();
  private Path output;

  /** Use a fresh output directory and preserve the caller's configuration. */
  @Before
  public void setUp() {
    for (String key : CONFIG_KEYS) {
      previousConfig.put(key, Config.get(key));
    }
    Config.set("exporter.baseDirectory", folder.getRoot().toString());
    Config.set("exporter.csv.folder_per_run", "false");
    Config.set("exporter.csv.included_files", "observations.csv");
    Config.set("exporter.csv.excluded_files", "");
    Config.set("exporter.csv.file_number_digits", "1");
    Config.set("exporter.encoding", "UTF-8");
    output = folder.getRoot().toPath().resolve("csv");
  }

  /** Restore configuration after each regression case. */
  @After
  public void tearDown() {
    previousConfig.forEach((key, value) -> {
      if (value == null) {
        Config.remove(key);
      } else {
        Config.set(key, value);
      }
    });
  }

  @Test
  public void oneRowLimitCreatesAFileForEveryResource() throws Exception {
    write(manager(1, false), 1, 5);
    for (int index = 1; index <= 5; index++) {
      assertFile("observations-" + index + ".csv", index);
    }
    assertFalse(Files.exists(output.resolve("observations-6.csv")));
  }

  @Test
  public void oneRowLimitCanAppendAfterExistingFiles() throws Exception {
    write(manager(1, false), 1, 2);
    write(manager(1, true), 3, 4);
    for (int index = 1; index <= 4; index++) {
      assertFile("observations-" + index + ".csv", index);
    }
    assertFalse(Files.exists(output.resolve("observations-5.csv")));
  }

  @Test
  public void oneRowLimitAppendCanStartWithNoFiles() throws Exception {
    write(manager(1, true), 1, 2);
    assertFile("observations-1.csv", 1);
    assertFile("observations-2.csv", 2);
  }

  @Test
  public void twoRowLimitRotatesAtTheResourceBoundary() throws Exception {
    write(manager(2, false), 1, 5);
    assertFile("observations-1.csv", 1, 2);
    assertFile("observations-2.csv", 3, 4);
    assertFile("observations-3.csv", 5);
    assertFalse(Files.exists(output.resolve("observations-4.csv")));
  }

  @Test
  public void largerLimitSupportsSeveralRotations() throws Exception {
    write(manager(3, false), 1, 7);
    assertFile("observations-1.csv", 1, 2, 3);
    assertFile("observations-2.csv", 4, 5, 6);
    assertFile("observations-3.csv", 7);
  }

  @Test
  public void appendCreatesTheHeaderForANewUnsplitFile() throws Exception {
    write(manager(0, true), 1, 2);
    assertFile("observations.csv", 1, 2);
  }

  @Test
  public void appendKeepsOneHeaderInAnExistingUnsplitFile() throws Exception {
    write(manager(0, false), 1, 2);
    write(manager(0, true), 3, 4);
    assertFile("observations.csv", 1, 2, 3, 4);
  }

  @Test
  public void appendFillsAPartialFileBeforeCreatingTheNext() throws Exception {
    write(manager(3, false), 1, 2);
    write(manager(3, true), 3, 5);
    assertFile("observations-1.csv", 1, 2, 3);
    assertFile("observations-2.csv", 4, 5);
  }

  @Test
  public void appendCreatesANewFileAfterAFullFile() throws Exception {
    write(manager(2, false), 1, 2);
    write(manager(2, true), 3, 5);
    assertFile("observations-1.csv", 1, 2);
    assertFile("observations-2.csv", 3, 4);
    assertFile("observations-3.csv", 5);
  }

  private CSVFileManager manager(int limit, boolean append) {
    Config.set("exporter.csv.max_lines_per_file", String.valueOf(limit));
    Config.set("exporter.csv.append_mode", String.valueOf(append));
    return new CSVFileManager();
  }

  private void write(CSVFileManager manager, int first, int last) throws Exception {
    for (int number = first; number <= last; number++) {
      manager.writeResourceLine(row(number) + System.lineSeparator(), RESOURCE);
    }
    manager.flushWriter(RESOURCE);
  }

  private String row(int number) {
    return "2020-01-01,patient,encounter,laboratory,code,description," + number
        + ",mg,numeric";
  }

  private void assertFile(String name, int... numbers) throws Exception {
    List<String> expected = new ArrayList<>();
    expected.add("DATE,PATIENT,ENCOUNTER,CATEGORY,CODE,DESCRIPTION,VALUE,UNITS,TYPE");
    Arrays.stream(numbers).forEach(number -> expected.add(row(number)));
    Path path = output.resolve(name);
    assertEquals(expected, Files.readAllLines(path, StandardCharsets.UTF_8));
    List<? extends Map<String, String>> parsed =
        SimpleCSV.parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
    assertEquals(numbers.length, parsed.size());
    for (int index = 0; index < numbers.length; index++) {
      assertEquals(String.valueOf(numbers[index]), parsed.get(index).get("VALUE"));
    }
  }
}
