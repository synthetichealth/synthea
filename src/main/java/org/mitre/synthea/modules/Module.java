package org.mitre.synthea.modules;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

/**
 * Module represents the entry point of a generic module.
 * 
 * The `modules` map is the static list of generic modules.
 * It is loaded once per process, and the list of modules is shared
 * between the generated population. Because we share modules across
 * the population, it is important that States are cloned before they
 * are executed. This keeps the "master" copy of the module clean.
 */
public class Module {

	private static Map<String,Module> modules = loadModules();
	
	private static Map<String,Module> loadModules() {
		Map<String,Module> retVal = new ConcurrentHashMap<String,Module>();

		retVal.put("Lifecycle", new LifecycleModule());
		retVal.put("Cardiovascular Disease", new CardiovascularDiseaseModule());

		try {
			URL modulesFolder = ClassLoader.getSystemClassLoader().getResource("modules");
			Path path = Paths.get(modulesFolder.toURI());
			Files.walk(path, Integer.MAX_VALUE)
				.filter(Files::isReadable)
				.filter(Files::isRegularFile)
			    .filter(p -> p.toString().endsWith(".json"))
				.forEach(t -> {
					try {
						Module module = loadFile(t, path);
						String relativePath = relativePath(t, path);
						retVal.put(relativePath, module);
					} catch (IOException e) {
						e.printStackTrace();
					}
				});
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.format("Loaded %d modules.\n", retVal.size());

		return retVal;
	}
	
	private static String relativePath(Path filePath, Path modulesFolder) {
		String folderString = Matcher.quoteReplacement(modulesFolder.toString() + File.separator);
		return filePath.toString().replaceFirst(folderString, "").replaceFirst(".json", "").replace("\\", "/");
	}
	
	private static Module loadFile(Path path, Path modulesFolder) throws IOException {
		System.out.format("Loading %s\n", path.toString());
		boolean submodule = !path.getParent().equals(modulesFolder);
		JsonObject object = null;
		FileReader fileReader = null;
		JsonReader reader = null;
		fileReader = new FileReader(path.toString());
		reader = new JsonReader(fileReader);
		JsonParser parser = new JsonParser();
		object = parser.parse(reader).getAsJsonObject();
		fileReader.close();
		reader.close();
		return new Module(object, submodule);
	}
	
	public static String[] getModuleNames() {
		return modules.keySet().toArray(new String[modules.size()]);
	}
	
	/**
	 * @return a list of top-level modules. Submodules are not included.
	 */
	public static List<Module> getModules() {
		List<Module> list = new ArrayList<Module>();
		modules.forEach((k, v) -> {
			if (!v.submodule) {
				list.add(v);
			}
		});
		return list;
	}
	
	/**
	 * @param path : the relative path of the module, without the root or ".json" file extension. 
	 * For example, "medications/otc_antihistamine" or "appendicitis".
	 * @return module : the given module
	 */
	public static Module getModuleByPath(String path) {
		return modules.get(path);
	}
	
	public String name;
	public boolean submodule;
	public List<String> remarks;
	private Map<String,State> states;
	
	protected Module()
	{
		// no-args constructor only allowed to be used by subclasses
	}
	
	public Module(JsonObject definition, boolean submodule) {
		name = String.format("%s Module", definition.get("name").getAsString());
		this.submodule = submodule;
		remarks = new ArrayList<String>();
		if(definition.has("remarks")) {
			for( JsonElement value : definition.get("remarks").getAsJsonArray())
			{
				remarks.add(value.getAsString());
			}			
		}
		JsonObject object = definition.get("states").getAsJsonObject();
		states = new ConcurrentHashMap<String,State>();
		object.entrySet().forEach(entry -> {
			State state = new State(name, entry.getKey(), (JsonObject) entry.getValue());
			states.put(entry.getKey(), state);
		});
	}
	
	/**
	 * Process this Module with the given Person at the specified time
	 * within the simulation.
	 * @param person : the person being simulated
	 * @param time : the date within the simulated world
	 * @return completed : whether or not this Module completed.
	 */
	@SuppressWarnings("unchecked")
	public boolean process(Person person, long time) {
		person.history = null;
		// what current state is this person in?
		if(!person.attributes.containsKey(this.name)) {
			person.history = new ArrayList<State>();
			person.history.add(initialState());
			person.attributes.put(this.name, person.history);
		}
		person.history = (ArrayList<State>) person.attributes.get(this.name);
		String activeKey = String.format("%s %s", EncounterModule.ACTIVE_WELLNESS_ENCOUNTER, this.name);
		if(person.attributes.containsKey(EncounterModule.ACTIVE_WELLNESS_ENCOUNTER)) {
			person.attributes.put(activeKey, true);
		}
		State current = person.history.get(0);
		//System.out.println("  Resuming at " + current.name);
		// process the current state,
		// looping until module is finished, 
		// probably more than one state
		String nextStateName = null;
		while(current.process(person, time)) {
			nextStateName = current.transition(person, time);
			//System.out.println("  Transitioning to " + nextStateName);
			current = states.get(nextStateName).clone(); // clone the state so we don't dirty the original
			person.history.add(0, current);
		}
		person.attributes.remove(activeKey);
		return (current.type == State.StateType.TERMINAL);
	}

	private State initialState() {
		Iterator<State> iter = states.values().iterator();
		while(iter.hasNext())
		{
			State state = iter.next();
			if(state.type == State.StateType.INITIAL)
			{
				return state.clone(); // clone the state so we don't dirty the original.
			}
		}
		return null;
	}
	
}
