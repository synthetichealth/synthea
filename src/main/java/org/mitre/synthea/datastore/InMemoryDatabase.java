package org.mitre.synthea.datastore;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import org.mitre.synthea.modules.HealthRecord;
import org.mitre.synthea.modules.HealthRecord.CarePlan;
import org.mitre.synthea.modules.HealthRecord.Code;
import org.mitre.synthea.modules.HealthRecord.Encounter;
import org.mitre.synthea.modules.HealthRecord.Medication;
import org.mitre.synthea.modules.HealthRecord.Observation;
import org.mitre.synthea.modules.HealthRecord.Procedure;
import org.mitre.synthea.modules.HealthRecord.Report;
import org.mitre.synthea.modules.Person;

/**
 * In-memory database, intended to be the primary repository for Synthea data as it runs.
 * Eventually this should be augmented to be more generic (possibly using an ORM?),
 * and allow for the (optional) use of a persistent database.
 */
public class InMemoryDatabase
{
	private static InMemoryDatabase INSTANCE;
	
	static
	{
		try {
			Class.forName("org.h2.Driver");
		} catch (ClassNotFoundException e) {
			throw new Error(e);
		}
	}
	
	public static InMemoryDatabase getInstance()
	{
		if (INSTANCE == null)
		{
			INSTANCE = new InMemoryDatabase();
		}
		
		return INSTANCE;
	}	
	
	private InMemoryDatabase() 
	{
		try(Connection connection = getConnection()) 
		{
			// TODO all of this needs to be done generically, ORM?
			// but this is faster in the short term
			// in the long term I want more standardized schemas	
			
			connection.prepareStatement("CREATE TABLE PERSON (id varchar, name varchar, birthdate bigint)").execute();
			connection.prepareStatement("create table attribute (person_id varchar, name varchar, value varchar)").execute();
			
			connection.prepareStatement("create table provider (id varchar, name varchar, birthdate bigint, is_chw boolean)").execute();
			
			connection.prepareStatement("create table provider_attribute (provider_id varchar, name varchar, value varchar)").execute();
			
			connection.prepareStatement("create table encounter (id varchar, person_id varchar, provider_id varchar, name varchar, type varchar, start bigint, stop bigint, code varchar, display varchar, system varchar)").execute();
			
			// are conditions needed in phase 1?
			connection.prepareStatement("create table condition (person_id varchar, name varchar, type varchar, start bigint, stop bigint, code varchar, display varchar, system varchar)").execute();
			
			
			connection.prepareStatement("create table medication (person_id varchar, name varchar, type varchar, start bigint, stop bigint, code varchar, display varchar, system varchar)").execute();
			
			
			connection.prepareStatement("create table procedure (person_id varchar, encounter_id varchar, name varchar, type varchar, start bigint, stop bigint, code varchar, display varchar, system varchar)").execute();
			
			connection.prepareStatement("create table observation (person_id varchar, encounter_id varchar, name varchar, type varchar, start bigint, value varchar, unit varchar, code varchar, display varchar, system varchar)").execute();
			
			// TODO diagnostic reports
			
			// TODO - special case here, would like to refactor. maybe make all attributes time-based?
			connection.prepareStatement("create table quality_of_life (person_id varchar, year int, value double)").execute();
			
			connection.commit();
			
		} catch (SQLException e) 
		{
			e.printStackTrace();
		}
	} 
	
	public Connection getConnection() throws SQLException
	{
		// DB_CLOSE_DELAY=-1 maintains the DB in memory after all connections closed
		// (so that we don't lose everything between 1 connection closing and the next being opened)
		return DriverManager.getConnection("jdbc:h2:mem:synthea;DB_CLOSE_DELAY=-1");
	}
	
