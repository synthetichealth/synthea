package org.mitre.synthea;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.function.Function;

import org.junit.Assert;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Config;

public abstract class TestHelper {

  /**
   * Returns a test fixture Module by filename.
   * @param filename The filename of the test fixture Module.
   * @return A Module.
   * @throws Exception On errors.
   */
  public static Module getFixture(String filename) throws Exception {
    Path modulesFolder = Paths.get("src/test/resources/generic");
    Path module = modulesFolder.resolve(filename);
    return Module.loadFile(module, modulesFolder);
  }

  /**
   * Helper method to disable export of all data formats and database output.
   * Ensures that unit tests do not pollute the output folders.
   */
  public static void exportOff() {
    Config.set("generate.database_type", "none"); // ensure we don't write to a file-based DB
    Config.set("exporter.use_uuid_filenames", "false");
    Config.set("exporter.subfolders_by_id_substring", "false");
    Config.set("exporter.ccda.export", "false");
    Config.set("exporter.fhir.export", "false");
    Config.set("exporter.fhir_dstu2.export", "false");
    Config.set("exporter.fhir.transaction_bundle", "false");
    Config.set("exporter.text.export", "false");
    Config.set("exporter.csv.export", "false");
    Config.set("exporter.hospital.fhir.export", "false");
    Config.set("exporter.hospital.fhir_dstu2.export", "false");
    Config.set("exporter.cost_access_outcomes_report", "false");
  }

  public static long timestamp(int year, int month, int day, int hr, int min, int sec) {
    return LocalDateTime.of(year, month, day, hr, min, sec).toInstant(ZoneOffset.UTC)
        .toEpochMilli();
  }
}
