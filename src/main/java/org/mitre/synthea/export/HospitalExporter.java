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
import java.util.concurrent.atomic.AtomicInteger;

import org.hl7.fhir.dstu3.model.Address;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryRequestComponent;
import org.hl7.fhir.dstu3.model.Bundle.BundleType;
import org.hl7.fhir.dstu3.model.Bundle.HTTPVerb;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.IntegerType;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Resource;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Provider;

public abstract class HospitalExporter {

  private static final FhirContext FHIR_CTX = FhirContext.forDstu3();

  private static final String SYNTHEA_URI = "http://synthetichealth.github.io/synthea/";

  protected static boolean TRANSACTION_BUNDLE =
      Boolean.parseBoolean(Config.get("exporter.fhir.transaction_bundle"));

  public static void export(long stop) {
    if (Boolean.parseBoolean(Config.get("exporter.hospital.fhir.export"))) {

      Bundle bundle = new Bundle();
      if (TRANSACTION_BUNDLE) {
        bundle.setType(BundleType.TRANSACTION);
      } else {
        bundle.setType(BundleType.COLLECTION);
      }
      for (Provider h : Provider.getProviderList()) {
        // filter - exports only those hospitals in use

        Table<Integer, String, AtomicInteger> utilization = h.getUtilization();
        int totalEncounters = utilization.column(Provider.ENCOUNTERS).values().stream()
            .mapToInt(ai -> ai.get()).sum();
        if (totalEncounters > 0) {
          addHospitalToBundle(h, bundle);
        }
      }

      String bundleJson = FHIR_CTX.newJsonParser().setPrettyPrint(true)
          .encodeResourceToString(bundle);

      // get output folder
      List<String> folders = new ArrayList<>();
      folders.add("fhir");
      String baseDirectory = Config.get("exporter.baseDirectory");
      File f = Paths.get(baseDirectory, folders.toArray(new String[0])).toFile();
      f.mkdirs();
      Path outFilePath = f.toPath().resolve("hospitalInformation" + stop + ".json");

      try {
        Files.write(outFilePath, Collections.singleton(bundleJson), StandardOpenOption.CREATE_NEW);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public static void addHospitalToBundle(Provider h, Bundle bundle) {
    Organization organizationResource = new Organization();

    organizationResource.addIdentifier().setSystem("https://github.com/synthetichealth/synthea")
        .setValue((String) h.getResourceID());

    organizationResource.setId(h.getResourceID());
    organizationResource.setName(h.name);

    Address address = new Address();
    address.addLine(h.address);
    address.setCity(h.city);
    address.setPostalCode(h.zip);
    address.setState(h.state);
    organizationResource.addAddress(address);

    Table<Integer, String, AtomicInteger> utilization = h.getUtilization();
    // calculate totals for utilization
    int totalEncounters = utilization.column(Provider.ENCOUNTERS).values().stream()
        .mapToInt(ai -> ai.get()).sum();
    Extension encountersExtension = new Extension(SYNTHEA_URI + "utilization-encounters-extension");
    IntegerType encountersValue = new IntegerType(totalEncounters);
    encountersExtension.setValue(encountersValue);
    organizationResource.addExtension(encountersExtension);

    int totalProcedures = utilization.column(Provider.PROCEDURES).values().stream()
        .mapToInt(ai -> ai.get()).sum();
    Extension proceduresExtension = new Extension(SYNTHEA_URI + "utilization-procedures-extension");
    IntegerType proceduresValue = new IntegerType(totalProcedures);
    proceduresExtension.setValue(proceduresValue);
    organizationResource.addExtension(proceduresExtension);

    int totalLabs = utilization.column(Provider.LABS).values().stream().mapToInt(ai -> ai.get())
        .sum();
    Extension labsExtension = new Extension(SYNTHEA_URI + "utilization-labs-extension");
    IntegerType labsValue = new IntegerType(totalLabs);
    labsExtension.setValue(labsValue);
    organizationResource.addExtension(labsExtension);

    int totalPrescriptions = utilization.column(Provider.PRESCRIPTIONS).values().stream()
        .mapToInt(ai -> ai.get()).sum();
    Extension prescriptionsExtension = new Extension(
        SYNTHEA_URI + "utilization-prescriptions-extension");
    IntegerType prescriptionsValue = new IntegerType(totalPrescriptions);
    prescriptionsExtension.setValue(prescriptionsValue);
    organizationResource.addExtension(prescriptionsExtension);

    Integer bedCount = h.getBedCount();
    if (bedCount != null) {
      Extension bedCountExtension = new Extension(SYNTHEA_URI + "bed-count-extension");
      IntegerType bedCountValue = new IntegerType(bedCount);
      bedCountExtension.setValue(bedCountValue);
      organizationResource.addExtension(bedCountExtension);
    }

    newEntry(bundle, organizationResource, h.getResourceID());
  }

  private static BundleEntryComponent newEntry(Bundle bundle, Resource resource,
      String resourceID) {
    BundleEntryComponent entry = bundle.addEntry();

    resource.setId(resourceID);
    entry.setFullUrl("urn:uuid:" + resourceID);

    entry.setResource(resource);

    if (TRANSACTION_BUNDLE) {
      BundleEntryRequestComponent request = entry.getRequest();
      request.setMethod(HTTPVerb.POST);
      request.setUrl(resource.getResourceType().name());
      entry.setRequest(request);
    }

    return entry;
  }
}