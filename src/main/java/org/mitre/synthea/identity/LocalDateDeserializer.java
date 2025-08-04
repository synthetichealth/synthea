package org.mitre.synthea.identity;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * A custom deserializer for LocalDate objects using Gson.
 */
public class LocalDateDeserializer implements JsonDeserializer<LocalDate> {
  @Override
  public LocalDate deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    return LocalDate.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE);
  }
}
