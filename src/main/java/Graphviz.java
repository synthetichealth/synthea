import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import guru.nidi.graphviz.attribute.Color;
import guru.nidi.graphviz.attribute.Records;
import guru.nidi.graphviz.attribute.Style;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Rasterizer;
import guru.nidi.graphviz.model.Factory;
import guru.nidi.graphviz.model.Graph;
import guru.nidi.graphviz.model.Label;
import guru.nidi.graphviz.model.Link;
import guru.nidi.graphviz.model.Node;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import org.mitre.synthea.export.Exporter;
import org.mitre.synthea.helpers.Utilities;


public class Graphviz {
  private static final String NEWLINE = "\\l";

  /**
   * Generate the Graphviz-like graphs of the disease modules.
   * @param args Optional path of modules to render. If not provided,
   *     the default modules will be loaded using the ClassLoader.
   * @throws URISyntaxException on failure to load modules.
   */
  public static void main(String[] args) throws URISyntaxException {
    File folder = Exporter.getOutputFolder("graphviz", null);

    Path inputPath = null;
    if (args != null && args.length > 0) {
      File file = new File(args[0]);
      inputPath = file.toPath();
    } else {
      URL modulesFolder = ClassLoader.getSystemClassLoader().getResource("modules");
      inputPath = Paths.get(modulesFolder.toURI());
    }

    System.out.println("Rendering graphs to `" + folder.getAbsolutePath() + "`...");

    long start = System.currentTimeMillis();
    generateJsonModuleGraphs(inputPath, folder);

    System.out.println("Completed in " + (System.currentTimeMillis() - start) + " ms.");
  }

