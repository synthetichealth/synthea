package org.mitre.synthea.export;

import ca.uhn.fhir.parser.IParser;

import com.google.common.collect.Table;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.Bundle.BundleType;
import org.hl7.fhir.dstu3.model.IntegerType;
import org.hl7.fhir.dstu3.model.Practitioner;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Provider;

public abstract class FhirPractitionerExporterStu3 {

  private static final String EXTENSION_URI =
      "http://synthetichealth.github.io/synthea/utilization-encounters-extension";

  /**
   * Export the practitioner in FHIR STU3 format.
   */
  public static void export(long stop) {
    if (Config.getAsBoolean("exporter.practitioner.fhir_stu3.export")) {

      Bundle bundle = new Bundle();
      if (Config.getAsBoolean("exporter.fhir.transaction_bundle")) {
        bundle.setType(BundleType.BATCH);
      } else {
        bundle.setType(BundleType.COLLECTION);
      }
      for (Provider h : Provider.getProviderList()) {
        // filter - exports only those hospitals in use

        Table<Integer, String, AtomicInteger> utilization = h.getUtilization();
        int totalEncounters = utilization.column(Provider.ENCOUNTERS).values().stream()
            .mapToInt(ai -> ai.get()).sum();
        if (totalEncounters > 0) {
          Map<String, ArrayList<Clinician>> clinicians = h.clinicianMap;
          for (String specialty : clinicians.keySet()) {
            ArrayList<Clinician> docs = clinicians.get(specialty);
            for (Clinician doc : docs) {
              if (doc.getEncounterCount() > 0) {
                BundleEntryComponent entry = FhirStu3.practitioner(bundle, doc);
                Practitioner practitioner = (Practitioner) entry.getResource();
                practitioner.addExtension()
                  .setUrl(EXTENSION_URI)
                  .setValue(new IntegerType(doc.getEncounterCount()));
              }
            }
          }
        }
      }

      boolean ndjson = Config.getAsBoolean("exporter.fhir.bulk_data", false);
      File outputFolder = Exporter.getOutputFolder("fhir_stu3", null);
      IParser parser = FhirStu3.getContext().newJsonParser();

      if (ndjson) {
        Path outFilePath = outputFolder.toPath().resolve("Practitioner." + stop + ".ndjson");
        for (BundleEntryComponent entry : bundle.getEntry()) {
          String entryJson = parser.encodeResourceToString(entry.getResource());
          Exporter.appendToFile(outFilePath, entryJson);
        }
      } else {
        Boolean pretty = Config.getAsBoolean("exporter.pretty_print", true);
        parser = parser.setPrettyPrint(pretty);
        Path outFilePath =
            outputFolder.toPath().resolve("practitionerInformation" + stop + ".json");
        String bundleJson = parser.encodeResourceToString(bundle);
        Exporter.overwriteFile(outFilePath, bundleJson);
      }
    }
  }
}