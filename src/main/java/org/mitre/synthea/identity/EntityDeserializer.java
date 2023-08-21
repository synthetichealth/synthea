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
import java.util.function.Function;

public class EntityDeserializer implements JsonDeserializer<Entity> {
  @Override
  public Entity deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    Entity entity = new Entity();
    JsonObject entityObject = json.getAsJsonObject();
    
    Function<String, String> getString = (fieldName) -> {
      if (!entityObject.has(fieldName)) return null;
      return entityObject.getAsJsonPrimitive(fieldName).getAsString();
    };
    entity.setIndividualId(getString.apply("individualId"));
    entity.setGender(getString.apply("gender"));
    entity.setSexAtBirth(getString.apply("sexAtBirth"));
    entity.setOmbRaceCategory(getString.apply("ombRaceCategory"));
    entity.setEthnicity(getString.apply("ethnicity"));
    entity.setSocioeconomicLevel(getString.apply("socioeconomicLevel"));
    entity.setHousingStatus(getString.apply("housingStatus"));
    entity.setDateOfBirth(LocalDate.parse(
        getString.apply("dateOfBirth"),
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