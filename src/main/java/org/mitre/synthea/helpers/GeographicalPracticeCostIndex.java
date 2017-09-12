package org.mitre.synthea.helpers;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

public class GeographicalPracticeCostIndex {
	
	private String carrier;
	private float localityNum;
	private String localityName;
	private float pwGpci2016;
	private float peGpci2016;
	private float mpGpci2016;
	private float pwGpci2017;
	private float peGpci2017;
	private float mpGpci2017;
	private float pwGpci2018;
	private float peGpci2018;
	private float mpGpci2018;

	private static HashMap<String, GeographicalPracticeCostIndex> gpciHash = 
			new HashMap<String,GeographicalPracticeCostIndex>();
		

	public GeographicalPracticeCostIndex(LinkedTreeMap m){
		carrier = (String) m.get("carrier");
		try{
			localityNum = Float.parseFloat((String) m.get("locality_number"));
		}catch(Exception e){
			System.out.println(m);
		}
		localityName = (String) m.get("locality_name");
		pwGpci2016 = Float.parseFloat((String) m.get("2016_pw_gpci_with_10_floor"));
		peGpci2016 = Float.parseFloat((String) m.get("2016_pe_gpci"));
		mpGpci2016 = Float.parseFloat((String) m.get("2016_mp_gpci"));
	    pwGpci2017 = Float.parseFloat((String) m.get("2017_pw_gpci_with_10_floor"));
		peGpci2017 = Float.parseFloat((String) m.get("2017_pe_gpci"));
	    mpGpci2017 = Float.parseFloat((String) m.get("2017_mp_gpci"));
		pwGpci2018 = Float.parseFloat((String) m.get("2018_pw_gpci"));
		peGpci2018 = Float.parseFloat((String) m.get("2018_pe_gpci"));
		mpGpci2018 = Float.parseFloat((String) m.get("2018_mp_gpci"));
	}
	
	public static void clear(){
		gpciHash.clear();
	}
	
	public static void loadGpciData(){
		
		String filename = "/geographical_practice_cost_index.json";
		try{
			InputStream stream = GeographicalPracticeCostIndex.class.getResourceAsStream(filename);
			//read all text into a string
			String json = new BufferedReader(new InputStreamReader(stream)).lines()
					.parallel().collect(Collectors.joining("\n"));
			Gson g = new Gson();
			//gpciHash = g.fromJson(json, HashMap.class);
			LinkedTreeMap<String, LinkedTreeMap> gson = g.fromJson(json, LinkedTreeMap.class);
			for(Entry<String, LinkedTreeMap> entry : gson.entrySet()) {
				String locName = entry.getKey();
				LinkedTreeMap value = entry.getValue();
				GeographicalPracticeCostIndex gpci = new GeographicalPracticeCostIndex(value);
				gpciHash.put(locName,gpci);
			}
			//LinkedTreeMap gpci = gson.get("REST OF MASSACHUSETTS");
		}catch(Exception e) {
			e.printStackTrace();
			throw new ExceptionInInitializerError(e);
		}
		
//		//create TreeMap for now
//		LinkedTreeMap map = new LinkedTreeMap();
//		LinkedTreeMap values = new LinkedTreeMap();
//		values.put("2017_pe_gpci", 1.067);
//		values.put("2017_pw_gpci_with_10_floor", 1.019);
//		values.put("2017_mp_gpci", 0.839);
//		values.put("locality_number", 99);
//		map.put("REST OF MASSACHUSETTS",values);
//		GeographicalPracticeCostIndex gpci = new GeographicalPracticeCostIndex(values);
//		gpciHash.put("REST OF MASSACHUSETTS", gpci);
	}
	
	public static HashMap<String,GeographicalPracticeCostIndex> getGpciHash(){
		
		return gpciHash;
	}
	
	public static GeographicalPracticeCostIndex getGpci(String localityName){
		
		return gpciHash.get(localityName);
	}
	
	public String getCarrier(){
		return carrier;
	}
	
	public float returnLocalityNum(){
		return localityNum;
	}
	
	public String returnLocalityName(){
		return localityName;
	}
	
	public double pwGpci2016(){
		return pwGpci2016;
	}
	
	public double peGpci2016(){
		return peGpci2016;
	}
	
	public double mpGpci2016(){
		return mpGpci2016;
	}
	
	public double getPwGpci2017() {
		return pwGpci2017;
	}

	public double getPeGpci2017() {
		return peGpci2017;
	}

	public double getMpGpci2017() {
		return mpGpci2017;
	}

	public double getPwGpci2018() {
		return pwGpci2018;
	}

	public double getPeGpci2018() {
		return peGpci2018;
	}

	public double getMpGpci2018() {
		return mpGpci2018;
	}
}
