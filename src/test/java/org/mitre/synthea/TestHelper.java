package org.mitre.synthea;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.mitre.synthea.engine.Module;
import org.mitre.synthea.helpers.Config;

public abstract class TestHelper {

	public static Module getFixture(String filename) throws IOException
	{
		Path modulesFolder = Paths.get("src/test/resources/generic");
		Path module = modulesFolder.resolve(filename);
		return Module.loadFile(module, modulesFolder);
	}
	
	public static void exportOff() {
		Config.set("generate.database_type", "none"); // ensure we don't write to a file-based DB
		Config.set("exporter.use_uuid_filenames", "false");
		Config.set("exporter.subfolders_by_id_substring", "false");
		Config.set("exporter.ccda.export", "false");
		Config.set("exporter.fhir.export","false");
		Config.set("exporter.hospital.fhir.export", "false");
		Config.set("exporter.cost_access_outcomes_report", "false");
	}
}
