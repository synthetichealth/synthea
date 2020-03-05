package org.mitre.synthea.export;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.h2.util.StringUtils;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;

public class VaSDoHReport {

  private static final String CODE = "Code";
  private static final String TYPE = "Type";
  private static final String VALUE = "Value";
  private static final String EXPECTED_THIS = "Expected Prevalence";
  private static final String EXPECTED_IDEATION = "Expected Prevalence of Suicidal Ideation";
  private static final String EXPECTED_ATTEMPT = "Expected Prevalence of Suicide Attempt";
  private static final String POPULATION = "Population";
  private static final String COUNT = "Count";
  private static final String ACTUAL_THIS = "Actual Prevalence";
  private static final String ACTUAL_IDEATION = "Actual Ideation Prevalence";
  private static final String ACTUAL_ATTEMPT = "Actual Attempt Prevalence";
  private static final String DELTA_THIS = "Prevalence Delta";
  private static final String DELTA_IDEATION = "Ideation Delta";
  private static final String DELTA_ATTEMPT = "Attempt Delta";
  
  private static final boolean isSuicidalIdeation(Person p) {
    // TODO
    return false;
  }
  
  private static final boolean isSuicideAttempt(Person p) {
    // if either one of these is non-null, they attempted suicide
    return p.attributes.get("suicide") != null || p.attributes.get("suicide_attempt") != null;
  }
  
  private static List<LinkedHashMap<String, String>> templateData;
  private static List<Row> dataRows;
  private static AtomicInteger population;
  
  private static class Row {
    Predicate<Person> matcher;
    AtomicInteger count = new AtomicInteger(0);
    AtomicInteger ideationCount = new AtomicInteger(0);
    AtomicInteger attemptCount = new AtomicInteger(0);
  }
  
  public static void init() {
    // read the template file, create matchers for the things we care about
    try {
        String csvData = Utilities.readResource("va_sdoh_prevalence_template.csv");
        if (csvData.startsWith("\uFEFF")) {
          csvData = csvData.substring(1); // Removes BOM.
        }
        templateData = SimpleCSV.parse(csvData);
    
        dataRows = new ArrayList<>();
        population = new AtomicInteger(0);
        
        for (LinkedHashMap<String, String> line : templateData) {
          String code = line.get(CODE);
          String type = line.get(TYPE);
          String value = line.get(VALUE);
          
          if (code == null || type == null) continue;
          
          Row row = new Row();
          row.matcher = buildMatcher(code, type, value);
          dataRows.add(row);
        }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  private static Predicate<Person> buildMatcher(String code, String type, String valueString) {
    
    Object value = stringToObject(valueString);
    
    
    switch (type) {
      case "Attribute":
        return (person) -> {
          Object attrValue = person.attributes.get(code);
          return attrValue != null && attrValue.equals(value);
        };
        
      case "Condition":
        // note: value ignored here
        return (person) -> person.record.present.containsKey(code);
        
      case "Observation":
        return (person) -> {
          Observation o = person.record.getLatestObservation(code);
          if (o == null) return false;
          
          if (value.equals(o.value)) return true;
          
          if (o.value instanceof Number) {
            Number n = (Number) o.value;
            return n.toString().equals(value); // potentially flaky, but hopefully we aren't actually testing any Doubles 
          }
          
          if (o.value instanceof Code) {
            Code c = (Code) o.value;
            return c.code.equals(value);
          }
          
          return o != null && value.equals(o.value);
        };
        
      default:
        throw new IllegalArgumentException("Illegal Type: " + type + " -- Expected 'Attribute', 'Condition', or 'Observation'");
    }
  }
  
  private static Object stringToObject(String valueString) {
    if (valueString == null) return null;
    
    if (valueString.equalsIgnoreCase("true")) return Boolean.TRUE;
    if (valueString.equalsIgnoreCase("false")) return Boolean.FALSE;
    
    if (StringUtils.isNumber(valueString)) {
      if (valueString.contains(".")) {
        return Double.valueOf(valueString);
      } else {
        return Integer.valueOf(valueString);
      }
    }
    
    return valueString;
  }

  public static void addPerson(Person p) {
    // run the given matchers on this person to count things
    population.incrementAndGet();
    
    boolean hasIdeation = isSuicidalIdeation(p);
    boolean hasAttempt = isSuicideAttempt(p);
    
    for (Row row : dataRows) {
      if (row.matcher.test(p)) {
        row.count.incrementAndGet();
        
        if (hasIdeation) row.ideationCount.incrementAndGet();
        if (hasAttempt) row.attemptCount.incrementAndGet();
      }
    }
    
  }
  
  public static void generateReport() {
    // calculate and write out counts
   
    String popString = population.toString();
    int popInt = population.get();
    
    for (int i = 0 ; i < dataRows.size() ; i++) {
      LinkedHashMap<String, String> line = templateData.get(i);
      Row dataRow = dataRows.get(i);
      
      int count = dataRow.count.get();
      
      line.put(POPULATION, popString);
      line.put(COUNT, Integer.toString(count));
      
      double prevalence = ((double) count) / popInt * 100.0;
      
      line.put(ACTUAL_THIS, Double.toString(prevalence));
      
      if (count > 0) {
        int ideationCount = dataRow.ideationCount.get();
        int attemptCount = dataRow.attemptCount.get();
        
        double ideationPrev = ((double) ideationCount) / count * 100.0;
        double attemptPrev = ((double) attemptCount) / count * 100.0;
        
        line.put(ACTUAL_IDEATION, Double.toString(ideationPrev));
        line.put(ACTUAL_ATTEMPT, Double.toString(attemptPrev));
        
        
        String expectedStr = line.get(EXPECTED_THIS);
        if (expectedStr != null && !expectedStr.trim().isEmpty()) {
          double expected = Double.parseDouble(expectedStr);
          double delta = prevalence - expected;
          line.put(DELTA_THIS, Double.toString(delta));
        }
        
        String expectedIdeationStr = line.get(EXPECTED_IDEATION);
        if (expectedIdeationStr != null && !expectedIdeationStr.trim().isEmpty()) {
          double expectedIdeation = Double.parseDouble(expectedIdeationStr);
          double delta = ideationPrev - expectedIdeation;
          line.put(DELTA_IDEATION, Double.toString(delta));
        }
        
        String expectedAttemptStr = line.get(EXPECTED_ATTEMPT);
        if (expectedAttemptStr != null && !expectedAttemptStr.trim().isEmpty()) {
          double expectedAttempt = Double.parseDouble(expectedAttemptStr);
          double delta = attemptPrev - expectedAttempt;
          line.put(DELTA_ATTEMPT, Double.toString(delta));
        } 
      }
    }
    
    try {
      String newCsvData = SimpleCSV.unparse(templateData);
  
      File outDirectory = Exporter.getOutputFolder("prevalence", null);
  
      Path outFilePath = outDirectory.toPath()
          .resolve("va_sdoh_prev_data" + System.currentTimeMillis() + ".csv");
  
      Files.write(outFilePath, Collections.singleton(newCsvData), StandardOpenOption.CREATE_NEW);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
}
