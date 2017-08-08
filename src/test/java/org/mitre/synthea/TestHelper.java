package org.mitre.synthea;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.mitre.synthea.modules.Module;

public abstract class TestHelper {

	public static Module getFixture(String filename) throws IOException
	{
		Path modulesFolder = Paths.get("src/test/resources/generic");
		Path module = modulesFolder.resolve(filename);
		return Module.loadFile(module, modulesFolder);
	}
}
