package org.mitre.synthea.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.ExtensionDt;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Bundle.Entry;
import ca.uhn.fhir.model.dstu2.resource.Organization;
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
import java.util.concurrent.atomic.AtomicInteger;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.writer.AWSS3Writer;

public abstract class HospitalExporterDstu2 {

  private static final FhirContext FHIR_CTX = FhirContext.forDstu2();

  private static final String SYNTHEA_URI = "http://synthetichealth.github.io/synthea/";

  public static void export(long stop) {
    if (Boolean.parseBoolean(Config.get("exporter.hospital.fhir_dstu2.export"))) {

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
          Entry entry = FhirDstu2.provider(bundle, h);
          addHospitalExtensions(h, (Organization) entry.getResource());
        }
      }

      String bundleJson = FHIR_CTX.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);

      try {
        // get output folder
        List<String> folders = new ArrayList<>();
        folders.add("fhir_dstu2");
        String baseDirectory = Config.get("exporter.baseDirectory");
        File f = Paths.get(baseDirectory, folders.toArray(new String[0])).toFile();
        f.mkdirs();
        Path outFilePath = f.toPath().resolve("hospitalInformation" + stop + ".json");
        if (Boolean.parseBoolean(Config.get("exporter.upload_directly_to_aws_s3"))) {
          AWSS3Writer.appendToFile("fhir_stu3", "practitionerInformation" + stop + ".json", Collections.singleton(bundleJson).toString());
        } else {
          Files.write(outFilePath, Collections.singleton(bundleJson), StandardOpenOption.CREATE_NEW);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public static void addHospitalExtensions(Provider h, Organization organizationResource) {
    Table<Integer, String, AtomicInteger> utilization = h.getUtilization();
    // calculate totals for utilization
    int totalEncounters = utilization.column(Provider.ENCOUNTERS).values().stream().mapToInt(ai -> ai.get()).sum();
    ExtensionDt encountersExtension = new ExtensionDt();
    encountersExtension.setUrl(SYNTHEA_URI + "utilization-encounters-extension");
    IntegerDt encountersValue = new IntegerDt(totalEncounters);
    encountersExtension.setValue(encountersValue);
    organizationResource.addUndeclaredExtension(encountersExtension);

    int totalProcedures = utilization.column(Provider.PROCEDURES).values().stream().mapToInt(ai -> ai.get()).sum();
    ExtensionDt proceduresExtension = new ExtensionDt();
    proceduresExtension.setUrl(SYNTHEA_URI + "utilization-procedures-extension");
    IntegerDt proceduresValue = new IntegerDt(totalProcedures);
    proceduresExtension.setValue(proceduresValue);
    organizationResource.addUndeclaredExtension(proceduresExtension);

    int totalLabs = utilization.column(Provider.LABS).values().stream().mapToInt(ai -> ai.get()).sum();
    ExtensionDt labsExtension = new ExtensionDt();
    labsExtension.setUrl(SYNTHEA_URI + "utilization-labs-extension");
    IntegerDt labsValue = new IntegerDt(totalLabs);
    labsExtension.setValue(labsValue);
    organizationResource.addUndeclaredExtension(labsExtension);

    int totalPrescriptions = utilization.column(Provider.PRESCRIPTIONS).values().stream().mapToInt(ai -> ai.get())
        .sum();
    ExtensionDt prescriptionsExtension = new ExtensionDt();
    prescriptionsExtension.setUrl(SYNTHEA_URI + "utilization-prescriptions-extension");
    IntegerDt prescriptionsValue = new IntegerDt(totalPrescriptions);
    prescriptionsExtension.setValue(prescriptionsValue);
    organizationResource.addUndeclaredExtension(prescriptionsExtension);

    Integer bedCount = h.getBedCount();
    if (bedCount != null) {
      ExtensionDt bedCountExtension = new ExtensionDt();
      bedCountExtension.setUrl(SYNTHEA_URI + "bed-count-extension");
      IntegerDt bedCountValue = new IntegerDt(bedCount);
      bedCountExtension.setValue(bedCountValue);
      organizationResource.addUndeclaredExtension(bedCountExtension);
    }
  }
}