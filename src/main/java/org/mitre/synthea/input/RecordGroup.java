package org.mitre.synthea.input;

import java.util.List;

/**
 * A grouping of FixedRecords that represents a single individual. FixedRecords provide demographic
 * information and the grouping can be used to capture variation that may happen across different
 * provider locations.
 */
public class RecordGroup {
  public List<FixedRecord> records;
  public int count;
}
