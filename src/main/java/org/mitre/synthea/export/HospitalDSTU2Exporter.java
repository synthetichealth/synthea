package org.mitre.synthea.export;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.ExtensionDt;
import ca.uhn.fhir.model.dstu2.composite.AddressDt;
import ca.uhn.fhir.model.dstu2.resource.BaseResource;
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

public abstract class HospitalDSTU2Exporter {

  private static final FhirContext FHIR_CTX = FhirContext.forDstu2();

  private static final String SYNTHEA_URI = "http://synthetichealth.github.io/synthea/";

  public static void export(long stop) {
    if (Boolean.parseBoolean(Config.get("exporter.hospital.fhir_dstu2.export"))) {
      
      Bundle bundle = new Bundle();
      bundle.setType(BundleTypeEnum.COLLECTION);
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
      folders.add("fhir_dstu2");
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

    organizationResource.setName(h.name);

    AddressDt address = new AddressDt();
    address.addLine(h.address);
    address.setCity(h.city);
    address.setPostalCode(h.zip);
    address.setState(h.state);
    organizationResource.addAddress(address);

    Table<Integer, String, AtomicInteger> utilization = h.getUtilization();
    // calculate totals for utilization
    int totalEncounters = utilization.column(Provider.ENCOUNTERS).values().stream()
        .mapToInt(ai -> ai.get()).sum();
    ExtensionDt encountersExtension = new ExtensionDt();
    encountersExtension.setUrl(SYNTHEA_URI + "utilization-encounters-extension");
    IntegerDt encountersValue = new IntegerDt(totalEncounters);
    encountersExtension.setValue(encountersValue);
    organizationResource.addUndeclaredExtension(encountersExtension);

    int totalProcedures = utilization.column(Provider.PROCEDURES).values().stream()
        .mapToInt(ai -> ai.get()).sum();
    ExtensionDt proceduresExtension = new ExtensionDt();
    proceduresExtension.setUrl(SYNTHEA_URI + "utilization-procedures-extension");
    IntegerDt proceduresValue = new IntegerDt(totalProcedures);
    proceduresExtension.setValue(proceduresValue);
    organizationResource.addUndeclaredExtension(proceduresExtension);

    int totalLabs = utilization.column(Provider.LABS).values().stream().mapToInt(ai -> ai.get())
        .sum();
    ExtensionDt labsExtension = new ExtensionDt();
    labsExtension.setUrl(SYNTHEA_URI + "utilization-labs-extension");
    IntegerDt labsValue = new IntegerDt(totalLabs);
    labsExtension.setValue(labsValue);
    organizationResource.addUndeclaredExtension(labsExtension);

    int totalPrescriptions = utilization.column(Provider.PRESCRIPTIONS).values().stream()
        .mapToInt(ai -> ai.get()).sum();
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

    newEntry(bundle, organizationResource, h.getResourceID());
  }

  private static Entry newEntry(Bundle bundle, BaseResource resource,
      String resourceID) {
    Entry entry = bundle.addEntry();

    resource.setId(resourceID);
    entry.setFullUrl("urn:uuid:" + resourceID);

    entry.setResource(resource);
    return entry;
  }
}