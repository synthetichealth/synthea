package org.mitre.synthea.helpers;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.Assert;
import org.junit.Test;

public class FactTableTest {

  @Test
  public void testFactTable() {
    // Setup a basic fact table with a header.
    FactTable table = new FactTable();
    table.setHeader("ID,KEY,NAME,DESCRIPTION");

    // Insert some facts.
    int h = table.addFact("H", "Hydrogen,Highly flammable gas");
    int he = table.addFact("He", "Helium,Inert gas");

    // Test ID lookups.
    Assert.assertTrue(table.getFactId("H") == h);
    Assert.assertTrue(table.getFactId("He") == he);

    // Make sure duplicate facts have the same ID.
    int h2 = table.addFact("H", "Hydrogen,Highly flammable gas");
    Assert.assertTrue(h == h2);

    StringWriter writer = new StringWriter();
    try {
      table.write(writer);
    } catch (IOException e) {
      Assert.fail(e.getMessage());
    }
    String output = writer.toString();
    Assert.assertTrue(output.contains(h + ",H"));
    Assert.assertTrue(output.contains(he + ",He"));    
  }

}
