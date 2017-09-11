package org.mitre.synthea.world;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

public class BillingConcept {
	
	//HashMap of all codes in synthea 
	private static HashMap<String, BillingConcept> conceptHash = new HashMap<String,BillingConcept>();
	
	private String hcpc;
	private String description;
	private String icd;
	private String cost;
	
	public BillingConcept(LinkedTreeMap p) {
		if(p.get("hcpc") != null)
			hcpc = (String) p.get("hcpc");
		description = (String) p.get("description");
		if(p.get("icd-10") != null)
			icd = (String) p.get("icd-10");
		if(p.get("cost") != null)
			cost = (String) p.get("cost");
	}
	
	public static void clear(){
		conceptHash.clear();
	}
	
	public static void loadConceptMappings() {
		
		String filename = "/concept_mappings.json";
		try {
			InputStream stream = BillingConcept.class.getResourceAsStream(filename);
			//read all text into a string
			String json = new BufferedReader(new InputStreamReader(stream)).lines()
					.parallel().collect(Collectors.joining("\n"));
			Gson g = new Gson(); 
			HashMap<String, LinkedTreeMap> gson = g.fromJson(json, HashMap.class);
			for(Entry<String, LinkedTreeMap> entry : gson.entrySet()) {
				LinkedTreeMap value = entry.getValue(); 
				for(Object o : value.entrySet()){
					String syntheaCode = ((Entry<String, LinkedTreeMap>) o).getKey();
					LinkedTreeMap conceptInfo = ((Entry<String, LinkedTreeMap>) o).getValue();
					BillingConcept cncpt = new BillingConcept(conceptInfo);
					conceptHash.put(syntheaCode,cncpt);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new ExceptionInInitializerError(e);
		}	
	}

	public static HashMap<String,BillingConcept> getConceptHash(){
		
		return conceptHash;
	}  
	
	public static BillingConcept getConcept(String code){
		
		return conceptHash.get(code);
	}
	
	public String getHcpcCode(){
		
		return hcpc;
	}
	
	public String getDescription(){
		
		return description;
	}
	
	public String getICDCode(){
		
		return icd;
	}
	
	public String getCost(){
		
		return cost;
	}
  }
