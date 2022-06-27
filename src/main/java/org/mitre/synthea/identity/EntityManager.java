package org.mitre.synthea.identity;

import com.google.gson.GsonBuilder;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton class that manages all Entities in a simulation. This class will typically be
 * instantiated using the fromJSON static method.
 */
public class EntityManager {
  private List<Entity> records;
  private transient Map<String, Entity> entityLookup;

  public List<Entity> getRecords() {
    return records;
  }

  public void setRecords(List<Entity> records) {
    this.records = records;
  }

  /**
   * Find a particular Entity by Id.
   * @param entityId The Id to look for
   * @return The found Entity, or null if one does not exist with the given Id.
   */
  public Entity findEntity(String entityId) {
    if (entityLookup == null) {
      buildLookup();
    }
    return entityLookup.get(entityId);
  }

  public int getPopulationSize() {
    return records.size();
  }

  private void buildLookup() {
    entityLookup = new HashMap<>();
    records.forEach(entity -> entityLookup.put(entity.getIndividualId(), entity));
  }

  /**
   * Ensures that all Entities loaded by this EntityManager are valid.
   * @return true if all Entities are valid
   */
  public boolean validate() {
    return records.stream().allMatch(entity -> entity.validSeedPeriods());
  }

  /**
   * Creates a new EntityManager by reading in the JSON containing all of the information on
   * entities, seeds and variants.
   * @param rawJSON the actual JSON
   * @return an Entity Manager
   */
  public static EntityManager fromJSON(String rawJSON) {
    GsonBuilder gson = new GsonBuilder();
    gson.registerTypeAdapter(Entity.class, new EntityDeserializer());
    gson.registerTypeAdapter(LocalDate.class, new LocalDateDeserializer());
    return gson.create().fromJson(rawJSON, EntityManager.class);
  }
}