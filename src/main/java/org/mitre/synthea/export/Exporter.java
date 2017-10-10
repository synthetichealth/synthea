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
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.helpers.SimpleCSV;



public abstract class Exporter 
{
	/**
	 * Export a single patient, into all the formats supported.
	 * (Formats may be enabled or disabled by configuration)
	 * 
	 * @param person Patient to export
	 * @param stopTime Time at which the simulation stopped
	 */
	public static void export(Person person, long stopTime)
	{
		// TODO: filter for export
		if (Boolean.parseBoolean(Config.get("exporter.fhir.export")))
		{
			String bundleJson = FhirStu3.convertToFHIR(person, stopTime);
			
			File outDirectory = getOutputFolder("fhir", person);
			Path outFilePath = outDirectory.toPath().resolve(filename(person, "json"));
			
			try 
			{
				Files.write(outFilePath, Collections.singleton(bundleJson), StandardOpenOption.CREATE_NEW);
			} catch (IOException e) 
			{
				e.printStackTrace();
			}
		}
		if (Boolean.parseBoolean(Config.get("exporter.ccda.export")))
		{
			String ccdaXml = CCDAExporter.export(person, stopTime);
			
			File outDirectory = getOutputFolder("ccda", person);
			Path outFilePath = outDirectory.toPath().resolve(filename(person, "xml"));
			
			try 
			{
				Files.write(outFilePath, Collections.singleton(ccdaXml), StandardOpenOption.CREATE_NEW);
			} catch (IOException e) 
			{
				e.printStackTrace();
			}
		}		
	}
	
	/**
	 * Run any exporters that require the full dataset to be generated prior to exporting.
	 * (Ex, an aggregate statistical exporter)
	 * 
	 * @param generator Generator that generated the patients
	 */
	public static void runPostCompletionExports(Generator generator) 
	{
		try{
			HospitalExporter.export(generator.stop);			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if (Boolean.parseBoolean(Config.get("exporter.cost_access_outcomes_report")))
		{
			ReportExporter.export(generator);
		}
		
		try{
			SimpleCSV.export(generator);	
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public static File getOutputFolder(String folderName, Person person)
	{
		List<String> folders = new ArrayList<>();
		
		folders.add(folderName);
		
		if (person != null && Boolean.parseBoolean(Config.get("exporter.subfolders_by_id_substring")))
		{
			String id = (String)person.attributes.get(Person.ID);
			
			folders.add(id.substring(0, 2));
			folders.add(id.substring(0, 3));
		}
		
		String baseDirectory = Config.get("exporter.baseDirectory");
		
		File f = Paths.get(baseDirectory, folders.toArray(new String[0])).toFile();
		f.mkdirs();
		
		return f;
	}
	
	public static String filename(Person person, String extension)
	{
		if (Boolean.parseBoolean(Config.get("exporter.use_uuid_filenames")))
		{
			return person.attributes.get(Person.ID) + "." + extension;
		} else
		{
			// ensure unique filenames for now
			return person.attributes.get(Person.NAME) + "_" + person.attributes.get(Person.ID) + "." + extension;
		}
		
	}
	
}
