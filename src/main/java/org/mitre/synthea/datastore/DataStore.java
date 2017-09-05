package org.mitre.synthea.datastore;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.mitre.synthea.modules.CommunityHealthWorker;
import org.mitre.synthea.modules.HealthRecord;
import org.mitre.synthea.modules.HealthRecord.CarePlan;
import org.mitre.synthea.modules.HealthRecord.Code;
import org.mitre.synthea.modules.HealthRecord.Encounter;
import org.mitre.synthea.modules.HealthRecord.Medication;
import org.mitre.synthea.modules.HealthRecord.Observation;
import org.mitre.synthea.modules.HealthRecord.Procedure;
import org.mitre.synthea.modules.HealthRecord.Report;
import org.mitre.synthea.modules.Person;
import org.mitre.synthea.world.Provider;

import com.google.gson.internal.LinkedTreeMap;

/**
 * In-memory database, intended to be the primary repository for Synthea data as it runs.
 * Eventually this should be augmented to be more generic (possibly using an ORM?),
 * and allow for the (optional) use of a persistent database.
 */
public class DataStore
{	
	// DB_CLOSE_DELAY=-1 maintains the DB in memory after all connections closed
	// (so that we don't lose everything between 1 connection closing and the next being opened)
	private static final String JDBC_OPTIONS = "DB_CLOSE_DELAY=-1";
	private static final String IN_MEMORY_JDBC_STRING = "jdbc:h2:mem:synthea;"+JDBC_OPTIONS;
	private static final String FILEBASED_JDBC_STRING = "jdbc:h2:~/synthea_java/database;"+JDBC_OPTIONS;
	
	static
	{
		try {
			Class.forName("org.h2.Driver");
		} catch (ClassNotFoundException e) {
			throw new Error(e);
		}
	}

	/**
	 * Flag for whether the DB uses a file (true) or is in-memory only (false).
	 */
	private boolean fileBased;
	
	public DataStore(boolean fileBased) 
	{
		this.fileBased = fileBased;
		try(Connection connection = getConnection()) 
		{
			// TODO all of this needs to be done generically, ORM?
			// but this is faster in the short term
			// in the long term I want more standardized schemas
			
			connection.prepareStatement("CREATE TABLE IF NOT EXISTS PERSON (id varchar, name varchar, birthdate bigint)").execute();
			connection.prepareStatement("CREATE TABLE IF NOT EXISTS attribute (person_id varchar, name varchar, value varchar)").execute();
			
			connection.prepareStatement("CREATE TABLE IF NOT EXISTS provider (id varchar, name varchar, is_chw boolean)").execute();
			
			connection.prepareStatement("CREATE TABLE IF NOT EXISTS provider_attribute (provider_id varchar, name varchar, value varchar)").execute();
			
			connection.prepareStatement("CREATE TABLE IF NOT EXISTS encounter (id varchar, person_id varchar, provider_id varchar, name varchar, type varchar, start bigint, stop bigint, code varchar, display varchar, system varchar)").execute();
			
			connection.prepareStatement("CREATE TABLE IF NOT EXISTS condition (person_id varchar, name varchar, type varchar, start bigint, stop bigint, code varchar, display varchar, system varchar)").execute();
			
			connection.prepareStatement("CREATE TABLE IF NOT EXISTS medication (person_id varchar, name varchar, type varchar, start bigint, stop bigint, code varchar, display varchar, system varchar)").execute();
			
			connection.prepareStatement("CREATE TABLE IF NOT EXISTS procedure (person_id varchar, encounter_id varchar, name varchar, type varchar, start bigint, stop bigint, code varchar, display varchar, system varchar)").execute();
			
			connection.prepareStatement("CREATE TABLE IF NOT EXISTS observation (person_id varchar, encounter_id varchar, name varchar, type varchar, start bigint, value varchar, unit varchar, code varchar, display varchar, system varchar)").execute();
			
			// TODO diagnostic reports, immunizations
			
			connection.prepareStatement("CREATE TABLE IF NOT EXISTS immunization (person_id varchar, encounter_id varchar, name varchar, type varchar, start bigint, code varchar, display varchar, system varchar)").execute();
			
			// TODO - special case here, would like to refactor. maybe make all attributes time-based?
			connection.prepareStatement("CREATE TABLE IF NOT EXISTS quality_of_life (person_id varchar, year int, value double)").execute();
			
			connection.commit();
			
		} catch (SQLException e) 
		{
			e.printStackTrace();
		}
	} 
	
