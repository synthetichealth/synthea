package org.mitre.synthea.export;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.mitre.synthea.engine.State;
import org.mitre.synthea.world.agents.Person;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

public class JSONExporter {
  public static String export(Person person) {
    Gson gson = new GsonBuilder()
        .excludeFieldsWithModifiers(Modifier.STATIC, Modifier.TRANSIENT, Modifier.VOLATILE)
        .addSerializationExclusionStrategy(new SyntheaExclusionStrategy())
        .registerTypeHierarchyAdapter(State.class, new StateSerializer())
        .create();
    return gson.toJson(person);
  }

  public static class StateSerializer implements JsonSerializer<State> {

    @Override
    public JsonElement serialize(State src, Type typeOfSrc, JsonSerializationContext context) {
      JsonObject stateOut = new JsonObject();
      stateOut.add("state_name", new JsonPrimitive(src.name));
      stateOut.add("entered", new JsonPrimitive(src.entered));
      if (src.exited != null) {
        stateOut.add("exited", new JsonPrimitive(src.exited));
      }
      return stateOut;
    }
  }

  public static class SyntheaExclusionStrategy implements ExclusionStrategy {
    @Override
    public boolean shouldSkipField(FieldAttributes f) {
      return f.getAnnotation(JSONSkip.class) != null;
    }

    @Override
    public boolean shouldSkipClass(Class<?> clazz) {
      return org.mitre.synthea.engine.Module.class.isAssignableFrom(clazz);
    }
  }
}
