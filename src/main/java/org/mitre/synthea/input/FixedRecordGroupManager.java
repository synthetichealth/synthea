package org.mitre.synthea.input;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A grouping of FixedRecords that represents a single individual. FixedRecords provide demographic
 * information and the grouping can be used to capture variation that may happen across different
 * provider locations.
 */
public class FixedRecordGroupManager {
  
  @SerializedName(value = "seed_records")
  public List<FixedRecord> seedRecords;

  @SerializedName(value = "variant_records")
  public List<FixedRecord> variantRecords;

  @Expose(serialize = false, deserialize = true)
  private transient Map<Integer, FixedRecordGroup> recordGroups;

  public FixedRecordGroupManager() {}

  /**
   * Creates the record groups based on the imported records.
   */
  public void createRecordGroups() {
    this.recordGroups = new HashMap<Integer, FixedRecordGroup>();
    // Initialize with the seed records.
    for (FixedRecord seedRecord : seedRecords) {
      this.recordGroups.put(Integer.parseInt(seedRecord.recordId),
          new FixedRecordGroup(seedRecord));
    }
    // Populate the seeded record groups with variant records.
    for (FixedRecord variantRecord : variantRecords) {
      if (!this.recordGroups.containsKey(Integer.parseInt(variantRecord.seedID))) {
        throw new RuntimeException("ERROR: Variant record seed ID " + variantRecord.seedID
            + " does not exist in seed records.");
      }
      this.recordGroups.get(Integer.parseInt(variantRecord.seedID))
          .addVariantRecord(variantRecord);
    }
    // Set the date ranges of the fixed records.
    for (FixedRecordGroup recordGroup : this.recordGroups.values()) {
      recordGroup.setVariantRecordYearRanges();
    }
  }

  public int getPopulationSize() {
    return this.recordGroups.size();
  }

  public FixedRecordGroup getRecordGroup(int index) {
    return this.recordGroups.get(Integer.parseInt(this.seedRecords.get(index).recordId));
  }
}