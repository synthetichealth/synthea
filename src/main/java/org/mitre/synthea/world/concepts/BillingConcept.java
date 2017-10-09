package org.mitre.synthea.world.concepts;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.google.gson.Gson;

public class BillingConcept 
{
	//HashMap of all codes in synthea 
	private static HashMap<String, BillingConcept> conceptHash = new HashMap<String,BillingConcept>();
	
	private String type;
	private String hcpc;
	private String description;
	private String icd;
	private String cost;
	
	public BillingConcept(String type, Map<String, ?> p) {
		this.type = type;
		hcpc = (String) p.get("hcpc");
		description = (String) p.get("description");
		icd = (String) p.get("icd-10");
		cost = (String) p.get("cost");
	}

	public static void loadConceptMappings() {
		
		String filename = "/concept_mappings.json";
		try {
			InputStream stream = BillingConcept.class.getResourceAsStream(filename);
			//read all text into a string
			String json = new BufferedReader(new InputStreamReader(stream)).lines()
					.parallel().collect(Collectors.joining("\n"));
			Gson g = new Gson(); 
			HashMap<String, Map<String,?>> gson = g.fromJson(json, HashMap.class);
			for(Entry<String, ?> entry : gson.entrySet()) {
				String type = entry.getKey();
				Map<String,?> value = (Map<String,?>)entry.getValue(); 
				for(Map.Entry<String,?> o : value.entrySet()){
					String syntheaCode = o.getKey();
					Map<String,?> conceptInfo = (Map<String,?>)o.getValue();
					BillingConcept cncpt = new BillingConcept(type,conceptInfo);
					conceptHash.put(syntheaCode,cncpt);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new ExceptionInInitializerError(e);
		}	
	}
	
	public static BillingConcept getConcept(String code)
	{	
		return conceptHash.get(code);
	}
	
	public String getHcpcCode(){
		
		return hcpc;
	}
	
	public String getDescription()
	{	
		return description;
	}
	
	public String getICDCode()
	{	
		return icd;
	}
	
	public String getCost()
	{	
		return cost;
	}
  }
