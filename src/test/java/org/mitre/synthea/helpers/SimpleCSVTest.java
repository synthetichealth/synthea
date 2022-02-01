package org.mitre.synthea.helpers;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.Test;

public class SimpleCSVTest {

  private static final String TEST_CSV = "ID,NAME,AGE\n0,Alice,30\n1,Bob,25\n2,Charles,50\n";

  @Test public void testSimpleCSV() throws IOException {
    // Parse
    List<LinkedHashMap<String,String>> data = SimpleCSV.parse(TEST_CSV);
    assertTrue(data.size() == 3);
    assertTrue(data.get(0).containsKey("ID"));
    assertTrue(data.get(0).containsKey("NAME"));
    assertTrue(data.get(0).containsKey("AGE"));

    // Unparse
    String csv = SimpleCSV.unparse(data);
    assertTrue(csv.equals(TEST_CSV));

    // Valid
    assertTrue(SimpleCSV.isValid(csv));
  }

}
