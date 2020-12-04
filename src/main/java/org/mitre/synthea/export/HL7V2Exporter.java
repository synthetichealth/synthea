package org.mitre.synthea.export;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.v28.datatype.XAD;
import ca.uhn.hl7v2.model.v28.datatype.XPN;
import ca.uhn.hl7v2.model.v28.message.ADT_A01;
import ca.uhn.hl7v2.model.v28.segment.DG1;
import ca.uhn.hl7v2.model.v28.segment.EVN;
import ca.uhn.hl7v2.model.v28.segment.MSH;
import ca.uhn.hl7v2.model.v28.segment.PID;
import java.io.IOException;

import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.RaceAndEthnicity;

public class HL7V2Exporter {

    private static final String HL7_VERSION = "2.8";
    private static final String CW_NAMESPACE_ID = "CWNS";
    private static final String MSG_TYPE = "ADT";
    private static final String MSG_EVENT_TYPE = "A01";

    private static ADT_A01 adt;

    public static String export(Person person, long time) {
        // create a super encounter... this makes it easier to access
        // all the Allergies (for example) in the export templates,
        // instead of having to iterate through all the encounters.
        Encounter superEncounter = person.record.new Encounter(time, "super");
        for (Encounter encounter : person.record.encounters) {
            if (encounter.start <= time) {
                superEncounter.observations.addAll(encounter.observations);
                superEncounter.reports.addAll(encounter.reports);
                superEncounter.conditions.addAll(encounter.conditions);
                superEncounter.allergies.addAll(encounter.allergies);
                superEncounter.procedures.addAll(encounter.procedures);
                superEncounter.immunizations.addAll(encounter.immunizations);
                superEncounter.medications.addAll(encounter.medications);
                superEncounter.careplans.addAll(encounter.careplans);
                superEncounter.imagingStudies.addAll(encounter.imagingStudies);
            } else {
                break;
            }
        }

        // The export templates fill in the record by accessing the attributes
        // of the Person, so we add a few attributes just for the purposes of export.
        person.attributes.put("UUID", UUID.randomUUID().toString());
        person.attributes.put("ehr_encounters", person.record.encounters);
        person.attributes.put("ehr_observations", superEncounter.observations);
        person.attributes.put("ehr_reports", superEncounter.reports);
        person.attributes.put("ehr_conditions", superEncounter.conditions);
        person.attributes.put("ehr_allergies", superEncounter.allergies);
        person.attributes.put("ehr_procedures", superEncounter.procedures);
        person.attributes.put("ehr_immunizations", superEncounter.immunizations);
        person.attributes.put("ehr_medications", superEncounter.medications);
        person.attributes.put("ehr_careplans", superEncounter.careplans);
        person.attributes.put("ehr_imaging_studies", superEncounter.imagingStudies);
        person.attributes.put("time", time);
        person.attributes.put("race_lookup", RaceAndEthnicity.LOOK_UP_CDC_RACE);
        person.attributes.put("ethnicity_lookup", RaceAndEthnicity.LOOK_UP_CDC_ETHNICITY_CODE);
        person.attributes.put("ethnicity_display_lookup", RaceAndEthnicity.LOOK_UP_CDC_ETHNICITY_DISPLAY);

        String msgContent;
        try {
            String curDT = getCurrentTimeStamp();
            adt = new ADT_A01();
            adt.initQuickstart(MSG_TYPE, MSG_EVENT_TYPE, "P");
            generateMSH(curDT);
            generateEVN(curDT);
            generatePID(person);
            generateAL1s(person);
            generateDG1s(person);
            msgContent = adt.encode();
            msgContent = msgContent.replace("\r", "\n");
        } catch (HL7Exception | IOException ex) {
            ex.printStackTrace();
            msgContent = "BLAMMO! error=" + ex.getMessage();
        }

        StringWriter writer = new StringWriter();
        writer.write(msgContent);
        return writer.toString();
    }

    private static String getCurrentTimeStamp() {
        return new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    }

    private static String getSequenceNumber() {
        String facilityNumberPrefix = "9999";
        return facilityNumberPrefix.concat(getCurrentTimeStamp());
    }

    private static void generateMSH(String curDT) throws DataTypeException {
        MSH msh = adt.getMSH();
        msh.getFieldSeparator().setValue("|");
        msh.getEncodingCharacters().setValue("^~\\&");
        msh.getSendingApplication().getNamespaceID().setValue(CW_NAMESPACE_ID);
        msh.getSendingApplication().getUniversalID().setValue("LUCY");
        msh.getSendingFacility().getNamespaceID().setValue(CW_NAMESPACE_ID);
        msh.getSendingFacility().getUniversalID().setValue("SYNTHEA");
        msh.getReceivingApplication().getNamespaceID().setValue(CW_NAMESPACE_ID);
        msh.getReceivingApplication().getUniversalID().setValue("XXX");
        msh.getReceivingFacility().getNamespaceID().setValue(CW_NAMESPACE_ID);
        msh.getReceivingFacility().getUniversalID().setValue("YYY");
        msh.getDateTimeOfMessage().setValue(curDT);
        msh.getMessageControlID().setValue(getSequenceNumber());
        msh.getVersionID().getVersionID().setValue(HL7_VERSION);
    }

    private static void generateEVN(String curDT) throws DataTypeException {
        EVN evn = adt.getEVN();
        evn.getRecordedDateTime().setValue(curDT);
    }

