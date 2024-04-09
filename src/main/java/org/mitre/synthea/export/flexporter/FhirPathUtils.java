package org.mitre.synthea.export.flexporter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.exec.util.StringUtils;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.mitre.synthea.export.FhirR4;


public abstract class FhirPathUtils {

  private static final FhirPathR4 FHIRPATH = new FhirPathR4(FhirR4.getContext());

  /**
   * Execute the given FHIRPath against the given resource and return the results.
   *
   * @param resource FHIR resource to execute FHIRPath against
   * @param fhirpath FHIRPath string
   * @return Raw values from FHIRPath engine evaluating the string
   */
  public static List<Base> evaluateResource(Resource resource, String fhirpath) {
    return FHIRPATH.evaluate(resource, fhirpath, Base.class);
  }

  /**
   * Execute the given FHIRPath against the given Bundle and return the results. There are a few
   * different possibilities in how this works.
   * - If the FHIRPath string starts with "Bundle" then this will execute the FHIRPath once
   * against the Bundle as a whole, otherwise it will be executed against each resource.
   * - If "returnResources" is true, then resources from the Bundle
   * that return a truthy value will be returned, otherwise the raw value from the FHIRpath engine
   * will be returned.
   *
   * @param bundle FHIR bundle to evaluate FHIRpath against
   * @param fhirpath FHIRPath string
   * @param returnResources If true, return resources from bundle matching FHIRPath; if false,
   *     return raw values from FHIRPath engine
   * @return Differs based on input - see above
   */
  public static List<Base> evaluateBundle(Bundle bundle, String fhirpath, boolean returnResources) {
    if (fhirpath.startsWith("Bundle")) {
      // run it on the entire bundle

      // NOTE: this doesn't check returnResources -- would that be useful here?
      return evaluateResource(bundle, fhirpath);
    } else {
      // the fhirpath doesn't start with "Bundle"
      //  so we'll apply it to each resource within the bundle
      List<Base> results = new ArrayList<>();

      for (BundleEntryComponent entry : bundle.getEntry()) {
        List<Base> resourceResults = evaluateResource(entry.getResource(), fhirpath);

        if (returnResources) {
          if (isTruthy(resourceResults)) {
            results.add(entry.getResource());
          }
        } else {
          results.addAll(resourceResults);
        }
      }

      return results;
    }
  }

  /**
   * Execute the given FHIRPath against the given Bundle and return the results. There are a few
   * different possibilities in how this works.
   * - If the FHIRPath string starts with "Bundle" then this will execute the FHIRPath once
   * against the Bundle as a whole, otherwise it will be executed against each resource.
   * - If "returnResources" is true, then resources from the Bundle
   * that return a truthy value will be returned, otherwise the raw value from the FHIRpath engine
   * will be returned.
   * This version allows for specifying variables to inject into the FHIRPath.
   *
   * @param bundle FHIR bundle to evaluate FHIRpath against
   * @param fhirpath FHIRPath string
   * @param variables FHIRPath variables to inject
   * @param returnResources If true, return resources from bundle matching FHIRPath; if false,
   *     return raw values from FHIRPath engine
   * @return Differs based on input - see above
   */
  public static List<Base> evaluateBundle(Bundle bundle, String fhirpath,
      Map<String, Object> variables, boolean returnResources) {

    if (variables != null) {
      for (Map.Entry<String, Object> entry : variables.entrySet()) {
        Object replacementObj = entry.getValue();

        String replacement = null;
        if (replacementObj instanceof String) {
          replacement = (String) replacementObj;
        } else if (replacementObj instanceof List) {
          List<String> replacementList = (List<String>) replacementObj;

          replacementList = replacementList.stream()
              .map(s -> {
                if (StringUtils.isQuoted(s)) {
                  return s;
                }
                // quoting strings is very difficult in general
                // but for now assume this will simple things like codes
                // which don't contain nested quotes
                if (s.contains("'")) {
                  return "\"" + s + "\"";
                } else {
                  return "'" + s + "'";
                }
              })
              .collect(Collectors.toList());

          replacement = "(" + String.join(" | ", replacementList) + ")";
        }


        if (replacement != null) {
          fhirpath = fhirpath.replace("%" + entry.getKey(), replacement);
        }
      }
    }

    return evaluateBundle(bundle, fhirpath, returnResources);
  }


  public static boolean appliesToResource(Resource resource, String fhirpath) {
    return isTruthy(evaluateResource(resource, fhirpath));
  }

  public static boolean appliesToBundle(Bundle bundle, String fhirpath) {
    return isTruthy(evaluateBundle(bundle, fhirpath, false));
  }

  public static boolean appliesToBundle(Bundle bundle, String fhirpath,
      Map<String, Object> variables) {
    return isTruthy(evaluateBundle(bundle, fhirpath, variables, false));
  }

  /**
   * Helper function to convert FHIRPath evaluation primitives into a boolean.
   * Nulls, empty strings, and boolean false all mean "false" here. Everything else means "true".
   */
  static boolean isTruthy(Base result) {
    if (result == null) {
      return false;
    } else if (result instanceof StringType) {
      StringType str = ((StringType) result);
      return !str.isEmpty() && !str.getValue().isEmpty();
    } else if (result instanceof BooleanType) {
      BooleanType bool = ((BooleanType) result);
      return !bool.isEmpty() && bool.booleanValue();
    }

    return true;
  }

  /**
   * Helper function to convert FHIRPath evaluation results into a boolean.
   * FHIRPath.evaluate returns a {@code List<Base>} which is matching pieces of resources.
   * This will return false if the list is empty, or if everything in the list is falsy.
   */
  static boolean isTruthy(List<Base> result) {
    if (result == null || result.isEmpty()) {
      return false;
    }

    return result.stream().anyMatch(i -> isTruthy(i));
  }
}
