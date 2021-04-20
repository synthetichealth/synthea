package org.mitre.synthea.world.concepts;

import java.util.List;

import org.mitre.synthea.helpers.SimpleYML;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

public class Names {
  
  private static SimpleYML names = loadNames();
  
  private static SimpleYML loadNames() {
    String filename = "names.yml";
    try {
      String namesData = Utilities.readResource(filename);
      return new SimpleYML(namesData);
    } catch (Exception e) {
      System.err.println("ERROR: unable to load yml: " + filename);
      e.printStackTrace();
      throw new ExceptionInInitializerError(e);
    }
  }
  
  /**
   * Generate a first name appropriate for a given gender and language.
   * @param gender Gender of the name, "M" or "F"
   * @param language Origin language of the name, "english", "spanish"
   * @param person person to generate a name for.
   * @return First name.
   */
  @SuppressWarnings("unchecked")
  public static String fakeFirstName(String gender, String language, Person person) {
    List<String> choices;
    if ("spanish".equalsIgnoreCase(language)) {
      choices = (List<String>) names.get("spanish." + gender);
    } else {
      choices = (List<String>) names.get("english." + gender);
    }
    // pick a random item from the list
    return choices.get(person.randInt(choices.size()));
  }

  /**
   * Generate a surname appropriate for a given language.
   * @param language Origin language of the name, "english", "spanish"
   * @param person person to generate a name for.
   * @return Surname or Family Name.
   */
  @SuppressWarnings("unchecked")
  public static String fakeLastName(String language, Person person) {
    List<String> choices;
    if ("spanish".equalsIgnoreCase(language)) {
      choices = (List<String>) names.get("spanish.family");
    } else {
      choices = (List<String>) names.get("english.family");
    }
    // pick a random item from the list
    return choices.get(person.randInt(choices.size()));
  }

  /**
   * Generate a Street Address.
   * @param includeLine2 Whether or not the address should have a second line,
   *     which can take the form of an apartment, unit, or suite number.
   * @param person person to generate an address for.
   * @return First name.
   */
  @SuppressWarnings("unchecked")
  public static String fakeAddress(boolean includeLine2, Person person) {
    int number = person.randInt(1000) + 100;
    List<String> n = (List<String>)names.get("english.family");
    // for now just use family names as the street name.
    // could expand with a few more but probably not worth it
    String streetName = n.get(person.randInt(n.size()));
    List<String> a = (List<String>)names.get("street.type");
    String streetType = a.get(person.randInt(a.size()));
    
    if (includeLine2) {
      int addtlNum = person.randInt(100);
      List<String> s = (List<String>)names.get("street.secondary");
      String addtlType = s.get(person.randInt(s.size()));
      return number + " " + streetName + " " + streetType + " " + addtlType + " " + addtlNum;
    } else {
      return number + " " + streetName + " " + streetType;
    }
  }

  /**
   * Adds a 1- to 3-digit hashcode to the end of the name.
   * @param name Person's name
   * @return The name with a hash appended, ex "John123" or "Smith22"
   */
  public static String addHash(String name) {
    // note that this value should be deterministic
    // It cannot be a random number. It needs to be a hash value or something deterministic.
    // We do not want John10 and John52 -- we want all the Johns to have the SAME numbers. e.g. All
    // people named John become John52
    // Why? Because we do not know how using systems will index names. Say a user of an system
    // loaded with Synthea data wants to find all the people named John Smith. This will be easier
    // if John Smith always resolves to John52 Smith32 and not [John52 Smith32, John10 Smith22, ...]
    return name + Integer.toString(Math.abs(name.hashCode() % 1000));
  }
}
