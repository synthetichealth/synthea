package org.mitre.synthea.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class UtilitiesVersionTest {
  @Test
  public void runtimeVersionMatchesBundledVersionResource() throws IOException {
    InputStream resource = UtilitiesVersionTest.class.getResourceAsStream("/version.txt");
    assertNotNull("version.txt must be packaged with the application", resource);
    String bundledVersion;
    try (InputStream input = resource) {
      bundledVersion = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
    }
    assertFalse("version.txt must not be empty", bundledVersion.isEmpty());
    assertEquals(bundledVersion, Utilities.SYNTHEA_VERSION.trim());
  }
}
