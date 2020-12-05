package org.mitre.synthea.export;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.v28.datatype.XAD;
import ca.uhn.hl7v2.model.v28.datatype.XPN;
import ca.uhn.hl7v2.model.v28.message.ADT_A01;
import ca.uhn.hl7v2.model.v28.segment.AL1;
import ca.uhn.hl7v2.model.v28.segment.DG1;
import ca.uhn.hl7v2.model.v28.segment.EVN;
import ca.uhn.hl7v2.model.v28.segment.MSH;
import ca.uhn.hl7v2.model.v28.segment.PID;
import ca.uhn.hl7v2.model.v28.segment.RXE;
import java.io.IOException;

import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
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
    private static final HashMap<String, String> customSegs = new HashMap();

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

        final StringBuilder msgContent = new StringBuilder();
        try {
            String curDT = getCurrentTimeStamp();
            adt = new ADT_A01();
            adt.initQuickstart(MSG_TYPE, MSG_EVENT_TYPE, "P");
            generateMSH(curDT);
            generateEVN(curDT);
            generatePID(person);
            generateAL1s(person);
            generateDG1s(person);
            generateRXEs(person);
            String rawMsg = adt.encode();
            //Fix the end-of-segment default HAPI uses
            rawMsg = rawMsg.replace("\r", "\n");
            msgContent.append(rawMsg);
            //Add any non-standard segments that HAPI doesn't like
            customSegs.forEach((k, seg) -> {
                System.out.println(String.format("\tAdding Custom Seg: '%s'-%s", k, seg));
                msgContent.append(seg);     
                msgContent.append("\n");
            });
        } catch (HL7Exception | IOException ex) {
            ex.printStackTrace();
            msgContent.append(String.format("BLAMMO! error=%s", ex.getMessage()));
        }

        StringWriter writer = new StringWriter();
        writer.write(msgContent.toString());
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
        System.out.println("\tGenerating PID: " + pattrs.get("name"));          
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

    private static void generateAL1s(Person person) throws DataTypeException, HL7Exception {
        List<Entry> allergies = (List<Entry>) person.attributes.get("ehr_allergies");
        if (allergies == null || allergies.isEmpty()) {
            return;
        }
        Integer ac = 0;
        for (Entry entry : allergies) {
            System.out.println("\tGenerating AL1: " + entry.toString());
            AL1 a = new AL1(adt, adt.getModelClassFactory());
            a.getAl11_SetIDAL1().setValue(String.valueOf(ac+1));
            Integer cc = 0;
            for (Code c : entry.codes) {
                switch (cc) {
                    case 0:
                        a.getAl12_AllergenTypeCode().getCwe1_Identifier().setValue(c.code);
                        a.getAl12_AllergenTypeCode().getCwe2_Text().setValue(c.display);
                        a.getAl12_AllergenTypeCode().getCwe3_NameOfCodingSystem().setValue(c.system);
                        break;
                    case 1:
                        a.getAl12_AllergenTypeCode().getCwe4_AlternateIdentifier().setValue(c.code);
                        a.getAl12_AllergenTypeCode().getCwe5_AlternateText().setValue(c.display);
                        a.getAl12_AllergenTypeCode().getCwe6_NameOfAlternateCodingSystem().setValue(c.system);
                        break;
                    case 2:
                        break;
                }
            }
            adt.insertAL1(a, ac++);
        }
    }

    private static void generateDG1s(Person person) throws DataTypeException, HL7Exception {
        List<Entry> conditions = (List<Entry>) person.attributes.get("ehr_conditions");
        if (conditions == null || conditions.isEmpty()) {
            return;
        }
        Integer dc = 0;
        for (Entry entry : conditions) {
            System.out.println("\tGenerating DG1: " + entry.toString());            
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

    private static void generateRXEs(Person person) throws DataTypeException, HL7Exception {
        Map<String,HealthRecord.Medication> meds = person.chronicMedications;
        if (meds == null || meds.isEmpty()) {
            return;
        }
        Integer mc = 0;
        for (HealthRecord.Medication med : meds.values()) {
            System.out.println("\tGenerating RXE: " + med.toString());            
            RXE m = new RXE(adt, adt.getModelClassFactory());
            Integer cc = 0;
            for (Code c : med.codes) {
                switch (cc) {
                    case 0:
                        System.out.println("\t\tAdding Med Code:" + c.toString());
                        m.getGiveCode().getCwe1_Identifier().setValue(c.code);
                        m.getGiveCode().getCwe2_Text().setValue(c.display);
                        m.getGiveCode().getCwe3_NameOfCodingSystem().setValue(c.system);
                        break;
                    case 1:
                        System.out.println("\t\tAdding Alternate Med Code:" + c.toString());                        
                        m.getGiveCode().getCwe4_AlternateIdentifier().setValue(c.code);
                        m.getGiveCode().getCwe5_AlternateText().setValue(c.display);
                        m.getGiveCode().getCwe6_NameOfAlternateCodingSystem().setValue(c.system);
                        break;
                    case 2:
                        break;
                }
            }
            if (med.reasons!=null && med.reasons.size()>0) {
                Integer rc = 0;
                for (Code c : med.reasons) {  
                    System.out.println("\t\tAdding Med Reason Code:" + c.toString());                     
                    m.insertGiveIndication(rc);
                    m.getGiveIndication(rc).getCwe1_Identifier().setValue(c.code);
                    m.getGiveIndication(rc).getCwe2_Text().setValue(c.display);   
                    m.getGiveIndication(rc).getCwe3_NameOfCodingSystem().setValue(c.system);  
                    rc++;
                }              
            }
            if (med.prescriptionDetails!=null) {
                if (med.prescriptionDetails.get("dosage")!=null) {
                    String dosageUnits = med.prescriptionDetails.get("dosage").getAsJsonObject().get("unit").getAsString();
                    Integer dosageFreq = med.prescriptionDetails.get("dosage").getAsJsonObject().get("frequency").getAsInt();   
                    Integer dosageAmt = med.prescriptionDetails.get("dosage").getAsJsonObject().get("amount").getAsInt();   
                    m.getGiveRateAmount().setValue(dosageAmt.toString() + "/" + dosageUnits);
                    m.getGivePerTimeUnit().setValue(dosageFreq.toString());
                    if (med.prescriptionDetails.get("duration")!=null) {
                        String durationUnits = med.prescriptionDetails.get("duration").getAsJsonObject().get("unit").getAsString();
                        Integer durationQty = med.prescriptionDetails.get("duration").getAsJsonObject().get("quantity").getAsInt();
                        m.getGiveAmountMaximum().setValue(String.valueOf(durationQty * dosageAmt));
                    }
                }

            }
                
            customSegs.put(String.format("RXE.%s", mc++), m.encode());
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
