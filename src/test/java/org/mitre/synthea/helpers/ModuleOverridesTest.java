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