  private static void generateJsonModuleGraphs(Path inputPath, File outputFolder) {
    // adapted from Module.loadModules()
    try {
      Utilities.walkAllModules(inputPath, t -> {
            try {
              JsonObject module = loadFile(t, inputPath);
              String relativePath = relativePath(t, inputPath);
              generateJsonModuleGraph(module, outputFolder, relativePath);
            } catch (IOException e) {
              e.printStackTrace();
            }
          });
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static JsonObject loadFile(Path path, Path modulesFolder) throws IOException {
    System.out.format("Loading %s\n", path.toString());
    FileReader fileReader = new FileReader(path.toString());
    JsonReader reader = new JsonReader(fileReader);
    JsonObject object = new JsonParser().parse(reader).getAsJsonObject();
    fileReader.close();
    reader.close();
    return object;
  }

  private static String relativePath(Path filePath, Path modulesFolder) {
    String folderString = Matcher.quoteReplacement(modulesFolder.toString() + File.separator);
    return filePath.toString().replaceFirst(folderString, "").replaceFirst(".json", "")
        .replace("\\", "/");
  }

  private static void generateJsonModuleGraph(JsonObject module, File outputFolder,
      String relativePath) throws IOException {
    // TODO -- a lot of this uses immutable objects. refactor to use mutable ones
    Graph g = Factory.graph().directed();

    JsonObject states = module.get("states").getAsJsonObject();

    Map<String, Node> nodeMap = new HashMap<>();

    for (Map.Entry<String, JsonElement> s : states.entrySet()) {
      // first pass -- all nodes
      String name = s.getKey();

      Node node = Factory.node(name).with(Style.ROUNDED);

      JsonObject state = s.getValue().getAsJsonObject();
      String type = state.get("type").getAsString();

      if (type.equals("Initial") || type.equals("Terminal")) {
        node = node.with(Color.BLACK.fill()).with(Style.ROUNDED.and(Style.FILLED))
            .with(Color.WHITE.font());
      }

      String details = getStateDescription(state);
      if (details.isEmpty()) {
        if (type.equals(name)) {
          node = node.with("label", name);
        } else {
          node = node.with(Records.mLabel(Records.turn(name, type)));
        }
      } else {
        node = node.with(Records.mLabel(Records.turn(name, Records.turn(type, details))));
      }
      nodeMap.put(name, node);
    }

    for (Map.Entry<String, JsonElement> s : states.entrySet()) {
      // second pass -- transitions
      String name = s.getKey();
      Node node = nodeMap.get(name);

      JsonObject state = s.getValue().getAsJsonObject();
      String type = state.get("type").getAsString();
      if (type.equals("Terminal")) {
        continue;
      }

      List<Link> links = new ArrayList<>();

      if (state.has("direct_transition")) {
        String targetName = state.get("direct_transition").getAsString();
        Node target = nodeMap.get(targetName);
        if (target == null) {
          throw new RuntimeException(
              relativePath + " " + name + " transitioning to unknown state: " + targetName);
        }
        Link direct = Factory.to(target);
        links.add(direct);
      } else if (state.has("conditional_transition")) {
        JsonArray cts = state.get("conditional_transition").getAsJsonArray();
        for (int i = 0; i < cts.size(); i++) {
          JsonObject ct = cts.get(i).getAsJsonObject();
          String cond = ct.has("condition") ? logicDetails(ct.get("condition").getAsJsonObject())
              : "else";
          String targetName = ct.get("transition").getAsString();
          Node target = nodeMap.get(targetName);
          if (target == null) {
            throw new RuntimeException(
                relativePath + " " + name + " transitioning to unknown state: " + targetName);
          }
          Link link = Factory.to(target).with(Label.of(Integer.toString(i + 1) + ". " + cond));
          links.add(link);
        }
      } else if (state.has("distributed_transition")) {
        JsonArray distributions = state.get("distributed_transition").getAsJsonArray();
        distributions.forEach(d -> {
          JsonObject dist = d.getAsJsonObject();
          String label;
          JsonElement dx = dist.get("distribution");
          if (dx.isJsonObject()) {
            // "named attribute transition"
            JsonObject distribution = dx.getAsJsonObject();
            label = "p(" + distribution.get("attribute").getAsString() + ")";
            if (distribution.has("default")) {
              double pct = distribution.get("default").getAsDouble() * 100.0;
              label = label + ", default " + pct + "%";
            }
          } else {
            double pct = dx.getAsDouble() * 100.0;
            label = pct + "%";
          }
          String destination = dist.get("transition").getAsString();

          Node target = nodeMap.get(destination);
          if (target == null) {
            throw new RuntimeException(
                relativePath + " " + name + " transitioning to unknown state: " + destination);
          }
          Link link = Factory.to(target).with(Label.of(label));
          links.add(link);
        });
      } else if (state.has("complex_transition")) {
        Map<String, List<String>> transitions = new HashMap<>();

        JsonArray cts = state.get("complex_transition").getAsJsonArray();

        cts.forEach(c -> {
          JsonObject ct = c.getAsJsonObject();
          String cond = ct.has("condition") ? logicDetails(ct.get("condition").getAsJsonObject())
              : "else";

          if (ct.has("transition")) {
            String destination = ct.get("transition").getAsString();

            List<String> ts = transitions.get(destination);
            if (ts == null) {
              ts = new LinkedList<>();
              transitions.put(destination, ts);
            }

            ts.add(cond);

          } else if (ct.has("distributions")) {
            JsonArray distributions = ct.get("distributions").getAsJsonArray();

            distributions.forEach(d -> {
              JsonObject dist = d.getAsJsonObject();
              String label;

              JsonElement dx = dist.get("distribution");

              if (dx.isJsonObject()) {
                // "named attribute transition"
                JsonObject distribution = dx.getAsJsonObject();
                label = "p(" + distribution.get("attribute").getAsString() + ")";
                if (distribution.has("default")) {
                  double pct = distribution.get("default").getAsDouble() * 100.0;
                  label = label + ", default " + pct + "%";
                }
              } else {
                double pct = dx.getAsDouble() * 100.0;
                label = pct + "%";
              }

              String destination = dist.get("transition").getAsString();
              List<String> ts = transitions.get(destination);
              if (ts == null) {
                ts = new LinkedList<>();
                transitions.put(destination, ts);
              }
              ts.add(cond + ": " + label);
            });
          }
        });

        transitions.forEach((targetName, labels) -> {
          String label = String.join(",\n", labels);
          Node target = nodeMap.get(targetName);
          if (target == null) {
            throw new RuntimeException(
                relativePath + " " + name + " transitioning to unknown state: " + targetName);
          }
          Link link = Factory.to(target).with(Label.of(label));
          links.add(link);
        });
      }
      g = g.with(node.link(links.toArray(new Link[0])));
    }

    File outputFile = outputFolder.toPath().resolve(relativePath + ".png").toFile();
    outputFile.mkdirs();
    guru.nidi.graphviz.engine.Graphviz.fromGraph(g).rasterizer(Rasterizer.BATIK)
        .render(Format.PNG).toFile(outputFile);
  }

  private static String getStateDescription(JsonObject state) {
    final StringBuilder details = new StringBuilder();

    String type = state.get("type").getAsString();

    switch (type) {
      case "Guard":
        details.append("Allow if " + logicDetails(state.get("allow").getAsJsonObject()));
        break;
      case "Delay":
      case "Death":
        if (state.has("range")) {
          JsonObject r = state.get("range").getAsJsonObject();
          String low = r.get("low").getAsString();
          String high = r.get("high").getAsString();
          String unit = r.get("unit").getAsString();
          details.append(low).append(" - ").append(high).append(" ").append(unit);
        } else if (state.has("exact")) {
          JsonObject e = state.get("exact").getAsJsonObject();
          String quantity = e.get("quantity").getAsString();
          String unit = e.get("unit").getAsString();
          details.append(quantity).append(" ").append(unit);
        }
        break;
      case "Encounter":
        if (state.has("wellness")) {
          details.append("Wait for regularly scheduled wellness encounter");
        }
        break;
      case "EncounterEnd":
        details.append("End the current encounter");
        if (state.has("discharge_disposition")) {
          JsonObject coding = state.get("discharge_disposition").getAsJsonObject();
          String code = coding.get("code").getAsString();
          String display = coding.get("display").getAsString();
          details.append("\\lDischarge Disposition: [").append(code).append("] ").append(display);
        }
        break;
      case "SetAttribute":
        String v = state.has("value") ? state.get("value").getAsString() : null;
        details.append("Set ").append(state.get("attribute").getAsString()).append(" = ").append(v);
        break;
      case "Symptom":
        String s = state.get("symptom").getAsString();

        if (state.has("range")) {
          JsonObject r = state.get("range").getAsJsonObject();
          String low = r.get("low").getAsString();
          String high = r.get("high").getAsString();
          details.append(s).append(": ").append(low).append(" - ").append(high);
        } else if (state.has("exact")) {
          JsonObject e = state.get("exact").getAsJsonObject();
          String quantity = e.get("quantity").getAsString();
          details.append(s).append(": ").append(quantity);
        }
        if (state.has("probability")) {
          double pct = state.get("probability").getAsDouble() * 100.0;
          String label = pct + "%";
          details.append(" (").append(label).append(")");
        }
        break;
      case "Observation":
        String unit = "";
        if (state.has("unit")) {
          unit = "in " + unit.replace('{', '(').replace('}', ')');
          // replace curly braces with parens, braces can cause issues
        }

        if (state.has("vital_sign")) {
          details.append("Record value from Vital Sign ")
              .append(state.get("vital_sign").getAsString()).append(" ").append(unit)
              .append(NEWLINE);
        } else if (state.has("attribute")) {
          details.append("Record value from Attribute ")
              .append(state.get("attribute").getAsString()).append(" ").append(unit)
              .append(NEWLINE);
        }
        break;
      case "Counter":
        String action = state.get("action").getAsString();
        String attribute = state.get("attribute").getAsString();
        details.append(action).append(" value of attribute ").append(attribute).append(" by 1");
        break;
      case "VitalSign":
        String vs = state.get("vital_sign").getAsString();
        unit = state.get("unit").getAsString();

        if (state.has("range")) {
          JsonObject r = state.get("range").getAsJsonObject();
          String low = r.get("low").getAsString();
          String high = r.get("high").getAsString();
          details.append("Set ").append(vs).append(": ").append(low).append(" - ").append(high)
              .append(" ").append(unit);
        } else if (state.has("exact")) {
          JsonObject e = state.get("exact").getAsJsonObject();
          String quantity = e.get("quantity").getAsString();
          details.append("Set ").append(vs).append(": ").append(quantity).append(" ").append(unit);
        }

        break;
      case "CallSubmodule":
        details.append("Call submodule ").append(state.get("submodule").getAsString());
        break;
      case "MultiObservation":
      case "DiagnosticReport":
        JsonArray observations = state.get("observations").getAsJsonArray();

        for (int i = 0; i < observations.size(); i++) {
          JsonObject obs = observations.get(i).getAsJsonObject();

          // force the sub-observations to Observations so we can re-use this description logic
          obs.addProperty("type", "Observation");

          String desc = getStateDescription(obs).replace(NEWLINE, NEWLINE + "   ");
          details.append(i + 1).append(". ").append(desc).append(NEWLINE);
        }
        details.append(NEWLINE); // extra space between sub-obs and details of this state
        break;
      case "ImagingStudy":
        JsonArray series = state.get("series").getAsJsonArray();

        JsonObject modality = series.get(0).getAsJsonObject().get("modality").getAsJsonObject();
        String modalityCode = modality.get("code").getAsString();
        String modalityDisplay = modality.get("display").getAsString();
        details.append("DICOM-DCM[").append(modalityCode).append("]: ").append(modalityDisplay)
            .append(NEWLINE);

        JsonObject bodySite = series.get(0).getAsJsonObject().get("body_site").getAsJsonObject();
        String bodySiteCode = bodySite.get("code").getAsString();
        String bodySiteDisplay = bodySite.get("display").getAsString();

        details.append("SNOMED-CT[").append(bodySiteCode).append("] Body Site: ")
            .append(bodySiteDisplay).append(NEWLINE);
        break;
      default:
        // no special description
    }

    // things common to many state types
    if (state.has("codes")) {
      JsonArray codes = state.get("codes").getAsJsonArray();
      codes.forEach(c -> {
        JsonObject coding = c.getAsJsonObject();
        String system = coding.get("system").getAsString();
        String code = coding.get("code").getAsString();
        String display = coding.get("display").getAsString();
        details.append(system).append("[").append(code).append("]: ").append(display)
            .append(NEWLINE);

      });
    }
    if (state.has("target_encounter")) {
      String verb = "Perform";
      switch (state.get("type").getAsString()) {
        case "ConditionOnset":
        case "AllergyOnset":
          verb = "Diagnose";
          break;
        case "MedicationOrder":
          verb = "Prescribe";
          break;
        default:
          // no special verb
      }
      details.append(verb).append(" at ").append(state.get("target_encounter").getAsString())
          .append(NEWLINE);
    }
    if (state.has("reason")) {
      details.append("Reason: ").append(state.get("reason").getAsString()).append(NEWLINE);
    }
    if (state.has("medication_order")) {
      details.append("Prescribed at: ").append(state.get("medication_order").getAsString())
          .append(NEWLINE);
    }
    if (state.has("condition_onset")) {
      details.append("Onset at: ").append(state.get("condition_onset").getAsString())
          .append(NEWLINE);
    }
    if (state.has("allergy_onset")) {
      details.append("Onset at: ").append(state.get("allergy_onset").getAsString()).append(NEWLINE);
    }
    if (state.has("careplan")) {
      details.append("Prescribed at: ").append(state.get("careplan").getAsString()).append(NEWLINE);
    }
    if (state.has("assign_to_attribute")) {
      details.append("Assign to Attribute: ").append(state.get("assign_to_attribute").getAsString())
          .append(NEWLINE);
    }
    if (state.has("referenced_by_attribute")) {
      details.append("Referenced By Attribute: ")
          .append(state.get("referenced_by_attribute").getAsString()).append(NEWLINE);
    }
    if (state.has("activities")) {
      details.append(NEWLINE).append("Activities:").append(NEWLINE);
      state.get("activities").getAsJsonArray().forEach(a -> {
        JsonObject coding = a.getAsJsonObject();
        String system = coding.get("system").getAsString();
        String code = coding.get("code").getAsString();
        String display = coding.get("display").getAsString();
        details.append(system).append("[").append(code).append("]: ").append(display)
            .append(NEWLINE);
      });
    }
    if (state.has("goals")) {
      details.append(NEWLINE).append("Goals:").append(NEWLINE);
      state.get("goals").getAsJsonArray().forEach(gl -> {
        JsonObject goal = gl.getAsJsonObject();
        if (goal.has("text")) {
          details.append(goal.get("text").getAsString()).append(NEWLINE);
        } else if (goal.has("codes")) {
          JsonObject coding = goal.get("codes").getAsJsonArray().get(0).getAsJsonObject();
          String system = coding.get("system").getAsString();
          String code = coding.get("code").getAsString();
          String display = coding.get("display").getAsString();
          details.append(system).append("[").append(code).append("]: ").append(display)
              .append(NEWLINE);
        } else if (goal.has("observation")) {
          JsonObject logic = goal.get("observation").getAsJsonObject();
          String obs = findReferencedType(logic);
          details.append("Observation ").append(obs).append("\\")
              .append(logic.get("operator").getAsString()).append(" ")
              .append(logic.get("value").getAsString()).append(NEWLINE);
        }
      });
    }
    if (state.has("duration")) {
      JsonObject d = state.get("duration").getAsJsonObject();
      String low = d.get("low").getAsString();
      String high = d.get("high").getAsString();
      String unit = d.get("unit").getAsString();
      details.append(NEWLINE).append("Duration: ").append(low).append(" - ").append(high)
          .append(" ").append(unit).append(NEWLINE);
    }
    if (state.has("category")) {
      details.append("Category: ").append(state.get("category")).append(NEWLINE);
    }

    return details.toString();
  }

  private static String logicDetails(JsonObject logic) {
    String conditionType = logic.get("condition_type").getAsString();

    switch (conditionType) {
      case "And":
      case "Or":
        List<String> subs = new LinkedList<>();
        logic.get("conditions").getAsJsonArray().forEach(c -> {
          JsonObject cond = c.getAsJsonObject();
          String innerConditionType = cond.get("condition_type").getAsString();
          if (innerConditionType.equals("And") || innerConditionType.equals("Or")) {
            subs.add(NEWLINE + logicDetails(cond) + NEWLINE);
          } else {
            subs.add(logicDetails(cond));
          }
        });

        return String.join(conditionType.toLowerCase() + " ", subs);
      case "At Least":
      case "At Most":
        String threshold;
        if (logic.has("minimum")) {
          threshold = logic.get("minimum").getAsString();
        } else {
          threshold = logic.get("maximum").getAsString();
        }

        subs = new LinkedList<>();
        logic.get("conditions").getAsJsonArray().forEach(c -> {
          JsonObject cond = c.getAsJsonObject();
          String innerConditionType = cond.get("condition_type").getAsString();
          if (innerConditionType.equals("And") || innerConditionType.equals("Or")) {
            subs.add(NEWLINE + logicDetails(cond) + NEWLINE);
          } else {
            subs.add(logicDetails(cond));
          }
        });

        return conditionType + " " + threshold + " of:" + NEWLINE + "- " + String.join("- ", subs);
      case "Not":
        JsonObject c = logic.get("condition").getAsJsonObject();
        String innerConditionType = c.get("condition_type").getAsString();
        if (innerConditionType.equals("And") || innerConditionType.equals("Or")) {
          return "not (" + NEWLINE + logicDetails(c) + ")" + NEWLINE;
        } else {
          return "not " + logicDetails(c);
        }
      case "Gender":
        return "gender is " + logic.get("gender").getAsString() + NEWLINE;
      case "Age":
        return "age \\" + logic.get("operator").getAsString() + " "
            + logic.get("quantity").getAsString() + " " + logic.get("unit").getAsString() + NEWLINE;
      case "Socioeconomic Status":
        return logic.get("category").getAsString() + " Socioeconomic Status" + NEWLINE;
      case "Race":
        return "race is " + logic.get("race").getAsString() + NEWLINE;
      case "Date":
        return "Year is \\" + logic.get("operator").getAsString() + " "
            + logic.get("year").getAsString() + NEWLINE;
      case "Symptom":
        return "Symptom: " + logic.get("symptom").getAsString() + " \\"
            + logic.get("operator").getAsString() + " " + logic.get("value").getAsString()
            + NEWLINE;
      case "PriorState":
        if (logic.has("within")) {
          JsonObject within = logic.get("within").getAsJsonObject();
          return "state '" + logic.get("name").getAsString() + "' has been processed within "
              + within.get("quantity").getAsString() + " " + within.get("unit").getAsString()
              + NEWLINE;
        } else {
          return "state '" + logic.get("name").getAsString() + "' has been processed" + NEWLINE;
        }
      case "Attribute":
        String value = logic.has("value") ? logic.get("value").getAsString() : "";
        return "Attribute: " + logic.get("attribute").getAsString() + " \\"
            + logic.get("operator").getAsString() + " " + value + NEWLINE;
      case "Observation":
        String obs = findReferencedType(logic);
        String valueString = "";
        if (logic.has("value")) {
          valueString = logic.get("value").getAsString();
        } else if (logic.has("value_code")) {
          JsonObject valueCode = logic.get("value_code").getAsJsonObject();
          valueString = "'" + valueCode.get("system").getAsString() + " ["
            + valueCode.get("code").getAsString() + "]: "
            + valueCode.get("display").getAsString() + "'";
        }
        return "Observation " + obs + " \\" + logic.get("operator").getAsString() + " "
            + valueString + NEWLINE;
      case "Vital Sign":
        return "Vital Sign " + logic.get("vital_sign").getAsString() + " \\"
            + logic.get("operator").getAsString() + " " + logic.get("value").getAsString() + "}"
            + NEWLINE;
      case "Active Condition":
        String cond = findReferencedType(logic);
        return "Condition " + cond + " is active" + NEWLINE;
      case "Active CarePlan":
        String plan = findReferencedType(logic);
        return "CarePlan " + plan + " is active" + NEWLINE;
      case "Active Medication":
        String med = findReferencedType(logic);
        return "Medication " + med + " is active" + NEWLINE;
      case "Active Allergy":
        String alg = findReferencedType(logic);
        return "Allergy " + alg + " is active" + NEWLINE;
      case "True":
      case "False":
        return conditionType;

      default:
        throw new RuntimeException("Unsupported condition: " + conditionType);
    }
  }

  private static String findReferencedType(JsonObject logic) {
    if (logic.has("codes")) {
      JsonObject coding = logic.get("codes").getAsJsonArray().get(0).getAsJsonObject();
      String system = coding.get("system").getAsString();
      String code = coding.get("code").getAsString();
      String display = coding.get("display").getAsString();

      return "'" + system + " [" + code + "]: " + display + "'";
    } else if (logic.has("referenced_by_attribute")) {
      return "Referenced By Attribute: '" + logic.get("referenced_by_attribute").getAsString()
          + "'";
    } else {
      String conditionType = logic.get("condition_type").getAsString();
      throw new RuntimeException(
          conditionType + " condition must be specified by code or attribute");
    }
  }
}
