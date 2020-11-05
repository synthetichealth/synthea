package org.mitre.synthea.input;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

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

  @Expose(serialize = false, deserialize = true) private transient Map<Integer, FixedRecordGroup> recordGroups;

  @Expose(serialize = false, deserialize = true) private transient int linkId;

  @Expose(serialize = false, deserialize = true) private transient int year;

  public FixedRecordGroupManager() {}

  public void createRecordGroups() {
    year = 0;
    recordGroups = new HashMap<Integer, FixedRecordGroup>();
    // Initialize with the seed records.
    for(FixedRecord seedRecord : seedRecords) {
      this.recordGroups.put(Integer.parseInt(seedRecord.recordId), new FixedRecordGroup(seedRecord));
    }
    // Populate the seeded record groups with variant records.
    for(FixedRecord variantRecord : variantRecords) {
      if (!this.recordGroups.containsKey(Integer.parseInt(variantRecord.seedID))) {
        throw new RuntimeException("ERROR: Variant record seed ID " + variantRecord.seedID + " does not exist in seed records.");
      }
      this.recordGroups.get(Integer.parseInt(variantRecord.seedID)).addVariantRecord(variantRecord);
    }
  }

  public int getPopulationSize() {
    return this.recordGroups.size();
  }

  public FixedRecordGroup getRecordGroup(int index) {
    return this.recordGroups.get(Integer.parseInt(this.seedRecords.get(index).recordId));
  }
}