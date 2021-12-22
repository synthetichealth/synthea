package org.mitre.synthea.identity;

import com.google.gson.GsonBuilder;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityManager {
  private List<Entity> records;
  private transient Map<String, Entity> entityLookup;

  public EntityManager() {

  }

  public List<Entity> getRecords() {
    return records;
  }

  public void setRecords(List<Entity> records) {
    this.records = records;
  }

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
    records.forEach(entity -> { entityLookup.put(entity.getIndividualId(), entity); });
  }

  public static EntityManager fromJSON(String rawJSON) {
    GsonBuilder gson = new GsonBuilder();
    gson.registerTypeAdapter(Entity.class, new EntityDeserializer());
    gson.registerTypeAdapter(LocalDate.class, new LocalDateDeserializer());
    return gson.create().fromJson(rawJSON, EntityManager.class);
  }
}
