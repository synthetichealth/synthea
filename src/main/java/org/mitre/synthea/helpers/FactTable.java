package org.mitre.synthea.helpers;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The FactTable helper class aids in the export of database-style
 * Fact Tables. If you have a "table" where each row is a fact or
 * lookup table where a value should be referenced by an ID, you
 * can add these keys and facts to this table and get back the ID.
 */
public class FactTable {
  /**
   * System-dependent string for a line break. (\n on Mac, *nix, \r\n on Windows)
   */
  private static final String NEWLINE = System.lineSeparator();
  /** Table column headers. Comma-separated. */
  private String header;
  /** This is the ID sequence generator. */
  private AtomicInteger id;
  /** Lookup the ID for a key. */
  private Map<String,Integer> keys;
  /** Lookup the fact by ID. */
  private Map<Integer,String> facts;
  
  /**
   * Create a FactTable with an ID that starts at 1
   * and increments with each new key/fact.
   */
  public FactTable() {
    id = new AtomicInteger(1);
    keys = new HashMap<String,Integer>();
    facts = new HashMap<Integer,String>();
  }
  
  /**
   * Set the column headers of the fact table.
   * @param header Column headers of the fact table.
   */
  public void setHeader(String header) {
    this.header = header;
  }
  
  /**
   * Get the ID for a fact by a key.
   * @param key The key for a fact. For example, 'M' or 'F'.
   * @return The ID for the fact. For example, 1 or 2.
   */
  public int getFactId(String key) {
    return keys.get(key);
  }
  
  /**
   * Adds a new key/fact combination to the table
   * and returns the ID. If the key already exists,
   * the appropriate ID is returned without modifying
   * the table.
   * 
   * @param key The key for a fact. For example, 'M' or 'F'.
   * @param fact The fact. For example, 'Male' or 'Female'.
   * @return The ID for the fact. For example, 1 or 2.
   */
  public int addFact(String key, String fact) {
    if (keys.containsKey(key)) {
      return keys.get(key);
    }
    
    int next = id.getAndIncrement();
    keys.put(key, next);
    facts.put(next, fact);
    return next;
  }
  
  /**
   * Write the contents of the FactTable to a file.
   * @param writer The open Writer to use to record the FactTable.
   * @throws IOException On errors.
   */
  public void write(Writer writer) throws IOException {
    writer.write(header);
    writer.write(NEWLINE);
    for (Integer key : facts.keySet()) {
      writer.write(key.toString());
      writer.write(',');
      String fact = facts.get(key);
      if (fact != null) {
        writer.write(facts.get(key));        
      }
      writer.write(NEWLINE);
    }
    writer.flush();
  }
}
