package org.mitre.synthea.datastore;

import com.google.common.collect.Table;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.ImagingStudy;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;
import org.mitre.synthea.world.concepts.HealthRecord.Report;

/**
 * In-memory database, intended to be the primary repository for Synthea data as it runs. Allows for
 * the (optional) use of a persistent database. Eventually this should be augmented to be more
 * generic (possibly using an ORM?).
 */
public class DataStore {
  // DB_CLOSE_DELAY=-1 maintains the DB in memory after all connections closed
  // (so that we don't lose everything between 1 connection closing and the next being opened)
  private static final String JDBC_OPTIONS = "DB_CLOSE_DELAY=-1";
  private static final String IN_MEMORY_JDBC_STRING = "jdbc:h2:mem:synthea;" + JDBC_OPTIONS;
  private static final String FILEBASED_JDBC_STRING = "jdbc:h2:./database;" + JDBC_OPTIONS;

  static {
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

  public DataStore(boolean fileBased) {
    this.fileBased = fileBased;
    try (Connection connection = getConnection()) {
      // TODO all of this needs to be done generically, ORM?
      // but this is faster in the short term
      // in the long term I want more standardized schemas

      connection
          .prepareStatement(
              "CREATE TABLE IF NOT EXISTS PERSON "
              + "(id varchar, name varchar, date_of_birth bigint, date_of_death bigint, "
              + "race varchar, gender varchar, socioeconomic_status varchar)")
          .execute();
      connection.prepareStatement("CREATE INDEX IF NOT EXISTS PERSON_RACE ON PERSON(RACE);")
          .execute();
      connection.prepareStatement("CREATE INDEX IF NOT EXISTS PERSON_GENDER ON PERSON(GENDER);")
          .execute();

      connection
          .prepareStatement(
              "CREATE TABLE IF NOT EXISTS ATTRIBUTE "
              + "(person_id varchar, name varchar, value varchar)")
          .execute();
      connection.prepareStatement(
          "CREATE INDEX IF NOT EXISTS ATTRIBUTE_KEY ON ATTRIBUTE(PERSON_ID, NAME);").execute();

      connection
          .prepareStatement(
              "CREATE TABLE IF NOT EXISTS PROVIDER (id varchar, name varchar)")
          .execute();
      connection.prepareStatement("CREATE INDEX IF NOT EXISTS PROVIDER_ID ON PROVIDER(ID);")
          .execute();

      connection
          .prepareStatement(
              "CREATE TABLE IF NOT EXISTS PROVIDER_ATTRIBUTE "
              + "(provider_id varchar, name varchar, value varchar)")
          .execute();

      connection
          .prepareStatement(
              "CREATE TABLE IF NOT EXISTS ENCOUNTER "
              + "(id varchar, person_id varchar, provider_id varchar, name varchar, type varchar, "
              + "start bigint, stop bigint, code varchar, display varchar, system varchar)")
          .execute();
      connection.prepareStatement("CREATE INDEX IF NOT EXISTS ENCOUNTER_ID ON ENCOUNTER(ID);")
          .execute();

      connection
          .prepareStatement(
              "CREATE TABLE IF NOT EXISTS CONDITION "
              + "(person_id varchar, name varchar, type varchar, start bigint, stop bigint, "
              + "code varchar, display varchar, system varchar)")
          .execute();
      connection
          .prepareStatement(
              "CREATE INDEX IF NOT EXISTS CONDITION_PERSON ON CONDITION(PERSON_ID, TYPE);")
          .execute();

      connection
          .prepareStatement(
              "CREATE TABLE IF NOT EXISTS MEDICATION "
              + "(id varchar, person_id varchar, provider_id varchar, name varchar, type varchar, "
              + "start bigint, stop bigint, code varchar, display varchar, system varchar)")
          .execute();

      connection
          .prepareStatement(
              "CREATE TABLE IF NOT EXISTS PROCEDURE "
              + "(person_id varchar, encounter_id varchar, name varchar, type varchar, "
              + "start bigint, stop bigint, code varchar, display varchar, system varchar)")
          .execute();

      connection
          .prepareStatement(
              "CREATE TABLE IF NOT EXISTS REPORT "
              + "(id varchar, person_id varchar, encounter_id varchar, name varchar, type varchar, "
              + "start bigint, code varchar, display varchar, system varchar)")
          .execute();

      connection
          .prepareStatement(
              "CREATE TABLE IF NOT EXISTS OBSERVATION "
              + "(person_id varchar, encounter_id varchar, report_id varchar, name varchar, "
              + "type varchar, start bigint, value varchar, unit varchar, "
              + "code varchar, display varchar, system varchar)")
          .execute();

      connection
          .prepareStatement(
              "CREATE TABLE IF NOT EXISTS IMMUNIZATION "
              + "(person_id varchar, encounter_id varchar, name varchar, type varchar, "
              + "start bigint, code varchar, display varchar, system varchar)")
          .execute();

      connection
          .prepareStatement(
              "CREATE TABLE IF NOT EXISTS CAREPLAN "
              + "(id varchar, person_id varchar, provider_id varchar, name varchar, type varchar, "
              + "start bigint, stop bigint, code varchar, display varchar, system varchar)")
          .execute();

      connection
          .prepareStatement(
              "CREATE TABLE IF NOT EXISTS IMAGING_STUDY "
              + "(id varchar, uid varchar, person_id varchar, encounter_id varchar, start bigint, "
              + "modality_code varchar, modality_display varchar, modality_system varchar, "
              + "bodysite_code varchar, bodysite_display varchar, bodysite_system varchar, "
              + "sop_class varchar)")
          .execute();

      connection
          .prepareStatement(
              "CREATE TABLE IF NOT EXISTS CLAIM "
              + "(id varchar, person_id varchar, encounter_id varchar, medication_id varchar, "
              + "time bigint, cost decimal)")
          .execute();
      connection.prepareStatement("CREATE INDEX IF NOT EXISTS CLAIM_ID ON CLAIM(ID);").execute();

      connection
          .prepareStatement(
              "CREATE TABLE IF NOT EXISTS COVERAGE (person_id varchar, year int, category varchar)")
          .execute();

      // TODO - special case here, would like to refactor. maybe make all attributes time-based?
      connection
          .prepareStatement(
              "CREATE TABLE IF NOT EXISTS QUALITY_OF_LIFE "
              + "(person_id varchar, year int, qol double, qaly double, daly double)")
          .execute();

      connection
          .prepareStatement(
              "CREATE TABLE IF NOT EXISTS UTILIZATION "
              + "(provider_id varchar, year int, encounters int, procedures int, "
              + "labs int, prescriptions int)")
          .execute();

      connection
          .prepareStatement(
              "CREATE TABLE IF NOT EXISTS UTILIZATION_DETAIL "
              + "(provider_id varchar, year int, category varchar, value int)")
          .execute();

      connection.commit();

    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  public Connection getConnection() throws SQLException {
    Connection connection = DriverManager
        .getConnection(this.fileBased ? FILEBASED_JDBC_STRING : IN_MEMORY_JDBC_STRING);
    connection.setAutoCommit(false);
    return connection;
  }

  @SuppressWarnings("unchecked")
  public boolean store(Person p) {
    String personID = (String) p.attributes.get(Person.ID);

    try (Connection connection = getConnection()) {
      // CREATE TABLE IF NOT EXISTS PERSON (id varchar, name varchar, date_of_birth bigint,
      // date_of_death bigint, race varchar, gender varchar, socioeconomic_status varchar)
      PreparedStatement stmt = connection.prepareStatement(
          "INSERT INTO PERSON "
          + "(id, name, date_of_birth, date_of_death, race, gender, socioeconomic_status) "
          + "VALUES (?,?,?,?,?,?,?);");

      stmt.setString(1, personID);
      stmt.setString(2, (String) p.attributes.get(Person.NAME));
      stmt.setLong(3, (long) p.attributes.get(Person.BIRTHDATE));
      if (p.record.death == null) {
        stmt.setObject(4, null);
      } else {
        stmt.setLong(4, p.record.death);
      }

      stmt.setString(5, (String) p.attributes.get(Person.RACE));
      stmt.setString(6, (String) p.attributes.get(Person.GENDER));
      stmt.setString(7, (String) p.attributes.get(Person.SOCIOECONOMIC_CATEGORY));

      stmt.execute();

      // CREATE TABLE IF NOT EXISTS ATTRIBUTE (person_id varchar, name varchar, value varchar)
      stmt = connection
          .prepareStatement("INSERT INTO ATTRIBUTE (person_id, name, value) VALUES (?,?,?);");
      for (Map.Entry<String, Object> attr : p.attributes.entrySet()) {
        stmt.setString(1, personID);
        stmt.setString(2, attr.getKey());
        stmt.setString(3, String.valueOf(attr.getValue()));
        stmt.addBatch();
      }
      stmt.executeBatch();

      // Add coverage to database
      stmt = connection
          .prepareStatement("INSERT INTO COVERAGE (person_id, year, category) VALUES (?,?,?);");
      List<String> coverage = (List<String>) p.attributes.get(HealthInsuranceModule.INSURANCE);
      long birthdate = (long) p.attributes.get(Person.BIRTHDATE);
      int birthYear = Utilities.getYear(birthdate);
      for (int i = 0; i < coverage.size(); i++) {
        String category = coverage.get(i);
        if (category == null) {
          break;
        } else {
          stmt.setString(1, personID);
          stmt.setInt(2, (birthYear + i));
          stmt.setString(3, category);
          stmt.addBatch();
        }
      }
      stmt.executeBatch();

      for (Encounter encounter : p.record.encounters) {
        String encounterID = UUID.randomUUID().toString();

        String providerID = null;

        if (encounter.provider != null) {
          providerID = encounter.provider.getResourceID();
        }

        // CREATE TABLE IF NOT EXISTS ENCOUNTER (id varchar, person_id varchar, provider_id varchar,
        // name varchar, type varchar, start bigint, stop bigint, code varchar, display varchar,
        // system varchar)
        stmt = connection.prepareStatement(
            "INSERT INTO ENCOUNTER "
            + "(id, person_id, provider_id, name, type, start, stop, code, display, system) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?);");
        stmt.setString(1, encounterID);
        stmt.setString(2, personID);
        stmt.setString(3, providerID);
        stmt.setString(4, encounter.name);
        stmt.setString(5, encounter.type);
        stmt.setLong(6, encounter.start);
        stmt.setLong(7, encounter.stop);
        if (encounter.codes.isEmpty()) {
          stmt.setString(8, null);
          stmt.setString(9, null);
          stmt.setString(10, null);
        } else {
          Code code = encounter.codes.get(0);
          stmt.setString(8, code.code);
          stmt.setString(9, code.display);
          stmt.setString(10, code.system);
        }
        stmt.execute();

        for (HealthRecord.Entry condition : encounter.conditions) {
          // CREATE TABLE IF NOT EXISTS CONDITION (person_id varchar, name varchar, type varchar,
          // start bigint, stop bigint, code varchar, display varchar, system varchar)
          stmt = connection.prepareStatement(
              "INSERT INTO CONDITION "
              + "(person_id, name, type, start, stop, code, display, system) "
              + "VALUES (?,?,?,?,?,?,?,?);");
          stmt.setString(1, personID);
          stmt.setString(2, condition.name);
          stmt.setString(3, condition.type);
          stmt.setLong(4, condition.start);
          stmt.setLong(5, condition.stop);
          if (condition.codes.isEmpty()) {
            stmt.setString(6, null);
            stmt.setString(7, null);
            stmt.setString(8, null);
          } else {
            Code code = condition.codes.get(0);
            stmt.setString(6, code.code);
            stmt.setString(7, code.display);
            stmt.setString(8, code.system);
          }
          stmt.execute();
        }

        for (Report report : encounter.reports) {
          String reportID = UUID.randomUUID().toString();

          // CREATE TABLE IF NOT EXISTS REPORT (id varchar, person_id varchar, encounter_id varchar,
          // name varchar, type varchar, start bigint, code varchar, display varchar, system
          // varchar)
          stmt = connection.prepareStatement(
              "INSERT INTO report "
              + "(id, person_id, encounter_id, name, type, start, code, display, system) "
              + "VALUES (?,?,?,?,?,?,?,?,?);");
          stmt.setString(1, personID);
          stmt.setString(2, encounterID);
          stmt.setString(3, reportID);
          stmt.setString(4, report.name);
          stmt.setString(5, report.type);
          stmt.setLong(6, report.start);
          if (report.codes.isEmpty()) {
            stmt.setString(7, null);
            stmt.setString(8, null);
            stmt.setString(9, null);
          } else {
            Code code = report.codes.get(0);
            stmt.setString(7, code.code);
            stmt.setString(8, code.display);
            stmt.setString(9, code.system);
          }

          stmt.execute();

          for (Observation observation : report.observations) {
            // CREATE TABLE IF NOT EXISTS OBSERVATION (person_id varchar, encounter_id varchar,
            // report_id varchar, name varchar, type varchar, start bigint, value varchar, unit
            // varchar, code varchar, display varchar, system varchar)
            stmt = connection.prepareStatement(
                "INSERT INTO OBSERVATION "
                + "(person_id, encounter_id, report_id, name, type, start, value, unit, "
                + "code, display, system) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?);");
            stmt.setString(1, personID);
            stmt.setString(2, encounterID);
            stmt.setString(3, reportID); // report ID
            stmt.setString(4, observation.name);
            stmt.setString(5, observation.type);
            stmt.setLong(6, observation.start);
            stmt.setString(7, String.valueOf(observation.value));
            stmt.setString(8, observation.unit);
            if (observation.codes.isEmpty()) {
              stmt.setString(9, null);
              stmt.setString(10, null);
              stmt.setString(11, null);
            } else {
              Code code = observation.codes.get(0);
              stmt.setString(9, code.code);
              stmt.setString(10, code.display);
              stmt.setString(11, code.system);
            }

            stmt.execute();
          }
        }

        for (Observation observation : encounter.observations) {
          if (observation.report != null) {
            // only add observations that don't belong to a diagnostic report here
            continue;
          }
          // CREATE TABLE IF NOT EXISTS OBSERVATION (person_id varchar, encounter_id varchar,
          // report_id varchar, name varchar, type varchar, start bigint, value varchar, unit
          // varchar, code varchar, display varchar, system varchar)
          stmt = connection.prepareStatement(
              "INSERT INTO OBSERVATION "
              + "(person_id, encounter_id, report_id, name, type, start, value, unit, "
              + "code, display, system) "
              + "VALUES (?,?,?,?,?,?,?,?,?,?,?);");
          stmt.setString(1, personID);
          stmt.setString(2, encounterID);
          stmt.setString(3, null); // report ID
          stmt.setString(4, observation.name);
          stmt.setString(5, observation.type);
          stmt.setLong(6, observation.start);
          stmt.setString(7, String.valueOf(observation.value));
          stmt.setString(8, observation.unit);
          if (observation.codes.isEmpty()) {
            stmt.setString(9, null);
            stmt.setString(10, null);
            stmt.setString(11, null);
          } else {
            Code code = observation.codes.get(0);
            stmt.setString(9, code.code);
            stmt.setString(10, code.display);
            stmt.setString(11, code.system);
          }

          stmt.execute();
        }

        for (Procedure procedure : encounter.procedures) {
          // CREATE TABLE IF NOT EXISTS PROCEDURE (person_id varchar, encounter_id varchar, name
          // varchar, type varchar, start bigint, stop bigint, code varchar, display varchar, system
          // varchar)
          stmt = connection.prepareStatement(
              "INSERT INTO PROCEDURE "
              + "(person_id, encounter_id, name, type, start, stop, code, display, system) "
              + "VALUES (?,?,?,?,?,?,?,?,?);");
          stmt.setString(1, personID);
          stmt.setString(2, encounterID);

          stmt.setString(3, procedure.name);
          stmt.setString(4, procedure.type);
          stmt.setLong(5, procedure.start);
          stmt.setLong(6, procedure.stop);
          if (procedure.codes.isEmpty()) {
            stmt.setString(7, null);
            stmt.setString(8, null);
            stmt.setString(9, null);
          } else {
            Code code = procedure.codes.get(0);
            stmt.setString(7, code.code);
            stmt.setString(8, code.display);
            stmt.setString(9, code.system);
          }

          stmt.execute();
        }

        for (Medication medication : encounter.medications) {
          // CREATE TABLE IF NOT EXISTS MEDICATION (id varchar, person_id varchar, provider_id
          // varchar, name varchar, type varchar, start bigint, stop bigint, code varchar, display
          // varchar, system varchar)
          stmt = connection.prepareStatement(
              "INSERT INTO MEDICATION "
              + "(id, person_id, provider_id, name, type, start, stop, code, display, system) "
              + "VALUES (?,?,?,?,?,?,?,?,?,?);");
          String medicationID = UUID.randomUUID().toString();
          stmt.setString(1, medicationID);
          stmt.setString(2, personID);
          stmt.setString(3, providerID);
          stmt.setString(4, medication.name);
          stmt.setString(5, medication.type);
          stmt.setLong(6, medication.start);
          stmt.setLong(7, medication.stop);
          if (medication.codes.isEmpty()) {
            stmt.setString(8, null);
            stmt.setString(9, null);
            stmt.setString(10, null);
          } else {
            Code code = medication.codes.get(0);
            stmt.setString(8, code.code);
            stmt.setString(9, code.display);
            stmt.setString(10, code.system);
          }
          stmt.execute();

          // CREATE TABLE IF NOT EXISTS CLAIM (id varchar, person_id varchar, encounter_id varchar,
          // medication_id varchar, time bigint, cost decimal)
          stmt = connection.prepareStatement(
              "INSERT INTO CLAIM "
              + "(id, person_id, encounter_id, medication_id, time, cost) "
              + "VALUES (?,?,?,?,?,?)");
          stmt.setString(1, UUID.randomUUID().toString());
          stmt.setString(2, personID);
          stmt.setString(3, encounterID);
          stmt.setString(4, medicationID);
          stmt.setLong(5, medication.start);
          stmt.setBigDecimal(6, medication.claim.total());
          stmt.execute();

        }

        for (HealthRecord.Entry immunization : encounter.immunizations) {
          // CREATE TABLE IF NOT EXISTS IMMUNIZATION (person_id varchar, encounter_id varchar, name
          // varchar, type varchar, start bigint, code varchar, display varchar, system varchar)
          stmt = connection.prepareStatement(
              "INSERT INTO IMMUNIZATION "
              + "(person_id, encounter_id, name, type, start, code, display, system) "
              + "VALUES (?,?,?,?,?,?,?,?);");
          stmt.setString(1, personID);
          stmt.setString(2, encounterID);
          stmt.setString(3, immunization.name);
          stmt.setString(4, immunization.type);
          stmt.setLong(5, immunization.start);
          if (immunization.codes.isEmpty()) {
            stmt.setString(6, null);
            stmt.setString(7, null);
            stmt.setString(8, null);
          } else {
            Code code = immunization.codes.get(0);
            stmt.setString(6, code.code);
            stmt.setString(7, code.display);
            stmt.setString(8, code.system);
          }
          stmt.execute();
        }

        for (CarePlan careplan : encounter.careplans) {
          // CREATE TABLE IF NOT EXISTS careplan (id varchar, person_id varchar, provider_id
          // varchar, name varchar, type varchar, start bigint, stop bigint, code varchar, display
          // varchar, system varchar)
          stmt = connection.prepareStatement(
              "INSERT INTO careplan "
              + "(id, person_id, provider_id, name, type, start, stop, code, display, system) "
              + "VALUES (?,?,?,?,?,?,?,?,?,?);");
          stmt.setString(1, UUID.randomUUID().toString());
          stmt.setString(2, personID);
          if (encounter.provider == null) {
            stmt.setString(3, null);
          } else {
            stmt.setString(3, encounter.provider.getResourceID());
          }
          stmt.setString(4, careplan.name);
          stmt.setString(5, careplan.type);
          stmt.setLong(6, careplan.start);
          stmt.setLong(7, careplan.stop);
          if (careplan.codes.isEmpty()) {
            stmt.setString(8, null);
            stmt.setString(9, null);
            stmt.setString(10, null);
          } else {
            Code code = careplan.codes.get(0);
            stmt.setString(8, code.code);
            stmt.setString(9, code.display);
            stmt.setString(10, code.system);
          }
          stmt.execute();
        }

        for (ImagingStudy imagingStudy : encounter.imagingStudies) {
          // CREATE TABLE IF NOT EXISTS IMAGING_STUDY (uid varchar,
          // person_id varchar, encounter_id varchar, start bigint,
          // modality_code varchar, modality_display varchar, modality_system varchar,
          // bodysite_code varchar, bodysite_display varchar, bodysite_system varchar,
          // sop_class varchar)

          stmt = connection.prepareStatement(
              "INSERT INTO IMAGING_STUDY "
              + "(id, uid, person_id, encounter_id, start, modality_code, modality_display, "
              + "modality_system, bodysite_code, bodysite_display, bodysite_system, sop_class) "
              + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?);");
          stmt.setString(1, UUID.randomUUID().toString());
          stmt.setString(2, Utilities.randomDicomUid(0, 0));
          stmt.setString(3, personID);
          stmt.setString(4, encounterID);
          stmt.setLong(5, imagingStudy.start);

          Code modality = imagingStudy.series.get(0).modality;
          stmt.setString(6, modality.code);
          stmt.setString(7, modality.display);
          stmt.setString(8, modality.system);

          Code bodySite = imagingStudy.series.get(0).bodySite;
          stmt.setString(9, bodySite.code);
          stmt.setString(10, bodySite.display);
          stmt.setString(11, bodySite.system);

          Code sopClass = imagingStudy.series.get(0).instances.get(0).sopClass;
          stmt.setString(12, sopClass.code);

          stmt.execute();
        }

        // CREATE TABLE IF NOT EXISTS CLAIM (id varchar, person_id varchar, encounter_id varchar,
        // medication_id varchar, time bigint, cost decimal)
        stmt = connection.prepareStatement(
            "INSERT INTO CLAIM "
            + "(id, person_id, encounter_id, medication_id, time, cost) "
            + "VALUES (?,?,?,?,?,?)");
        stmt.setString(1, UUID.randomUUID().toString());
        stmt.setString(2, personID);
        stmt.setString(3, encounterID);
        stmt.setString(4, null);
        stmt.setLong(5, encounter.start);
        stmt.setBigDecimal(6, encounter.claim.total());
        stmt.execute();

      }

      Map<Integer, Double> qalys = (Map<Integer, Double>) p.attributes.get("QALY");
      Map<Integer, Double> dalys = (Map<Integer, Double>) p.attributes.get("DALY");
      Map<Integer, Double> qols = (Map<Integer, Double>) p.attributes.get("QOL");
      if (qols != null) {
        // TODO - would rather have something more generic
        stmt = connection.prepareStatement(
            "INSERT INTO QUALITY_OF_LIFE (person_id, year, qol, qaly, daly) VALUES (?,?,?,?,?);");

        for (Integer year : qols.keySet()) {
          stmt.setString(1, personID);
          stmt.setInt(2, year);
          stmt.setDouble(3, qols.get(year));
          stmt.setDouble(4, qalys.get(year));
          stmt.setDouble(5, dalys.get(year));
          stmt.addBatch();
        }
        stmt.executeBatch();
      }

      connection.commit();
      return true;
    } catch (SQLException e) {
      e.printStackTrace();
      return false;
    }
  }

  public boolean store(Collection<? extends Provider> providers) {
    try (Connection connection = getConnection()) {
      // CREATE TABLE IF NOT EXISTS PROVIDER (id varchar, name varchar)
      PreparedStatement providerTable = connection
          .prepareStatement("INSERT INTO PROVIDER (id, name) VALUES (?,?);");

      // create table provider_attribute (provider_id varchar, name varchar, value varchar)
      PreparedStatement attributeTable = connection.prepareStatement(
          "INSERT INTO PROVIDER_ATTRIBUTE (provider_id, name, value) VALUES (?,?,?);");

      // CREATE TABLE IF NOT EXISTS UTILIZATION (provider_id varchar, encounters int, procedures
      // int, labs int, prescriptions int)
      PreparedStatement utilizationTable = connection.prepareStatement(
          "INSERT INTO UTILIZATION "
          + "(provider_id, year, encounters, procedures, labs, prescriptions) "
          + "VALUES (?,?,?,?,?,?)");

      // CREATE TABLE IF NOT EXISTS UTILIZATION_DETAIL (provider_id varchar, year int, category
      // string, value int)
      PreparedStatement utilizationDetailTable = connection.prepareStatement(
          "INSERT INTO UTILIZATION_DETAIL (provider_id, year, category, value) VALUES (?,?,?,?)");
      for (Provider p : providers) {
        String providerID = p.getResourceID();
        Map<String, Object> attributes = p.getAttributes();

        providerTable.setString(1, providerID);
        providerTable.setString(2, (String) attributes.get("name"));
        providerTable.addBatch();

        for (Object key : attributes.keySet()) {
          attributeTable.setString(1, providerID);
          attributeTable.setString(2, (String) key);
          attributeTable.setString(3, String.valueOf(attributes.get(key)));
          attributeTable.addBatch();
        }

        Table<Integer, String, AtomicInteger> u = p.getUtilization();
        for (Integer year : u.rowKeySet()) {
          utilizationTable.setString(1, providerID);
          utilizationTable.setInt(2, year);
          utilizationTable.setInt(3, pickUtilization(u, year, Provider.ENCOUNTERS));
          utilizationTable.setInt(4, pickUtilization(u, year, Provider.PROCEDURES));
          utilizationTable.setInt(5, pickUtilization(u, year, Provider.LABS));
          utilizationTable.setInt(6, pickUtilization(u, year, Provider.PRESCRIPTIONS));
          utilizationTable.addBatch();

          for (String category : u.columnKeySet()) {
            if (!category.startsWith(Provider.ENCOUNTERS)) {
              continue;
            }

            int count = pickUtilization(u, year, category);

            if (count == 0) {
              // don't bother storing 0 in the database
              continue;
            }

            utilizationDetailTable.setString(1, providerID);
            utilizationDetailTable.setInt(2, year);
            utilizationDetailTable.setString(3, category);
            utilizationDetailTable.setInt(4, count);
            utilizationDetailTable.addBatch();
          }
        }
      }

      String[] encounterTypes = { "encounters-wellness", "encounters-ambulatory",
          "encounters-outpatient", "encounters-emergency", "encounters-inpatient",
          "encounters-postdischarge", "encounters" };
      for (int year = 1900; year <= Utilities.getYear(System.currentTimeMillis()); year++) {
        for (int t = 0; t < encounterTypes.length; t++) {
          utilizationDetailTable.setString(1, "None");
          utilizationDetailTable.setInt(2, year);
          utilizationDetailTable.setString(3, encounterTypes[t]);
          utilizationDetailTable.setInt(4, 0);
          utilizationDetailTable.addBatch();
        }
      }

      providerTable.executeBatch();
      attributeTable.executeBatch();
      utilizationTable.executeBatch();
      utilizationDetailTable.executeBatch();
      connection.commit();
      return true;
    } catch (SQLException e) {
      e.printStackTrace();
      return false;
    }
  }

  private static int pickUtilization(Table<Integer, String, AtomicInteger> u, int year,
      String category) {
    AtomicInteger value = u.get(year, category);
    if (value == null) {
      return 0;
    } else {
      return value.get();
    }
  }
}
