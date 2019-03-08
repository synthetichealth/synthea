package org.mitre.synthea.helpers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Map;

import org.junit.Test;
import org.mitre.synthea.helpers.Attributes.Inventory;

public class AttributesTest {
  @Test
  public void testAttributes() throws Exception {
    Map<String,Inventory> inventory = Attributes.getAttributeInventory();
    assertNotNull(inventory);
    assertFalse(inventory.isEmpty());
    assertFalse(inventory.containsKey(""));
  }

  @Test
  public void testGraphs() throws Exception {
    Map<String,Inventory> inventory = Attributes.getAttributeInventory();
    String filename = "TestAttributesGraph";
    Attributes.graph(inventory, filename, true);
    File file = new File("./output/" + filename + ".png");
    assertTrue(file.exists());
    assertTrue(file.isFile());
    file.deleteOnExit();
  }
}
