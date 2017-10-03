package org.mitre.synthea.helpers;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.mitre.synthea.datastore.DataStore;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.dataformat.csv.CsvSchema.ColumnType;


public class SimpleCSV 
{

	/**
	 * Parse the data from the given CSV file into a List of Map<String,String>, where the key is the column name. 
	 * Uses a LinkedHashMap specifically to ensure the order of columns is preserved in the resulting maps.
	 * 
	 * @param csvData Raw CSV data
	 * @return parsed data
	 * @throws IOException if any exception occurs while parsing the data
	 */
	public static List<LinkedHashMap<String,String>> parse(String csvData) throws IOException
	{
		// Read schema from the first line; start with bootstrap instance
		// to enable reading of schema from the first line
		// NOTE: reads schema and uses it for binding
		CsvMapper mapper = new CsvMapper();
		CsvSchema schema = CsvSchema.emptySchema().withHeader(); // use first row as header; otherwise defaults are fine

		MappingIterator<LinkedHashMap<String,String>> it = mapper.readerFor(LinkedHashMap.class)
		   .with(schema)
		   .readValues(csvData);

		return it.readAll();
	}
	
	/**
	 * Convert the data in the given List<Map<String,String>> to a String of CSV data.
	 * Each Map in the List represents one line of the resulting CSV.
	 * Uses the keySet from the first Map to populate the set of columns. This means that the first Map must contain all the columns desired in the final CSV.
	 * The order of the columns is specified by the order provided by the first Map's keySet, so using an ordered Map implementation 
	 * (such as LinkedHashMap) is recommended.
	 * @param data
	 * @return data formatted as a String containing raw CSV data
	 * @throws IOException
	 */
	public static String unparse(List<? extends Map<String,String>> data) throws IOException 
	{
		CsvMapper mapper = new CsvMapper();
		CsvSchema.Builder schemaBuilder = CsvSchema.builder();
		schemaBuilder.setUseHeader(true);

		Collection<String> columns = data.get(0).keySet();
		schemaBuilder.addColumns(columns, ColumnType.STRING);

		return mapper.writer( schemaBuilder.build() ).writeValueAsString(data);
	}

	public static void main(String[] args) throws Exception
    {
        DataStore db = new DataStore(true);  
        File source = new File("/Users/Seely/Desktop/prevalence_template.csv"); //for Mac
        //File source = new File("C:\\Users\\dehall\\synthea\\resources\\prevalence_template.csv"); //for Windows
        String csvData = Files.readAllLines(source.toPath()).stream().collect(Collectors.joining("\n"));
         
        List<LinkedHashMap<String,String>> data = SimpleCSV.parse(csvData);
   	
        try (Connection connection = db.getConnection()) 
  	  { 
          for (LinkedHashMap<String,String> line : data)
          {
        	  
	        	  if (line.get("ITEM").isEmpty()) 
	    		  {
	    			  continue;
	    		  }

	    	          if (!line.get("GIVEN CONDITION").isEmpty())
	    	          {
	        		  givenCondition(connection, line); 
	    	          }
	    	          else 
	    	          {
	    	        	  getPrev(connection,line);
	    	          }
	    	          getPop(connection,line);
	    	          completeSyntheaFields(connection,line);
	    	          completeDifferneceField(connection,line);
	        	  }
          
          		allConditions(connection, data);
  	  }
	        	  
	        	  catch (Exception e) 
	        	  {
	      			e.printStackTrace();
	      			throw new RuntimeException("error exporting statistics");
	      	  }

          String newCsvData = SimpleCSV.unparse(data);
          
          File outFile = new File("/Users/Seely/Desktop/prev_data"+System.currentTimeMillis()+".csv"); //for Mac 
          //File outFile = new File("C:\\Users\\dehall\\Desktop\\prev_data"+System.currentTimeMillis()+".csv"); //for Windows 
          Files.write(outFile.toPath(), Collections.singleton(newCsvData), StandardOpenOption.CREATE_NEW);
          
    }
	
