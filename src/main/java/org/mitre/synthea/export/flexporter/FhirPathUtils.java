package org.mitre.synthea.export.flexporter;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;

import ca.uhn.fhir.context.FhirContext;

public abstract class FhirPathUtils {
  public static final FhirContext FHIR_CTX = FhirContext.forR4();

  private static final FhirPathR4 FHIRPATH = new FhirPathR4(FHIR_CTX);

  public static List<Base> evaluateResource(Resource resource, String fhirpath) {
    return FHIRPATH.evaluate(resource, fhirpath, Base.class);
  }

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

  public static boolean appliesToResource(Resource resource, String fhirpath) {
    return isTruthy(evaluateResource(resource, fhirpath));
  }

  public static boolean appliesToBundle(Bundle bundle, String fhirpath) {
    return isTruthy(evaluateBundle(bundle, fhirpath, false));
  }

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

  static boolean isTruthy(List<Base> result) {
    if (result == null || result.isEmpty()) {
      return false;
    }

    return result.stream().anyMatch(i -> isTruthy(i));
  }
}
