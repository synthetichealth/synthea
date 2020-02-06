package org.mitre.synthea.helpers;

import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;

public class ModuleOverrides {

	// sample line osteoporosis.json\:\:$['states']['Male']['distributed_transition'][1]['distribution'] = 0.02
	
	
	private List<String> includeFields;
	private List<String> excludeFields;
	private FilenameFilter includeModules;
	private FilenameFilter excludeModules;
	
	/**
	 * 
	 * @param args -- format is [includeFields,includeModules,excludeFields,excludeModules]
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		String includeFieldsArg = args[0];
		String includeModulesArg = args[1];
		String excludeFieldsArg = args[2];
		String excludeModulesArg = args[3];
	
		List<String> excludeFields = argToList(excludeFieldsArg, null);
		List<String> includeFields;
		if (excludeFields == null) {
			includeFields = argToList(includeFieldsArg, Arrays.asList("distribution"));
		} else {
			includeFields = null; // if they exclude something, don't do anything with includes
		}
		
		List<String> includeModules = argToList(includeModulesArg, null);
		List<String> excludeModules = argToList(excludeModulesArg, null);
		
		System.out.println("Included fields: " + includeFields);
		System.out.println("Excluded fields: " + excludeFields);
		System.out.println("Included modules: " + includeModules);
		System.out.println("Excluded modules: " + excludeModules);
		
		ModuleOverrides mo = new ModuleOverrides(includeFields, includeModules, excludeFields, excludeModules);
		
		List<String> lines = mo.generateOverrides();
		
	    Path outFilePath = new File("./output/overrides.properties").toPath();
	    
	    Files.write(outFilePath, lines);
	    
	    System.out.println("Catalogued " + lines.size() + " parameters.");
	    System.out.println("Done.");
	}
	
	private static List<String> argToList(String arg, List<String> defaults) {
		if (arg == null || arg.isEmpty()) {
			return defaults;
		}
		
		List<String> list = new LinkedList<>();
		
		list.addAll( Arrays.asList(arg.split(",")) );
		list.replaceAll(s -> s.trim());
		
		return list;
	}
	
	public ModuleOverrides(List<String> includeFields, 
			List<String> includeModulesList, 
			List<String> excludeFields,
			List<String> excludeModulesList) {
		this.includeFields = includeFields;
		this.excludeFields = excludeFields;
		
		if (includeModules != null) {
			this.includeModules = new WildcardFileFilter(includeModulesList, IOCase.INSENSITIVE);
		}
		if (excludeModules != null) {
			this.excludeModules = new WildcardFileFilter(excludeModulesList, IOCase.INSENSITIVE);
		}
	}
	
	public List<String> generateOverrides() throws Exception {
		List<String> lines = new LinkedList<>();
		
		Utilities.walkAllModules((basePath, modulePath) -> processModule(basePath, modulePath, lines));
	    
	    return lines;
	}
	
	private void processModule(Path modulesPath, Path modulePath, List<String> lines) {
		String moduleFilename = modulesPath.relativize(modulePath).toString();

		if ((includeModules != null && !includeModules.accept(null, moduleFilename))
				|| (excludeModules != null && excludeModules.accept(null, moduleFilename))) {
			return;
		}
		
		try (JsonReader reader = new JsonReader(new FileReader(modulePath.toString()))) {
			JsonObject module = new JsonParser().parse(reader).getAsJsonObject();

			String lineStart = moduleFilename + "\\:\\:$";
			lines.addAll(handleElement(lineStart, "$", module));
		} catch (IOException e) {
			throw new RuntimeException("Unable to read modules", e);
		}
	}
	
	private List<String> handleElement(String path, String currentElementName, JsonElement element) {
		// do a depth-first search, add things that are numbers
		List<String> parameters = new LinkedList<>();
		
		if (element.isJsonArray()) {
			JsonArray ja = element.getAsJsonArray();
			for (int i = 0 ; i < ja.size() ; i++) {
				JsonElement fieldValue = ja.get(i);
				parameters.addAll( handleElement(path + "[" + i + "]", currentElementName, fieldValue) );
			}
		} else if (element.isJsonObject()) {
			JsonObject jo = element.getAsJsonObject();
			
			for (String field : jo.keySet()) {
				String safeFieldName = field.replace(" ", "\\ "); // have to escape spaces in properties file
				JsonElement fieldValue = jo.get(field);
				parameters.addAll( handleElement(path + "['" + safeFieldName + "']", field, fieldValue) );
			}
			
		} else if (element.isJsonPrimitive()) {
			JsonPrimitive jp = element.getAsJsonPrimitive();
			if (jp.isNumber()) {
				if ((includeFields != null && includeFields.contains(currentElementName))
						|| (excludeFields != null && !excludeFields.contains(currentElementName))) {
					String newParam = path + " = " + jp.getAsString();
					parameters.add(newParam);	
				}
			}
		}
		
		return parameters;
	}
}
