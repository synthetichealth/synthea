package org.mitre.synthea.engine.lookup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.Range;
import org.junit.Test;
import org.mitre.synthea.engine.lookup.LookupTableIndex.Implementation;
import org.mitre.synthea.engine.lookup.LookupTableIndex.Row;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;

/** Compares range indexes against the scan reference using the actual module lookup tables. */
public class LookupTableCorpusTest {
  /**
   * Every module-referenced table agrees at its rule corners and immediately outside each edge.
   * Probabilities are irrelevant here: row numbers expose lost, duplicated or reordered matches.
   */
  @Test
  public void indexesAgreeAtBundledRuleBoundaries() throws Exception {
    Map<String, Integer> transitionCounts = referencedTables();
    int tables = 0;
    int queries = 0;
    int ageOnlyTables = 0;
    int timeOnlyTables = 0;
    int ageAndTimeTables = 0;
    int exactOnlyTables = 0;

    for (Map.Entry<String, Integer> table : transitionCounts.entrySet()) {
      String resource = "modules/lookup_tables/" + table.getKey();
      List<? extends Map<String, String>> csv =
          SimpleCSV.parse(Utilities.readResource(resource, true, true));
      assertTrue("Empty referenced lookup table: " + resource, !csv.isEmpty());
      List<String> columns = new ArrayList<>(csv.get(0).keySet());
      List<String> attributes = columns.subList(0, columns.size() - table.getValue());
      boolean hasAge = attributes.contains("age");
      boolean hasTime = attributes.contains("time");
      if (hasAge && hasTime) {
        ageAndTimeTables++;
      } else if (hasAge) {
        ageOnlyTables++;
      } else if (hasTime) {
        timeOnlyTables++;
      } else {
        exactOnlyTables++;
      }

      List<Row<Integer>> rules = new ArrayList<>();
      List<Query> probes = new ArrayList<>();
      for (Map<String, String> row : csv) {
        List<String> exact = new ArrayList<>();
        Range<Integer> age = null;
        Range<Long> time = null;
        for (String attribute : attributes) {
          String value = row.get(attribute);
          if (attribute.equals("age")) {
            String[] bounds = value.split("-");
            age = Range.between(Integer.parseInt(bounds[0]), Integer.parseInt(bounds[1]));
          } else if (attribute.equals("time")) {
            time = Utilities.parseDateRange(value);
          } else {
            exact.add(value);
          }
        }
        int rowId = rules.size();
        rules.add(new Row<>(exact, age, time, rowId));
        Integer lowAge = age == null ? null : age.getMinimum();
        Integer highAge = age == null ? null : age.getMaximum();
        long lowTime = time == null ? 0L : time.getMinimum();
        long highTime = time == null ? 0L : time.getMaximum();
        probes.add(new Query(exact, lowAge, lowTime, rowId));
        probes.add(new Query(exact, highAge, highTime, rowId));
        probes.add(new Query(exact, lowAge, highTime, rowId));
        probes.add(new Query(exact, highAge, lowTime, rowId));
        if (lowAge != null && lowAge > Integer.MIN_VALUE) {
          probes.add(new Query(exact, lowAge - 1, lowTime, null));
        }
        if (highAge != null && highAge < Integer.MAX_VALUE) {
          probes.add(new Query(exact, highAge + 1, highTime, null));
        }
        if (time != null && lowTime > Long.MIN_VALUE) {
          probes.add(new Query(exact, lowAge, lowTime - 1, null));
        }
        if (time != null && highTime < Long.MAX_VALUE) {
          probes.add(new Query(exact, highAge, highTime + 1, null));
        }
      }

      Map<Implementation, LookupTableIndex<Integer>> indexes = new EnumMap<>(Implementation.class);
      for (Implementation implementation : Implementation.values()) {
        indexes.put(implementation, LookupTableIndex.create(implementation, rules));
      }
      for (Query probe : probes) {
        compare(indexes, resource, probe);
      }
      queries += probes.size();
      tables++;
    }
    assertTrue("Expected the bundled transition corpus", tables >= 60);
    assertTrue("Expected substantial boundary coverage", queries > 10000);
    assertTrue("Age-only tables were not exercised", ageOnlyTables > 0);
    assertTrue("Time-only tables were not exercised", timeOnlyTables > 0);
    assertTrue("Combined age/time tables were not exercised", ageAndTimeTables > 0);
    assertTrue("Tables without range columns were not exercised", exactOnlyTables > 0);
  }

  private void compare(Map<Implementation, LookupTableIndex<Integer>> indexes, String resource,
      Query probe) {
    List<Integer> expected = indexes.get(Implementation.SCAN)
        .findAll(probe.attributes, probe.age, probe.time);
    if (probe.matchingRow != null) {
      assertTrue("Rule does not match its own corner: " + resource + " row " + probe.matchingRow,
          expected.contains(probe.matchingRow));
    }
    for (Implementation implementation : Implementation.values()) {
      if (implementation != Implementation.SCAN) {
        assertEquals(resource + " " + implementation + " " + probe.attributes
            + " age=" + probe.age + " time=" + probe.time,
            expected, indexes.get(implementation).findAll(probe.attributes, probe.age, probe.time));
      }
    }
  }

  private static final class Query {
    private final List<String> attributes;
    private final Integer age;
    private final long time;
    private final Integer matchingRow;

    Query(List<String> attributes, Integer age, long time, Integer matchingRow) {
      this.attributes = attributes;
      this.age = age;
      this.time = time;
      this.matchingRow = matchingRow;
    }
  }

  private Map<String, Integer> referencedTables() throws Exception {
    Map<String, Integer> tables = new TreeMap<>();
    try (Stream<Path> files = Files.walk(Paths.get("src/main/resources/modules"))) {
      for (Path file : files.filter(path -> path.toString().endsWith(".json"))
          .collect(Collectors.toList())) {
        collectReferences(JsonParser.parseString(Files.readString(file)), tables);
      }
    }
    return tables;
  }

  private void collectReferences(JsonElement element, Map<String, Integer> tables) {
    if (element.isJsonArray()) {
      for (JsonElement child : element.getAsJsonArray()) {
        collectReferences(child, tables);
      }
    } else if (element.isJsonObject()) {
      JsonObject object = element.getAsJsonObject();
      if (object.has("lookup_table_transition")) {
        List<JsonElement> options = new ArrayList<>();
        object.getAsJsonArray("lookup_table_transition").forEach(options::add);
        assertTrue("Module has an empty lookup transition", !options.isEmpty());
        String name = options.get(0).getAsJsonObject().get("lookup_table_name").getAsString();
        Integer previous = tables.putIfAbsent(name, options.size());
        if (previous != null) {
          assertEquals("Inconsistent transition-column count for " + name,
              previous.intValue(), options.size());
        }
      }
      for (Map.Entry<String, JsonElement> field : object.entrySet()) {
        collectReferences(field.getValue(), tables);
      }
    }
  }
}
