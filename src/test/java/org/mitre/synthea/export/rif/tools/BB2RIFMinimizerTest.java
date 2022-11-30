package org.mitre.synthea.export.rif.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.mitre.synthea.helpers.Utilities;

public class BB2RIFMinimizerTest {
  @Test
  public void testGetMinimalSetOfBenes() throws IOException {
    String csvData = Utilities.readResourceAndStripBOM("export/rif/export_summary.csv");
    List<String> benes = BB2RIFMinimizer.getMinimalSetOfBenes(csvData);
    assertEquals(2, benes.size());
    assertTrue(benes.contains("-1000001"));
    assertTrue(benes.contains("-1000004"));
  }
}