	public Connection getConnection() throws SQLException
	{
		Connection connection = DriverManager.getConnection( this.fileBased ? FILEBASED_JDBC_STRING : IN_MEMORY_JDBC_STRING);
		connection.setAutoCommit(false);
		return connection;
	}
	
	public boolean store(Person p) 
	{
		String personID = (String) p.attributes.get(Person.ID);
		
		try ( Connection connection = getConnection() )
		{
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
				if (encounter.provider == null && encounter.chw == null)
				{
					stmt.setString(3, null);
				} else if (encounter.provider != null)
				{
					stmt.setString(3, encounter.provider.getResourceID());
				} else if (encounter.chw != null)
				{
					stmt.setString(3, (String) encounter.chw.services.get("resourceID"));
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
					// "CREATE TABLE IF NOT EXISTS immunization (person_id varchar, encounter_id varchar, name varchar, type varchar, start bigint, code varchar, display varchar, system varchar)"
					stmt = connection.prepareStatement("INSERT INTO IMMUNIZATION (person_id, encounter_id, name, type, start, code, display, system) VALUES (?,?,?,?,?,?,?,?);");  
					stmt.setString(1, personID);
					stmt.setString(2, encounterID);
					stmt.setString(3, immunization.name);
					stmt.setString(4, immunization.type);
					stmt.setLong(5, immunization.start);
					if (immunization.codes.isEmpty())
					{
						stmt.setString(6, null);
						stmt.setString(7, null);
						stmt.setString(8, null);
					} else
					{
						Code code = immunization.codes.get(0);
						stmt.setString(6, code.code);
						stmt.setString(7, code.display);
						stmt.setString(8, code.system);
					}
					stmt.execute();
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
	
	public boolean store(Collection<? extends Provider> providers)
	{
		try ( Connection connection = getConnection() )
		{
			// "create table provider (id varchar, name varchar, is_chw boolean)"
			PreparedStatement providerTable = connection.prepareStatement("INSERT INTO PROVIDER (id, name, is_chw) VALUES (?,?,?);");
			
			// create table provider_attribute (provider_id varchar, name varchar, value varchar)
			PreparedStatement attributeTable = connection.prepareStatement("INSERT INTO PROVIDER_ATTRIBUTE (provider_id, name, value) VALUES (?,?,?);");            
			
			for (Provider p : providers)
			{
				String providerID = p.getResourceID();
				LinkedTreeMap attributes = p.getAttributes();
				
				providerTable.setString(1, providerID);
				providerTable.setString(2, (String)attributes.get("name"));
				providerTable.setBoolean(3, false);
				providerTable.addBatch();
				
				for (Object key : attributes.keySet()) {
					attributeTable.setString(1, providerID);
					attributeTable.setString(2, (String)key );
					attributeTable.setString(3, String.valueOf(attributes.get(key)) );
					attributeTable.addBatch();
				}
			}

			providerTable.executeBatch();
			attributeTable.executeBatch();
			connection.commit();
			return true;
		}catch (SQLException e) 
		{			
			e.printStackTrace();
			return false;	
		}
	}
	
	public boolean storeCHWs(Collection<CommunityHealthWorker> chws)
	{

		try ( Connection connection = getConnection() )
		{
			// "create table provider (id varchar, name varchar, is_chw boolean)"
			PreparedStatement providerTable = connection.prepareStatement("INSERT INTO PROVIDER (id, name, is_chw) VALUES (?,?,?);");

			// create table provider_attribute (provider_id varchar, name varchar, value varchar)
			PreparedStatement attributeTable = connection.prepareStatement("INSERT INTO PROVIDER_ATTRIBUTE (provider_id, name, value) VALUES (?,?,?);");

			for (CommunityHealthWorker chw : chws)
			{
				String providerID = (String) chw.services.get("resourceID");
				Map<String, Object> attributes = chw.services;
				
				providerTable.setString(1, providerID);
				providerTable.setString(2,  "CHW providing " + attributes.get(CommunityHealthWorker.DEPLOYMENT) + " services in " + attributes.get(CommunityHealthWorker.CITY));
				providerTable.setBoolean(3, true);
				providerTable.addBatch();
				
				for (Object key : attributes.keySet()) {
					attributeTable.setString(1, providerID);
					attributeTable.setString(2, (String)key );
					attributeTable.setString(3, String.valueOf(attributes.get(key)) );
					attributeTable.addBatch();
				}
			}

			providerTable.executeBatch();
			attributeTable.executeBatch();
			connection.commit();
			return true;
		}catch (SQLException e) 
		{			
			e.printStackTrace();
			return false;	
		}
	}
	
}
