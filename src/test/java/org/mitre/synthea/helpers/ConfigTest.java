package org.mitre.synthea.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import org.junit.Test;

public class ConfigTest {
  @Test
  public void testConfig() throws URISyntaxException, IOException {
    URI uri = ConfigTest.class.getResource("/test.properties").toURI();
    File file = new File(uri);
    Config.load(file);
    Set<String> propertyNames = Config.allPropertyNames();
    assertFalse(propertyNames.isEmpty());

    String[] expected = {"test.foo.bar", "test.foo.baz", "test.bar", "foo"};
    for (String key : expected) {
      assertTrue(propertyNames.contains(key));
    }
  }

  @Test
  public void testMultiConfig() throws URISyntaxException, IOException {
    URI uri1 = ConfigTest.class.getResource("/test.properties").toURI();
    File file1 = new File(uri1);
    Config.load(file1);
    assertEquals("42", Config.get("test.bar"));
    URI uri2 = ConfigTest.class.getResource("/test2.properties").toURI();
    File file2 = new File(uri2);
    Config.load(file2);
    Set<String> propertyNames = Config.allPropertyNames();
    assertFalse(propertyNames.isEmpty());

    String[] expectedFromFirstConfig = {"test.foo.bar", "test.foo.baz", "test.bar", "foo"};
    for (String key : expectedFromFirstConfig) {
      assertTrue(propertyNames.contains(key));
    }
    String[] expectedFromSecondConfig = {"test.foo.bar2", "test.foo.baz2"};
    for (String key : expectedFromSecondConfig) {
      assertTrue(propertyNames.contains(key));
    }
    assertEquals("24", Config.get("test.bar"));
  }

  @Test
  public void testSetAndUnsetConfig() throws IOException, URISyntaxException {
    URI uri = ConfigTest.class.getResource("/test.properties").toURI();
    File file = new File(uri);
    Config.load(file);
    Set<String> propertyNames = Config.allPropertyNames();
    assertFalse(propertyNames.isEmpty());

    assertFalse(propertyNames.contains("bing.bong.do"));
    Config.set("bing.bong.do", "true");

    propertyNames = Config.allPropertyNames();
    assertFalse(propertyNames.isEmpty());
    assertTrue(propertyNames.contains("bing.bong.do"));

    Config.remove("bing.bong.do");
    propertyNames = Config.allPropertyNames();
    assertFalse(propertyNames.contains("bing.bong.do"));

  }
}
