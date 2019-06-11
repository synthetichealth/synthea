package org.mitre.synthea.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.ExtensionDt;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Bundle.Entry;
import ca.uhn.fhir.model.dstu2.resource.Practitioner;
import ca.uhn.fhir.model.dstu2.valueset.BundleTypeEnum;
import ca.uhn.fhir.model.primitive.IntegerDt;

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

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Provider;

public abstract class FhirPractitionerExporterDstu2 {

  private static final FhirContext FHIR_CTX = FhirContext.forDstu2();

  private static final String EXTENSION_URI = "http://synthetichealth.github.io/synthea/utilization-encounters-extension";

  public static void export(long stop) {
    if (Boolean.parseBoolean(Config.get("exporter.practitioner.fhir_dstu2.export"))) {

      Bundle bundle = new Bundle();
      if (Boolean.parseBoolean(Config.get("exporter.fhir.transaction_bundle"))) {
        bundle.setType(BundleTypeEnum.TRANSACTION);
      } else {
        bundle.setType(BundleTypeEnum.COLLECTION);
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
                Entry entry = FhirDstu2.practitioner(bundle, doc);
                Practitioner practitioner = (Practitioner) entry.getResource();
                ExtensionDt extension = new ExtensionDt();
                extension.setUrl(EXTENSION_URI);
                extension.setValue(new IntegerDt(doc.getEncounterCount()));
                practitioner.addUndeclaredExtension(extension);
              }
            }
          }
        }
      }

      String bundleJson = FHIR_CTX.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);

      try {
        if (Boolean.parseBoolean(Config.get("exporter.useAwsS3")) == true) {
          // todo : write to aws3
        } else {
          // get output folder
          List<String> folders = new ArrayList<>();
          folders.add("fhir_dstu2");
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