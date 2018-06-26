package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.Test;
import org.mitre.synthea.datastore.DataStore;
import org.mitre.synthea.helpers.SimpleCSV;

public class CustomSqlReportTest {

  @Test
  public void testTableToCsv() throws Exception {
    DataStore testStore = new DataStore(false); // false == use in-memory
    Connection connection = testStore.getConnection();

    // using a query that doesn't require any data in the DB. 
    // H2 has a dummy table "dual" with contains 1 column "X" with 1 value "1"
    // so getting the count and sum of this column both == 1
    String query = "SELECT COUNT(X) NUM, SUM(X) TOTAL FROM DUAL";
    String result = CustomSqlReport.queryToCsv(connection, query);
    
    assertTrue(result.contains("NUM"));
    assertTrue(result.contains("TOTAL"));
    
    // reparse it to a map so we can check it more "independently"
    List<LinkedHashMap<String,String>> csvLines = SimpleCSV.parse(result);
    
    assertEquals(1, csvLines.size());
    
    LinkedHashMap<String,String> resultMap = csvLines.get(0);
    
    assertEquals(2, resultMap.size());
    assertEquals("1", resultMap.get("NUM"));
    assertEquals("1", resultMap.get("TOTAL"));
    
    // try a query that will return no results
    query = "SELECT 1 FROM DUAL WHERE 42 = 54";
    result = CustomSqlReport.queryToCsv(connection, query);
    
    assertEquals(CustomSqlReport.NO_RESULTS, result);
  }
}