	public static void getPrev(Connection connection, LinkedHashMap<String,String> line) throws Exception 
    {  
    	 		StringBuilder sb = new StringBuilder();
        		sb.append("SELECT COUNT(*) FROM PERSON p, CONDITION c, ATTRIBUTE a\n" + 
          	  		"WHERE \n" + 
          	  		"p.ID = c.PERSON_ID\n" +
          	  		"AND c.PERSON_ID = a.PERSON_ID\n" + 
          	  		"AND (c.DISPLAY = ?)\n" +
          	  		"AND (p.DATE_OF_DEATH is null)\n" +
          	  		"");
        		  
        		  if (!line.get("GENDER").equals("*")) 
        		  {
      	  			sb.append("AND (p.GENDER = ?)\n");
        		  }
        		  
        		  if (!line.get("RACE").equals("*")) 
        		  {
        			  sb.append("AND (p.RACE = ?)\n");
        		  } 
        		  
        		  if (line.get("AGE GROUP").equals("adult"))
        		  {
        			  sb.append("AND (a.NAME = 'AGE' AND CAST(a.VALUE AS INT) >= 18)\n");
        		  }
        		  if (line.get("AGE GROUP").equals("child"))
        		  {
        			  sb.append("AND (a.NAME = 'AGE' AND CAST(a.VALUE AS INT) < 18)\n");
        		  }
        		  if (line.get("AGE GROUP").equals("senior"))
        		  {
        			  sb.append("AND (a.NAME = 'AGE' AND CAST(a.VALUE AS INT) >= 65)\n");
        		  }

        		PreparedStatement stmt = connection.prepareStatement(sb.toString());
        		
        	  	stmt.setString(1, line.get("ITEM"));
        	  	
        	  	int i = 2;
        	  	
        	  	if (!line.get("GENDER").equals("*")) 
        	  	{
        	  		stmt.setString(i++, line.get("GENDER"));
        		}
        	  	
        	  	if (!line.get("RACE").equals("*")) 
        	  	{
        	  		stmt.setString(i++, line.get("RACE"));
        	  	}
        	  	
        	  	ResultSet rs = stmt.executeQuery();
        	  	
          	 rs.next(); 

      		 int countOccur = rs.getInt(1);
      		 line.put("SYNTHEA OCCURRENCES", Integer.toString(countOccur));    	 
     }

     public static void getPop(Connection connection, LinkedHashMap<String,String> line) throws Exception 
     {
        		  
    	 		StringBuilder sb = new StringBuilder();
        		sb.append("SELECT COUNT(*) FROM PERSON p, ATTRIBUTE a\n" + 
          	  		"WHERE \n" + 
          	  		"p.ID = a.PERSON_ID\n" + 
          	  		"AND (p.DATE_OF_DEATH is null)\n" +
          	  		"");
        		  
        		  if (!line.get("GENDER").equals("*")) 
        		  {
      	  			sb.append("AND (p.GENDER = ?)\n");
        		  }
        		  
        		  if (!line.get("RACE").equals("*")) 
        		  {
        			  sb.append("AND (p.RACE = ?)\n");
        		  } 
        		  if (line.get("AGE GROUP").equals("adult"))
        		  {
        			  sb.append("AND (a.NAME = 'AGE' AND CAST(a.VALUE AS INT) >= 18)\n");
        		  }
        		  if (line.get("AGE GROUP").equals("child"))
        		  {
        			  sb.append("AND (a.NAME = 'AGE' AND CAST(a.VALUE AS INT) < 18)\n");
        		  }
        		  if (line.get("AGE GROUP").equals("senior"))
        		  {
        			  sb.append("AND (a.NAME = 'AGE' AND CAST(a.VALUE AS INT) > 65)\n");
        		  }

        		 
        		  
        		PreparedStatement stmt = connection.prepareStatement(sb.toString());
        	  	
        	  	int i = 1;
        	  	
        	  	if (!line.get("GENDER").equals("*")) 
        	  	{
        	  		stmt.setString(i++, line.get("GENDER"));
        		}
        	  	
        	  	if (!line.get("RACE").equals("*")) 
        	  	{
        	  		stmt.setString(i++, line.get("RACE"));
        	  	}
        	  	
        	  	ResultSet rs = stmt.executeQuery();
        	  	
          	rs.next(); 
	
          	int countPop = rs.getInt(1);
	 
	      	line.put("SYNTHEA POPULATION", Integer.toString(countPop));
	      	
       }  
     
