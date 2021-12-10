package org.mitre.synthea.identity;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class EntityDeserializer implements JsonDeserializer<Entity> {
  @Override
  public Entity deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    Entity entity = new Entity();
    JsonObject entityObject = json.getAsJsonObject();
    entity.setIndividualId(entityObject.getAsJsonPrimitive("individualId").getAsString());
    entity.setGender(entityObject.getAsJsonPrimitive("gender").getAsString());
    entity.setDateOfBirth(LocalDate.parse(entityObject.getAsJsonPrimitive("dateOfBirth").getAsString(),
        DateTimeFormatter.ISO_LOCAL_DATE));
    List<Seed> seeds = new ArrayList<>();
    entityObject.getAsJsonArray("seeds").forEach(seedElement -> {
      Seed seed = context.deserialize(seedElement, Seed.class);
      seed.setEntity(entity);
      seed.getVariants().forEach(variant -> variant.setSeed(seed));
      seeds.add(seed);
    });
    entity.setSeeds(seeds);
    return entity;
  }
}