    private static void generatePID(Person person) throws DataTypeException, HL7Exception {
        PID pid = adt.getPID();
        Map<String, Object> pattrs = person.attributes;
        XPN patientName = pid.getPatientName(0);
        patientName.getFamilyName().getSurname().setValue(getStrAttr(pattrs, "last_name"));
        patientName.getGivenName().setValue(getStrAttr(pattrs, "first_name"));
        patientName.getPrefixEgDR().setValue(getStrAttr(pattrs, "name_prefix"));
        pid.getPatientIdentifierList(0).getIDNumber().setValue(getStrAttr(pattrs, "id"));
        XAD patientAddress = pid.getPatientAddress(0);
        patientAddress.getStreetAddress().getStreetOrMailingAddress().setValue("123 Main Street");
        patientAddress.getCity().setValue(getStrAttr(pattrs, "city"));
        patientAddress.getStateOrProvince().setValue(getStrAttr(pattrs, "state"));
        patientAddress.getZipOrPostalCode().setValue(getStrAttr(pattrs, "zip"));
        patientAddress.getCountry().setValue(getStrAttr(pattrs, "country"));
        patientAddress.getCountyParishCode().getText().setValue(getStrAttr(pattrs, "county"));

        //pid.getDriverSLicenseNumberPatient().setValue(pattrs.get("identifier_drivers").toString()); Not valid anymore in HL7
        //pid.getSSNNumberPatient().setValue(pattrs.get("ssn").toString()); Not valid anymore
        String raceName = getStrAttr(pattrs, "race");
        pid.insertPid10_Race(0).getText().setValue(raceName);
        pid.insertPid10_Race(0).getIdentifier().setValue(((Map) pattrs.get("race_lookup")).get(raceName).toString());
        pid.insertPid22_EthnicGroup(0).getText().setValue(((Map) pattrs.get("ethnicity_display_lookup")).get(raceName).toString());
        pid.insertPid22_EthnicGroup(0).getIdentifier().setValue(((Map) pattrs.get("ethnicity_lookup")).get(raceName).toString());

        pid.getAdministrativeSex().getIdentifier().setValue(getStrAttr(pattrs, "gender"));
        pid.getDateTimeOfBirth().setValue(getDateAttr(pattrs, "birthdate"));
        pid.getBirthPlace().setValue(getStrAttr(pattrs, "birthplace"));

        pid.getMaritalStatus().getIdentifier().setValue(getStrAttr(pattrs, "marital_status"));
        String mothersName = getStrAttr(pattrs, "name_mother");
        if (StringUtils.isNotBlank(mothersName)) {
            String[] nameParts = mothersName.split("\\ ");
            if (nameParts.length > 1) {
                pid.insertMotherSMaidenName(0).getGivenName().setValue(nameParts[0]);
                pid.insertMotherSMaidenName(0).getFamilyName().getSurname().setValue(nameParts[1]);
            }
        }
        pid.insertPhoneNumberHome(0).getTelephoneNumber().setValue(getStrAttr(pattrs, "telecom"));
        pid.getPid15_PrimaryLanguage().getText().setValue(getStrAttr(pattrs, "first_language"));
    }

    private static void generateAL1s(Person person) {
        List<Entry> allergies = (List<Entry>) person.attributes.get("ehr_allergies");
        if (allergies == null || allergies.isEmpty()) {
            return;
        }
        allergies.forEach((entry) -> {
            System.out.println(entry);
        });
    }

    private static void generateDG1s(Person person) throws DataTypeException, HL7Exception {
        List<Entry> conditions = (List<Entry>) person.attributes.get("ehr_conditions");
        if (conditions == null || conditions.isEmpty()) {
            return;
        }
        Integer dc = 0;
        for (Entry entry : conditions) {
            DG1 d = new DG1(adt, adt.getModelClassFactory());
            Integer cc = 0;
            for (Code c : entry.codes) {
                switch (cc) {
                    case 0:
                        d.getDiagnosisCodeDG1().getCwe1_Identifier().setValue(c.code);
                        d.getDiagnosisCodeDG1().getCwe2_Text().setValue(c.display);
                        d.getDiagnosisCodeDG1().getCwe3_NameOfCodingSystem().setValue(c.system);
                        break;
                    case 1:
                        d.getDiagnosisCodeDG1().getCwe4_AlternateIdentifier().setValue(c.code);
                        d.getDiagnosisCodeDG1().getCwe5_AlternateText().setValue(c.display);
                        d.getDiagnosisCodeDG1().getCwe6_NameOfAlternateCodingSystem().setValue(c.system);
                        break;      
                    case 2:
                        break;
                }
            }
            if (entry.start > 0) {
                d.getDiagnosisDateTime().setValue(new Date(entry.start));
            }
            adt.insertDG1(d, dc++);
        }
    }

    private static String getStrAttr(Map<String, Object> pattrs, String key) {
        if (pattrs.containsKey(key)) {
            return (String) pattrs.get(key);
        }
        return null;
    }

    private static Date getDateAttr(Map<String, Object> pattrs, String key) throws DataTypeException {
        try {
            Long dateL = (Long) pattrs.get(key);
            if (dateL != null) {
                Date d = new Date(dateL);
                return d;
            }
        } catch (RuntimeException e) {
            throw new DataTypeException("Couldn't parse attribute from key=" + key);
        }
        return null;
    }

}