     public static void completeSyntheaFields(Connection connection, LinkedHashMap<String,String> line) throws Exception
     {
    	 
	    	 
	    	 if ((line.get("SYNTHEA OCCURRENCES").isEmpty()) || (line.get("SYNTHEA POPULATION").isEmpty()))
	    	 {
	    		 line.put("SYNTHEA PREVALENCE RATE", (null));
	    		 line.put("SYNTHEA PREVALENCE PERCENT", (null));
	     }
	    	
	    	 else
	    	 {	
	    		 double occurr = Double.parseDouble(line.get("SYNTHEA OCCURRENCES"));
	    		 double pop = Double.parseDouble(line.get("SYNTHEA POPULATION"));
	    		 
	    		 if (pop != 0) 
	    		 {
	    			 double prevRate = occurr/pop;
		    		 double prevPercent = prevRate * 100;
	    			 line.put("SYNTHEA PREVALENCE RATE", Double.toString(prevRate));
		    		 line.put("SYNTHEA PREVALENCE PERCENT", Double.toString(prevPercent));
	    		 }
	    		 
	    		 else
	    		 {
	    			 line.put("SYNTHEA PREVALENCE RATE", Double.toString(0));
		    		 line.put("SYNTHEA PREVALENCE PERCENT", Double.toString(0));
	    		 }
	    	 }
     }
     
     public static void completeDifferneceField(Connection connection, LinkedHashMap<String,String> line) throws Exception
     {
	    		 if (line.get("ACTUAL PREVALENCE PERCENT").isEmpty())
	    		 {
	    			 line.put("DIFFERENCE", (null));  
	    		 }

	    		 else
	    		 {
	    			 double actualPrev = Double.parseDouble(line.get("ACTUAL PREVALENCE PERCENT"));
	    			 double prevPercent = Double.parseDouble(line.get("SYNTHEA PREVALENCE PERCENT"));
	    			 double diff = (prevPercent - actualPrev);
	    			 line.put("DIFFERENCE", Double.toString(diff)); 
	    		 }
     }
     
     public static void givenCondition(Connection connection, LinkedHashMap<String,String> line) throws Exception
     {
    	 	if (!line.get("GIVEN CONDITION").isEmpty()) 
    	 	{
    	 		StringBuilder sb = new StringBuilder();
        		sb.append("SELECT COUNT(*) FROM PERSON p, CONDITION c1, CONDITION c2\n" + 
          	  		"WHERE \n" + 
          	  		"p.ID = c1.PERSON_ID\n" + 
          	  		"AND c1.PERSON_ID = c2.PERSON_ID\n" +
          	  		"AND (p.DATE_OF_DEATH is null)\n" +
          	  		"AND (c1.DISPLAY = ?)\n" +
          	  		"");
        		
        		if (!line.get("GIVEN CONDITION").isEmpty()) 
      		  {
      			  sb.append("AND (c2.DISPLAY = ?)\n");
      		  } 

        		PreparedStatement stmt = connection.prepareStatement(sb.toString());
        		
        	  	stmt.setString(1, line.get("ITEM"));
        	  	
        	  	int i = 2;
        	  	
        	  	if (!line.get("GIVEN CONDITION").isEmpty()) 
        	  	{
        	  		stmt.setString(i++, line.get("GIVEN CONDITION"));
        		}
        	  	
        	  	ResultSet rs = stmt.executeQuery();
        	  	
              	rs.next(); 
    	
              	int givenCondition = rs.getInt(1);
    	 
    	      	line.put("SYNTHEA OCCURRENCES", Integer.toString(givenCondition));
    	 	}
     }
     
     public static void allConditions(Connection connection, List<LinkedHashMap<String,String>> data) throws Exception
     {
    	 	
    	 	PreparedStatement stmt = connection.prepareStatement("select count(*) from person where person.DATE_OF_DEATH is null");
    	 	ResultSet rs = stmt.executeQuery();
    	 	rs.next();
    	 	int totalPopulation = rs.getInt(1);

    	 	stmt = connection.prepareStatement("select distinct c.display as DistinctDisplay, count(distinct c.person_id) as CountDisplay \n" + 
    	 			"from condition c, person p\n" + 
    	 			"where c.person_id = p.id\n" + 
    	 			"and p.date_of_death is null\n" + 
    	 			"group by c.display\n" + 
    	 			"order by c.display ASC");
    	 	rs = stmt.executeQuery();
    	 	while (rs.next()) 
    	 		{
                String disease = rs.getString("DistinctDisplay");
                int count = rs.getInt("CountDisplay");
                //System.out.println(disease + "\t" + count);
                LinkedHashMap<String, String> line = new LinkedHashMap<String,String>();
                line.put("ITEM", disease);
                line.put("SYNTHEA OCCURRENCES", Integer.toString(count));
                line.put("SYNTHEA POPULATION", Integer.toString(totalPopulation));
                data.add(line);
                completeSyntheaFields(connection,line);
    	 		}
     }
}
