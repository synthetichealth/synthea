package org.mitre.synthea.engine;

import com.google.gson.JsonObject;
import org.mitre.synthea.world.agents.Person;

public interface DurationProvider {
  void setUnit(String unit);
  String getUnit();
  void load(JsonObject definition);
  long generate(Person person);
  boolean detect(JsonObject definition);
}
