package org.mitre.synthea.export.rif;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.MissingResourceException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.DefaultRandomNumberGenerator;
import org.mitre.synthea.helpers.RandomNumberGenerator;

public class CodeMapperTest {
  private static RandomNumberGenerator random;

  @BeforeClass
  public static void setUp() {
    random = new DefaultRandomNumberGenerator(0);
  }

  @Test
  public void testUnweightedCodeMapper() {
    Config.set("exporter.bfd.require_code_maps", "true");
    CodeMapper mapper = new CodeMapper("export/unweighted_code_map.json");
    assertTrue(mapper.canMap("10509002"));
    assertEquals("ABC20.9", mapper.map("10509002", random));
    assertEquals("ABC209", mapper.map("10509002", random, true));
    assertFalse(mapper.canMap("not a code"));
  }

  @Test
  public void testWeightedCodeMapper() {
    Config.set("exporter.bfd.require_code_maps", "true");
    CodeMapper mapper = new CodeMapper("export/weighted_code_map.json");
    assertTrue(mapper.canMap("10509002"));
    int abcCount = 0;
    int defCount = 0;
    for (int i = 0; i < 10; i++) {
      String code = mapper.map("10509002", random);
      if (code.equals("ABC20.9")) {
        abcCount++;
      } else if (code.equals("DEF20.9")) {
        defCount++;
      }
    }
    assertTrue(defCount > abcCount);
  }

  @Test(expected = MissingResourceException.class)
  public void testThrowsExceptionWhenMapFileMissing() {
    Config.set("exporter.bfd.require_code_maps", "true");
    CodeMapper mapper = new CodeMapper("export/missing_code_map.json");
  }

  @Test
  public void testDoesntThrowsExceptionWhenSuppressed() {
    Config.set("exporter.bfd.require_code_maps", "false");
    CodeMapper mapper = new CodeMapper("export/missing_code_map.json");
  }
}
