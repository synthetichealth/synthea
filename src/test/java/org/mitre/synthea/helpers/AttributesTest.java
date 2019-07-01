package org.mitre.synthea.helpers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.helpers.Attributes.Inventory;

public class AttributesTest {

  private static Map<String,Inventory> inventory = null;

  @BeforeClass
  public static void setup() throws Exception {
    inventory = Attributes.getAttributeInventory();
  }

  @Test
  public void testAttributes() throws Exception {
    assertNotNull(inventory);
    assertFalse(inventory.isEmpty());
    assertFalse(inventory.containsKey(""));
  }

  @Test
  public void testGraphs() throws Exception {
    String filename = "TestAttributesGraph";
    Attributes.graph(inventory, filename, true);
    File file = new File("./output/" + filename + ".png");
    assertTrue(file.exists());
    assertTrue(file.isFile());
    file.deleteOnExit();
  }
}
