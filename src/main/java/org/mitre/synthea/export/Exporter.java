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

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.modules.Person;

public abstract class Exporter 
{
	public static void export(Person person, long stopTime)
	{
		// TODO: filter for export
		if (Boolean.parseBoolean(Config.get("exporter.fhir.export")))
		{
			String bundleJson = FhirStu3.convertToFHIR(person);
			
			File outDirectory = getOutputFolder("fhir", person);
			
			Path outFilePath = outDirectory.toPath().resolve(filename(person));
			
			try 
			{
				Files.write(outFilePath, Collections.singleton(bundleJson), StandardOpenOption.CREATE_NEW);
			} catch (IOException e) 
			{
				e.printStackTrace();
			}
		}
	}
	
	public static File getOutputFolder(String folderName, Person person)
	{
		List<String> folders = new ArrayList<>();
		
		folders.add(folderName);
		
		if (Boolean.parseBoolean(Config.get("exporter.subfolders_by_id_substring")))
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
	
	public static String filename(Person person)
	{
		if (Boolean.parseBoolean(Config.get("exporter.use_uuid_filenames")))
		{
			return person.attributes.get(Person.ID) + ".json";
		} else
		{
			// ensure unique filenames for now
			return person.attributes.get(Person.NAME) + "_" + person.attributes.get(Person.ID) + ".json";
		}
		
	}
	
}
