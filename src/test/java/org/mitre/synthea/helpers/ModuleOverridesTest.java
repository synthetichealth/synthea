package org.mitre.synthea.helpers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.jayway.jsonpath.internal.Path;
import com.jayway.jsonpath.internal.path.PathCompiler;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class ModuleOverridesTest {

  @Test
  public void testOverridesAllNulls() throws Exception {
    List<String> includeFields = null;
    List<String> includeModules = null;
    List<String> excludeFields = null;
    List<String> excludeModules = null;
    ModuleOverrides mo =
        new ModuleOverrides(includeFields, includeModules, excludeFields, excludeModules);
    List<String> overrides = mo.generateOverrides();
    assertNotNull(overrides);
    assertTrue(overrides.size() == 0);
  }

  @Test
  public void testOverridesDistributions() throws Exception {
    List<String> includeFields = Arrays.asList("distribution");
    List<String> includeModules = null;
    List<String> excludeFields = null;
    List<String> excludeModules = null;
    ModuleOverrides mo =
        new ModuleOverrides(includeFields, includeModules, excludeFields, excludeModules);
    List<String> overrides = mo.generateOverrides();
    assertNotNull(overrides);
    assertTrue(overrides.size() > 0);
    String fullFileContent = String.join(System.lineSeparator(), overrides);
    Properties props = new Properties();
    props.load(IOUtils.toInputStream(fullFileContent, StandardCharsets.UTF_8));

    props.forEach((k, v) -> {
      String key = k.toString();
      String value = v.toString();
      assertStringEndsWith(key, "['distribution']");
      assertCorrectFormat(key, value);
    });
  }

  @Test
  public void testOverridesIncludeModule() throws Exception {
    List<String> includeFields = Arrays.asList("distribution");
    List<String> includeModules = Arrays.asList("med_rec*");
    List<String> excludeFields = null;
    List<String> excludeModules = null;
    ModuleOverrides mo =
        new ModuleOverrides(includeFields, includeModules, excludeFields, excludeModules);
    List<String> overrides = mo.generateOverrides();
    assertNotNull(overrides);
    assertTrue(overrides.size() > 0);
    // note: this is an easy example module, should == 1,
    // but i don't want to tie the test too tightly to a real module that may change
    String fullFileContent = String.join(System.lineSeparator(), overrides);
    Properties props = new Properties();
    props.load(IOUtils.toInputStream(fullFileContent, StandardCharsets.UTF_8));

    props.forEach((k, v) -> {
      String key = k.toString();
      String value = v.toString();
      assertStringContains(key, "med_rec.json");
      assertStringEndsWith(key, "['distribution']");
      assertCorrectFormat(key, value);
    });
  }

  @Test
  public void testOverridesExcludeFields() throws Exception {
    List<String> includeFields = null;
    List<String> includeModules = null;
    List<String> excludeFields = Arrays.asList("distribution");
    List<String> excludeModules = null;
    ModuleOverrides mo =
        new ModuleOverrides(includeFields, includeModules, excludeFields, excludeModules);
    List<String> overrides = mo.generateOverrides();
    assertNotNull(overrides);
    assertTrue(overrides.size() > 0);
    String fullFileContent = String.join(System.lineSeparator(), overrides);
    Properties props = new Properties();
    props.load(IOUtils.toInputStream(fullFileContent, StandardCharsets.UTF_8));

    props.forEach((k, v) -> {
      String key = k.toString();
      String value = v.toString();
      assertStringDoesNotEndWith(key, "['distribution']");
      assertCorrectFormat(key, value);
    });
  }

  @Test
  public void testOverridesExcludeModule() throws Exception {
    List<String> includeFields = Arrays.asList("distribution");
    List<String> includeModules = null;
    List<String> excludeFields = null;
    List<String> excludeModules = Arrays.asList("med_rec*");
    ModuleOverrides mo =
        new ModuleOverrides(includeFields, includeModules, excludeFields, excludeModules);
    List<String> overrides = mo.generateOverrides();
    assertNotNull(overrides);
    assertTrue(overrides.size() > 0);
    String fullFileContent = String.join(System.lineSeparator(), overrides);
    Properties props = new Properties();
    props.load(IOUtils.toInputStream(fullFileContent, StandardCharsets.UTF_8));

    props.forEach((k, v) -> {
      String key = k.toString();
      String value = v.toString();
      assertStringDoesNotContain(key, "med_rec.json");
      assertStringEndsWith(key, "['distribution']");
      assertCorrectFormat(key, value);
    });
  }

  @Test
  public void debugPropertiesGeneration() throws Exception {
    System.out.println("=== DEBUG: Testing properties generation ===");

    List<String> includeFields = Arrays.asList("distribution");
    List<String> includeModules = null;
    List<String> excludeFields = null;
    List<String> excludeModules = null;

    ModuleOverrides mo = new ModuleOverrides(includeFields, includeModules, excludeFields, excludeModules);
    List<String> overrides = mo.generateOverrides();

    System.out.println("DEBUG: Generated " + overrides.size() + " override entries");

    // Show first few entries to see the format
    for (int i = 0; i < Math.min(10, overrides.size()); i++) {
      String line = overrides.get(i);
      System.out.println("DEBUG Line " + i + ": " + line);

      // Check if line contains problematic characters
      if (line.contains("Patient's") || line.contains("Assign")) {
        System.out.println("  *** POTENTIAL PROBLEM LINE ***");
        System.out.println("  Length: " + line.length());
        System.out.println("  Contains apostrophe: " + line.contains("'"));

        // Try to parse just this line as properties
        try {
          Properties testProps = new Properties();
          testProps.load(IOUtils.toInputStream(line, StandardCharsets.UTF_8));
          System.out.println("  ✅ Properties parsing OK");
        } catch (Exception e) {
          System.err.println("  ❌ Properties parsing failed: " + e.getMessage());
        }

        // Try to validate the JSONPath part
        if (line.contains("::")) {
          String[] parts = line.split("::", 2);
          if (parts.length == 2) {
            String[] valueParts = parts[1].split(" = ", 2);
            if (valueParts.length == 2) {
              String jsonPath = valueParts[0];
              System.out.println("  JSONPath part: '" + jsonPath + "'");

              try {
                com.jayway.jsonpath.internal.path.PathCompiler.compile(jsonPath);
                System.out.println("  ✅ JSONPath validation OK");
              } catch (Exception e) {
                System.err.println("  ❌ JSONPath validation failed: " + e.getMessage());
              }
            }
          }
        }
      }
    }

    System.out.println("=== DEBUG: Properties generation complete ===");
  }

  // Add this debug method to ModuleOverridesTest.java
  @Test
  public void debugExcludeFields() throws Exception {
    System.out.println("=== DEBUG: Testing exclude fields scenario ===");

    List<String> includeFields = null;
    List<String> includeModules = null;
    List<String> excludeFields = Arrays.asList("distribution");
    List<String> excludeModules = null;

    ModuleOverrides mo = new ModuleOverrides(includeFields, includeModules, excludeFields, excludeModules);
    List<String> overrides = mo.generateOverrides();

    System.out.println("DEBUG: Generated " + overrides.size() + " override entries");

    // Show first few entries to see what's being excluded/included
    int problemCount = 0;
    for (int i = 0; i < Math.min(20, overrides.size()); i++) {
      String line = overrides.get(i);
      System.out.println("DEBUG Line " + i + ": " + line);

      // Check for problematic patterns
      if (line.contains("Patient") || line.contains("Assign") || line.contains("'")) {
        System.out.println("  *** CHECKING THIS LINE ***");
        problemCount++;

        // Parse as Properties to see what happens
        try {
          Properties testProps = new Properties();
          testProps.load(IOUtils.toInputStream(line, StandardCharsets.UTF_8));

          testProps.forEach((k, v) -> {
            String key = k.toString();
            String value = v.toString();
            System.out.println("  Parsed key: '" + key + "'");
            System.out.println("  Parsed value: '" + value + "'");

            // Test the JSONPath validation that's failing
            try {
              String[] keyParts = key.split("::");
              if (keyParts.length == 2) {
                String jsonPathPart = keyParts[1];
                System.out.println("  JSONPath part: '" + jsonPathPart + "'");

                com.jayway.jsonpath.internal.path.PathCompiler.compile(jsonPathPart);
                System.out.println("  ✅ JSONPath compilation OK");
              }
            } catch (Exception e) {
              System.err.println("  ❌ JSONPath compilation failed: " + e.getMessage());
            }
          });

        } catch (Exception e) {
          System.err.println("  ❌ Properties parsing failed: " + e.getMessage());
        }

        if (problemCount > 5) {
          System.out.println("DEBUG: Stopping after 5 problem lines...");
          break;
        }
      }
    }

    if (problemCount == 0) {
      System.out.println("DEBUG: No problematic lines found in first 20 entries");

      // Try the full Properties loading that the real test does
      String fullFileContent = String.join(System.lineSeparator(), overrides);
      System.out.println("DEBUG: Total content length: " + fullFileContent.length());

      try {
        Properties props = new Properties();
        props.load(IOUtils.toInputStream(fullFileContent, StandardCharsets.UTF_8));
        System.out.println("DEBUG: ✅ Full properties loading successful, " + props.size() + " properties");

        // Check a few keys
        props.forEach((k, v) -> {
          String key = k.toString();
          if (key.contains("Patient") || key.contains("Assign")) {
            System.out.println("DEBUG: Found problematic key: " + key);
          }
        });

      } catch (Exception e) {
        System.err.println("DEBUG: ❌ Full properties loading failed: " + e.getMessage());

        // Try to find the problematic line
        String[] lines = fullFileContent.split(System.lineSeparator());
        for (int i = 0; i < lines.length; i++) {
          try {
            Properties testProps = new Properties();
            testProps.load(IOUtils.toInputStream(lines[i], StandardCharsets.UTF_8));
          } catch (Exception lineEx) {
            System.err.println("DEBUG: Problematic line " + i + ": " + lines[i]);
            break;
          }
        }
      }
    }

    System.out.println("=== DEBUG: Exclude fields debug complete ===");
  }

  private static void assertCorrectFormat(String key, String value) {
    assertTrue(key.contains("::"));
    String[] keyParts = key.split("::");
    String filePart = keyParts[0];
    assertStringEndsWith(filePart, ".json");
    String jsonPathPart = keyParts[1];
    Path jsonPath = PathCompiler.compile(jsonPathPart);
    assertNotNull(jsonPath); // confirm the key is valid JSONPath
    assertTrue(isNumeric(value));
  }

  private static void assertStringContains(String str, String shouldContain) {
    assertTrue("Expected '" + str + "' to contain '" + shouldContain + "'",
        str.contains(shouldContain));
  }

  private static void assertStringDoesNotContain(String str, String shouldContain) {
    assertFalse("Expected '" + str + "' to not contain '" + shouldContain + "'",
        str.contains(shouldContain));
  }

  private static void assertStringEndsWith(String str, String shouldContain) {
    assertTrue("Expected '" + str + "' to end with '" + shouldContain + "'",
        str.endsWith(shouldContain));
  }

  private static void assertStringDoesNotEndWith(String str, String shouldContain) {
    assertFalse("Expected '" + str + "' to not end with '" + shouldContain + "'",
        str.endsWith(shouldContain));
  }

  private static boolean isNumeric(String str) {
    try {
      Double.parseDouble(str);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
