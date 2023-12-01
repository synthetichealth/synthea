package org.mitre.synthea.export.flexporter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import ca.uhn.fhir.parser.IParser;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Patient;
import org.junit.Test;
import org.mitre.synthea.export.FhirR4;

public class FlexporterJavaScriptContextTest {

  @Test
  public void testJavascriptContext() {
    FlexporterJavascriptContext context = new FlexporterJavascriptContext();

    context.loadFunction("function applySomeProfile(resource) {\n"
        + "  resource['meta'] = {profile: [\"http://example.com/random-profile\"]}\n"
        + "}");

    context.loadFunction("function giveEveryResourceSomeText(bundle) {\n"
        + "  bundle.entry.forEach(e => e.resource.text = {\n"
        + "    \"status\": \"generated\",\n"
        + "    \"div\": \"<div>javascript was here</div>\"\n"
        + "  });\n"
        + "}");

    Patient p = new Patient();
    p.addName().addGiven("Alex").setFamily("Aquamarine");
    DateType date = new DateType();
    date.fromStringValue("1999-09-29");
    p.setBirthDateElement(date);

    Bundle b = new Bundle();
    b.addEntry().setResource(p);

    IParser parser = FhirR4.getContext().newJsonParser();

    String bundleJson = parser.encodeResourceToString(b);
    context.loadBundle(bundleJson);

    context.applyFunctionToBundle("giveEveryResourceSomeText");
    context.applyFunctionToResources("applySomeProfile", "Patient");

    String outBundleJson = context.getBundle();

    Bundle outBundle = parser.parseResource(Bundle.class, outBundleJson);

    outBundle.getEntry().forEach(entry -> {
      DomainResource resource = (DomainResource) entry.getResource();
      assertEquals("http://example.com/random-profile", resource.getMeta().getProfile().get(0).getValueAsString());

      assertTrue(resource.getText().getDivAsString().contains("javascript was here"));
      // check here is "contains" instead of simple equality
      // because HAPI seems to add the xmlns to the text, which is probably consistent,
      // but this is the real point of the test
    });
  }
}
