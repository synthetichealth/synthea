package org.mitre.synthea.export;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.dstu3.model.Address;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.IntegerType;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Resource;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.Hospital;
import org.mitre.synthea.world.Provider;

import com.google.gson.internal.LinkedTreeMap;

import ca.uhn.fhir.context.FhirContext;

public abstract class HospitalExporter{
	
	private static final FhirContext FHIR_CTX = FhirContext.forDstu3();
	
	private static final String SYNTHEA_URI = "http://synthetichealth.github.io/synthea/";
	
	public static void export(){
		if(Boolean.parseBoolean(Config.get("exporter.hospital.fhir.export"))){
			
			String bundleJson = "";
			for(Hospital h : Hospital.getHospitalList()){
				// filter - exports only those hospitals in use
				if(h.getUtilization().get(Provider.ENCOUNTERS) != 0){
					bundleJson = bundleJson + convertToFHIR(h);
				}
			}
			
			// get output folder
			List<String> folders = new ArrayList<>();
			folders.add("fhir");
			String baseDirectory = Config.get("exporter.baseDirectory");
			File f = Paths.get(baseDirectory, folders.toArray(new String[0])).toFile();
			f.mkdirs();
			Path outFilePath = f.toPath().resolve("hospitalInformation.json");
			
			// delete previous file if it exists
			try {
				Files.delete(outFilePath);
			} catch (IOException e) {
			}
		
			try {
				Files.write(outFilePath, Collections.singleton(bundleJson), StandardOpenOption.CREATE_NEW);
			} catch (IOException e) 
			{
				e.printStackTrace();
			}
		}
	}
	
	public static String convertToFHIR(Hospital h){
		Bundle bundle = new Bundle();
		Organization organizationResource = new Organization();
		
		organizationResource.addIdentifier()
			.setSystem("https://github.com/synthetichealth/synthea")
			.setValue((String) h.getResourceID());
		
		LinkedTreeMap hAttributes = h.getAttributes();
		
		organizationResource.setName(hAttributes.get("name").toString());
		
		Address address = new Address();
		address.addLine(hAttributes.get("address").toString());
		address.setCity(hAttributes.get("city").toString());
		address.setPostalCode(hAttributes.get("city_zip").toString());
		address.setState(hAttributes.get("state").toString());
		organizationResource.addAddress(address);

		Extension encountersExtension = new Extension(SYNTHEA_URI + "utilization-encounters-extension");
		IntegerType encountersValue = new IntegerType(h.getUtilization().get(Provider.ENCOUNTERS));
		encountersExtension.setValue(encountersValue);
		organizationResource.addExtension(encountersExtension);
		
		Extension proceduresExtension = new Extension(SYNTHEA_URI + "utilization-procedures-extension");
		IntegerType proceduresValue = new IntegerType(h.getUtilization().get(Provider.PROCEDURES));
		proceduresExtension.setValue(proceduresValue);
		organizationResource.addExtension(proceduresExtension);
		
		Extension labsExtension = new Extension(SYNTHEA_URI + "utilization-labs-extension");
		IntegerType labsValue = new IntegerType(h.getUtilization().get(Provider.LABS));
		labsExtension.setValue(labsValue);
		organizationResource.addExtension(labsExtension);
		
		Extension prescriptionsExtension = new Extension(SYNTHEA_URI + "utilization-prescriptions-extension");
		IntegerType prescriptionsValue = new IntegerType(h.getUtilization().get(Provider.PRESCRIPTIONS));
		prescriptionsExtension.setValue(prescriptionsValue);
		organizationResource.addExtension(prescriptionsExtension);
		
		Integer bedCount = h.getBedCount();
		if(bedCount != null){
			Extension bedCountExtension = new Extension(SYNTHEA_URI + "bed-count-extension");
			IntegerType bedCountValue = new IntegerType(bedCount);
			bedCountExtension.setValue(bedCountValue);
			organizationResource.addExtension(bedCountExtension);
		}
		
		BundleEntryComponent hospitalEntry = newEntry(bundle, organizationResource, h.getResourceID());
		String bundleJson = FHIR_CTX.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
		return bundleJson;
	}
	
	private static BundleEntryComponent newEntry(Bundle bundle, Resource resource, String resourceID){
		BundleEntryComponent entry = bundle.addEntry();

		resource.setId(resourceID);
		entry.setFullUrl("urn:uuid:" + resourceID);
		
		entry.setResource(resource);
		return entry;
	}
}