package org.mitre.synthea.identity;

import com.google.gson.GsonBuilder;

import java.time.LocalDate;
import java.util.List;

public class EntityManager {
  private List<Entity> records;

  public EntityManager() {

  }

  public List<Entity> getRecords() {
    return records;
  }

  public void setRecords(List<Entity> records) {
    this.records = records;
  }

  public static EntityManager fromJSON(String rawJSON) {
    GsonBuilder gson = new GsonBuilder();
    gson.registerTypeAdapter(Entity.class, new EntityDeserializer());
    gson.registerTypeAdapter(LocalDate.class, new LocalDateDeserializer());
    return gson.create().fromJson(rawJSON, EntityManager.class);
  }
}
