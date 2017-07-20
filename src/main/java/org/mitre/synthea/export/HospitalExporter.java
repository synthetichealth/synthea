package org.mitre.synthea.export;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Resource;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.Hospital;
import org.mitre.synthea.world.Provider;

import ca.uhn.fhir.context.FhirContext;

public abstract class HospitalExporter{
	
	private static final FhirContext FHIR_CTX = FhirContext.forDstu3();
	
	public static void export(){
		if(Boolean.parseBoolean(Config.get("exporter.fhir.export"))){
			
			String bundleJson = "";
			for(Hospital h : Hospital.getHospitalList()){
				bundleJson = bundleJson + convertToFHIR(h);
			}
			
			// get output folder
			List<String> folders = new ArrayList<>();
			folders.add("fhir");
			String baseDirectory = Config.get("exporter.baseDirectory");
			File f = Paths.get(baseDirectory, folders.toArray(new String[0])).toFile();
			f.mkdirs();
			Path outFilePath = f.toPath().resolve("hospitalInformation.json");
			
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