	public boolean store(Person p) 
	{
		String personID = (String) p.attributes.get(Person.ID);
		
		try ( Connection connection = getConnection() )
		{	
			connection.setAutoCommit(false);  
			PreparedStatement stmt = connection.prepareStatement("INSERT INTO PERSON (id, name, birthdate) VALUES (?,?,?);");
			
			stmt.setString(1, personID);
			stmt.setString(2, (String)p.attributes.get(Person.NAME));
			stmt.setLong(3, (long)p.attributes.get(Person.BIRTHDATE));
			
			stmt.execute();
			
			stmt = connection.prepareStatement("INSERT INTO ATTRIBUTE (person_id, name, value) VALUES (?,?,?);");            
			for (Map.Entry<String,Object> attr : p.attributes.entrySet()) {
				stmt.setString(1, personID);
				stmt.setString(2, attr.getKey() );
				stmt.setString(3, String.valueOf(attr.getValue()) );
				stmt.addBatch();
			}
			stmt.executeBatch();
			
			for (Encounter encounter : p.record.encounters)
			{
				String encounterID = UUID.randomUUID().toString();
				
				// TODO insert encounter
				//table encounter (id varchar, person_id varchar, provider_id varchar, name varchar, type varchar, start bigint, stop bigint, code varchar, display varchar, system varchar)").execute();
				
				stmt = connection.prepareStatement("INSERT INTO ENCOUNTER (id, person_id, provider_id, name, type, start, stop, code, display, system) VALUES (?,?,?,?,?,?,?,?,?,?);");  
				stmt.setString(1, encounterID);
				stmt.setString(2, personID);
				if (encounter.provider == null)
				{
					stmt.setString(3, null);
				} else
				{
					stmt.setString(3, encounter.provider.getResourceID());
				}
				stmt.setString(4, encounter.name);
				stmt.setString(5, encounter.type);
				stmt.setLong(6, encounter.start);
				stmt.setLong(7, encounter.stop);
				if (encounter.codes.isEmpty())
				{
					stmt.setString(8, null);
					stmt.setString(9, null);
					stmt.setString(10, null);
				} else 
				{
					Code code = encounter.codes.get(0);
					stmt.setString(8, code.code);
					stmt.setString(9, code.display);
					stmt.setString(10, code.system);
				}
				stmt.execute();
				
				for (HealthRecord.Entry condition : encounter.conditions)
				{
					// 			connection.prepareStatement("create table condition (person_id varchar, name varchar, type varchar, start bigint, stop bigint, code varchar, display varchar, system varchar)").execute();
					stmt = connection.prepareStatement("INSERT INTO CONDITION (person_id, name, type, start, stop, code, display, system) VALUES (?,?,?,?,?,?,?,?);");  
					stmt.setString(1, personID);
					stmt.setString(2, condition.name);
					stmt.setString(3, condition.type);
					stmt.setLong(4, condition.start);
					stmt.setLong(5, condition.stop);
					if (condition.codes.isEmpty())
					{
						stmt.setString(6, null);
						stmt.setString(7, null);
						stmt.setString(8, null);
					} else
					{
						Code code = condition.codes.get(0);
						stmt.setString(6, code.code);
						stmt.setString(7, code.display);
						stmt.setString(8, code.system);
					}
					stmt.execute();
				}
				
				for (Observation observation : encounter.observations)
				{
					// 			connection.prepareStatement("create table observation (person_id varchar, encounter_id varchar, name varchar, type varchar, start bigint, value varchar, unit varchar, code varchar, display varchar, system varchar)").execute();
					stmt = connection.prepareStatement("INSERT INTO OBSERVATION (person_id, encounter_id, name, type, start, value, unit, code, display, system) VALUES (?,?,?,?,?,?,?,?,?,?);");  
					stmt.setString(1, personID);
					stmt.setString(2, encounterID);
					stmt.setString(3, observation.name);
					stmt.setString(4, observation.type);
					stmt.setLong(5, observation.start);
					stmt.setString(6, String.valueOf(observation.value));
					stmt.setString(7, observation.unit);
					if (observation.codes.isEmpty())
					{
						stmt.setString(8, null);
						stmt.setString(9, null);
						stmt.setString(10, null);
					} else
					{
						Code code = observation.codes.get(0);
						stmt.setString(8, code.code);
						stmt.setString(9, code.display);
						stmt.setString(10, code.system);
					}

					stmt.execute();
				}
				
				for (Procedure procedure : encounter.procedures)
				{
					// 			connection.prepareStatement("create table procedure (person_id varchar, encounter_id varchar, name varchar, type varchar, start bigint, stop bigint, code varchar, display varchar, system varchar)").execute();
					stmt = connection.prepareStatement("INSERT INTO PROCEDURE (person_id, encounter_id, name, type, start, stop, code, display, system) VALUES (?,?,?,?,?,?,?,?,?);");  
					stmt.setString(1, personID);
					stmt.setString(2, encounterID);

					stmt.setString(3, procedure.name);
					stmt.setString(4, procedure.type);
					stmt.setLong(5, procedure.start);
					stmt.setLong(6, procedure.stop);
					if (procedure.codes.isEmpty())
					{
						stmt.setString(7, null);
						stmt.setString(8, null);
						stmt.setString(9, null);
					} else
					{
						Code code = procedure.codes.get(0);
						stmt.setString(7, code.code);
						stmt.setString(8, code.display);
						stmt.setString(9, code.system);
					}

					stmt.execute();
				}
				
				for (Medication medication : encounter.medications)
				{
					// create table medication (person_id varchar, name varchar, type varchar, start bigint, stop bigint, code varchar, display varchar, system varchar)
					stmt = connection.prepareStatement("INSERT INTO MEDICATION (person_id, name, type, start, stop, code, display, system) VALUES (?,?,?,?,?,?,?,?);");  
					stmt.setString(1, personID);
					stmt.setString(2, medication.name);
					stmt.setString(3, medication.type);
					stmt.setLong(4, medication.start);
					stmt.setLong(5, medication.stop);
					if (medication.codes.isEmpty())
					{
						stmt.setString(6, null);
						stmt.setString(7, null);
						stmt.setString(8, null);
					} else
					{
						Code code = medication.codes.get(0);
						stmt.setString(6, code.code);
						stmt.setString(7, code.display);
						stmt.setString(8, code.system);
					}
					stmt.execute();
				}
				
				for (HealthRecord.Entry immunization : encounter.immunizations)
				{
					// TODO insert immunization - ignored in phase 1
				}
				
				for (Report report : encounter.reports)
				{
					// TODO insert report
				}
				
				for (CarePlan careplan : encounter.careplans)
				{
					// TODO insert careplan - ignored in phase 1
				}
			}
			
			Map<Integer,Double> qols = (Map<Integer,Double>)p.attributes.get("QOL");
			
			if (qols != null)
			{
				// TODO - would rather have something more generic
				stmt = connection.prepareStatement("INSERT INTO QUALITY_OF_LIFE (person_id, year, value) VALUES (?,?,?);");     
				
				for (Map.Entry<Integer,Double> attr : qols.entrySet()) 
				{
					stmt.setString(1, personID);
					stmt.setInt(2, attr.getKey() );
					stmt.setDouble(3, attr.getValue() );
					stmt.addBatch();
				}
				stmt.executeBatch();
			}
			
			connection.commit(); 
			return true;
		} catch (SQLException e) 
		{			
			e.printStackTrace();
			return false;	
		}
	}
}
