package org.mitre.synthea.export;

import ca.uhn.fhir.context.FhirContext;
import com.google.common.collect.Table;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Practitioner;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Provider;

public abstract class FhirPractitionerExporterR4 {

  private static final FhirContext FHIR_CTX = FhirContext.forR4();

  private static final String EXTENSION_URI = "http://synthetichealth.github.io/synthea/utilization-encounters-extension";

  public static void export(long stop) {
    if (Boolean.parseBoolean(Config.get("exporter.practitioner.fhir.export"))) {

      Bundle bundle = new Bundle();
      if (Boolean.parseBoolean(Config.get("exporter.fhir.transaction_bundle"))) {
        bundle.setType(BundleType.TRANSACTION);
      } else {
        bundle.setType(BundleType.COLLECTION);
      }
      for (Provider h : Provider.getProviderList()) {
        // filter - exports only those hospitals in use

        Table<Integer, String, AtomicInteger> utilization = h.getUtilization();
        int totalEncounters = utilization.column(Provider.ENCOUNTERS).values().stream().mapToInt(ai -> ai.get()).sum();
        if (totalEncounters > 0) {
          Map<String, ArrayList<Clinician>> clinicians = h.clinicianMap;
          for (String specialty : clinicians.keySet()) {
            ArrayList<Clinician> docs = clinicians.get(specialty);
            for (Clinician doc : docs) {
              if (doc.getEncounterCount() > 0) {
                BundleEntryComponent entry = FhirR4.practitioner(bundle, doc);
                Practitioner practitioner = (Practitioner) entry.getResource();
                practitioner.addExtension().setUrl(EXTENSION_URI).setValue(new IntegerType(doc.getEncounterCount()));
              }
            }
          }
        }
      }

      String bundleJson = FHIR_CTX.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);

      

      try {
        if (Boolean.parseBoolean(Config.get("exporter.upload_directly_to_aws_s3")) == true) {
          // todo : write to aws3
        } else {
          // get output folder
          List<String> folders = new ArrayList<>();
          folders.add("fhir");
          String baseDirectory = Config.get("exporter.baseDirectory");
          File f = Paths.get(baseDirectory, folders.toArray(new String[0])).toFile();
          f.mkdirs();
          Path outFilePath = f.toPath().resolve("practitionerInformation" + stop + ".json");
          
          Files.write(outFilePath, Collections.singleton(bundleJson), StandardOpenOption.CREATE_NEW);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}