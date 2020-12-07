package org.mitre.synthea.export;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.v28.datatype.CWE;
import ca.uhn.hl7v2.model.v28.datatype.NM;
import ca.uhn.hl7v2.model.v28.datatype.ST;
import ca.uhn.hl7v2.model.v28.datatype.XAD;
import ca.uhn.hl7v2.model.v28.datatype.XPN;
import ca.uhn.hl7v2.model.v28.group.ADT_A01_PROCEDURE;
import ca.uhn.hl7v2.model.v28.message.ADT_A01;
import ca.uhn.hl7v2.model.v28.segment.AL1;
import ca.uhn.hl7v2.model.v28.segment.DG1;
import ca.uhn.hl7v2.model.v28.segment.EVN;
import ca.uhn.hl7v2.model.v28.segment.MSH;
import ca.uhn.hl7v2.model.v28.segment.OBX;
import ca.uhn.hl7v2.model.v28.segment.PD1;
import ca.uhn.hl7v2.model.v28.segment.PID;
import ca.uhn.hl7v2.model.v28.segment.PV1;
import ca.uhn.hl7v2.model.v28.segment.RXE;
import java.io.IOException;

import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.StringUtils;
import org.mitre.synthea.world.agents.Clinician;

import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.RaceAndEthnicity;

public class HL7V2Exporter {

    private static final String HL7_VERSION = "2.8";
    private static final String CW_NAMESPACE_ID = "CWNS";
    private static final String MSG_TYPE = "ADT";
    private static final String MSG_EVENT_TYPE = "A01";

    private ADT_A01 adt;
    private HashMap<String, String> customSegs = new HashMap();

    public static HL7V2Exporter getInstance() {
        return new HL7V2Exporter();
    }

    public String export(Person person, long time) {
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
            processPatient(person);
            processEncounters(person);
            processAllergies(person);
            processDiagnosis(person);
            processMedications(person);
            processProcedures(person);
            processVitals(person);
            String rawMsg = adt.encode();
            //Fix the end-of-segment default HAPI uses
            rawMsg = rawMsg.replace("\r", "\n");
            msgContent.append(rawMsg);
            //Add any non-standard segments that HAPI doesn't like
            customSegs.forEach((k, seg) -> {
//                System.out.println(String.format("\tAdding Custom Seg: '%s'-%s", k, seg));
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

    private void generateMSH(String curDT) throws DataTypeException {
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

    private void generateEVN(String curDT) throws DataTypeException {
        EVN evn = adt.getEVN();
        evn.getRecordedDateTime().setValue(curDT);
    }

    private void processPatient(Person person) throws DataTypeException, HL7Exception {
        PID pid = adt.getPID();
        Map<String, Object> pattrs = person.attributes;
//        System.out.println("\tGenerating PID: " + pattrs.get("name"));
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
        
        PD1 pd1 = adt.getPD1();
    }

    private void processEncounters(Person person) throws DataTypeException, HL7Exception {
        List<HealthRecord.Encounter> encounters = (List<HealthRecord.Encounter>) person.attributes.get("ehr_encounters");
        Encounter e = encounters.get(0);
//        System.out.println("\tGenerating PV1: " + e.name);   
        PV1 pv1 = adt.getPV1();
        Clinician prov =e.clinician;
        if (e.provider!=null) {
            pv1.getAssignedPatientLocation().getFacility().getHd1_NamespaceID().setValue(e.provider.name);
            pv1.getVisitNumber().getCx1_IDNumber().setValue(String.valueOf(e.hashCode()));
        }
        pv1.getAssignedPatientLocation().getFacility();
        pv1.getAttendingDoctor(0).getXcn1_PersonIdentifier().setValue(String.valueOf(prov.identifier));
        pv1.getAttendingDoctor(0).getXcn2_FamilyName().getFn1_Surname().setValue((String)prov.getAttributes().get("last_name"));
        pv1.getAttendingDoctor(0).getXcn3_GivenName().setValue((String)prov.getAttributes().get("first_name"));
        if (e.start>0) {
            pv1.getAdmitDateTime().setValue(new Date(e.start));
            if (e.stop>0) {
                pv1.getDischargeDateTime().setValue(new Date(e.stop));
            }
        }
        pv1.getAdmissionType().getCwe1_Identifier().setValue(e.type);
        pv1.getAdmissionType().getCwe2_Text().setValue(e.name);
    }
    
    private void processAllergies(Person person) throws DataTypeException, HL7Exception {
        List<Entry> allergies = (List<Entry>) person.attributes.get("ehr_allergies");
        if (allergies == null || allergies.isEmpty()) {
            return;
        }
        Integer ac = 0;
        List<String> seenAllergenCodes = new ArrayList();
        for (Entry entry : allergies) {
//            System.out.println("\tGenerating AL1: " + entry.name);
            AL1 a = new AL1(adt, adt.getModelClassFactory());
            a.getAl11_SetIDAL1().setValue(String.valueOf(ac + 1));
            if (entry.codes != null && entry.codes.size() > 0) {
                Code c = entry.codes.get(0);
                if (seenAllergenCodes.contains(c.code)) {
//                    System.out.println("\t\tSkipping Dup Allergy:" + c.display);
                } else {
//                    System.out.println("\t\tAdding Allergy:" + c.display);
                    seenAllergenCodes.add(c.code);
                    a.getAl12_AllergenTypeCode().getCwe1_Identifier().setValue(c.code);
                    a.getAl12_AllergenTypeCode().getCwe2_Text().setValue(c.display);
                    a.getAl12_AllergenTypeCode().getCwe3_NameOfCodingSystem().setValue(c.system);
                    adt.insertAL1(a, ac++);
                }
            }
        }
    }

    private void processDiagnosis(Person person) throws DataTypeException, HL7Exception {
        List<Entry> conditions = (List<Entry>) person.attributes.get("ehr_conditions");
        if (conditions == null || conditions.isEmpty()) {
            return;
        }
        Integer dc = 0;
        List<String> seenDGCodes = new ArrayList();
        for (Entry entry : conditions) {
//            System.out.println("\tGenerating DG1: " + entry.name);
            DG1 d = new DG1(adt, adt.getModelClassFactory());
            if (entry.codes != null && entry.codes.size() > 0) {
                Code c = entry.codes.get(0);
                if (seenDGCodes.contains(c.code)) {
//                    System.out.println("\t\tSkipping Dup Diag:" + c.display);
                } else {
//                    System.out.println("\t\tAdding Diag:" + c.display);
                    seenDGCodes.add(c.code);
                    d.getDiagnosisCodeDG1().getCwe1_Identifier().setValue(c.code);
                    d.getDiagnosisCodeDG1().getCwe2_Text().setValue(c.display);
                    d.getDiagnosisCodeDG1().getCwe3_NameOfCodingSystem().setValue(c.system);
                    seenDGCodes.add(c.code);

                    if (entry.start > 0) {
                        d.getDiagnosisDateTime().setValue(new Date(entry.start));
                    }
                    adt.insertDG1(d, dc++);
                }
            }
        }
    }

    private void processMedications(Person person) throws DataTypeException, HL7Exception {
        Map<String, HealthRecord.Medication> meds = person.chronicMedications;
        if (meds == null || meds.isEmpty()) {
            return;
        }
        Integer mc = 0;
        List<String> seenMedCodes = new ArrayList();
        for (HealthRecord.Medication med : meds.values()) {
//            System.out.println("\tGenerating RXE: " + med.name);
            RXE m = new RXE(adt, adt.getModelClassFactory());
            if (med.codes != null && med.codes.size() > 0) {
                Code medCode = med.codes.get(0);
                if (seenMedCodes.contains(medCode.code)) {
//                    System.out.println("\t\tSkipping Dup Med Code:" + medCode.display);
                } else {
//                    System.out.println("\t\tAdding Med Code:" + medCode.display);
                    seenMedCodes.add(medCode.code);
                    m.getGiveCode().getCwe1_Identifier().setValue(medCode.code);
                    m.getGiveCode().getCwe2_Text().setValue(medCode.display);
                    m.getGiveCode().getCwe3_NameOfCodingSystem().setValue(medCode.system);
                    if (med.reasons != null && med.reasons.size() > 0) {
                        Integer rc = 0;
                        for (Code medReasonCode : med.reasons) {
//                            System.out.println("\t\tAdding Med Reason Code:" + medReasonCode.toString());
                            m.insertGiveIndication(rc);
                            m.getGiveIndication(rc).getCwe1_Identifier().setValue(medReasonCode.code);
                            m.getGiveIndication(rc).getCwe2_Text().setValue(medReasonCode.display);
                            m.getGiveIndication(rc).getCwe3_NameOfCodingSystem().setValue(medReasonCode.system);
                            rc++;
                        }
                    }
                    if (med.prescriptionDetails != null) {
                        if (med.prescriptionDetails.get("dosage") != null) {
                            String dosageUnits = med.prescriptionDetails.get("dosage").getAsJsonObject().get("unit").getAsString();
                            Integer dosageFreq = med.prescriptionDetails.get("dosage").getAsJsonObject().get("frequency").getAsInt();
                            Integer dosageAmt = med.prescriptionDetails.get("dosage").getAsJsonObject().get("amount").getAsInt();
                            m.getGiveRateAmount().setValue(dosageAmt.toString() + "/" + dosageUnits);
                            m.getGivePerTimeUnit().setValue(dosageFreq.toString());
                            if (med.prescriptionDetails.get("duration") != null) {
                                String durationUnits = med.prescriptionDetails.get("duration").getAsJsonObject().get("unit").getAsString();
                                Integer durationQty = med.prescriptionDetails.get("duration").getAsJsonObject().get("quantity").getAsInt();
                                m.getGiveAmountMaximum().setValue(String.valueOf(durationQty * dosageAmt));
                            }
                        }

                    }

                    customSegs.put(String.format("RXE.%s", mc++), m.encode());
                }
            }
        }
    }

    private void processProcedures(Person person) throws DataTypeException, HL7Exception {
        List<Entry> procs = (List<Entry>) person.attributes.get("ehr_procedures");
        if (procs == null || procs.isEmpty()) {
            return;
        }
        List<String> seenProcCodes = new ArrayList();
        Integer pc = 0;
        for (Entry gEntry : procs) {
            HealthRecord.Procedure entry = (HealthRecord.Procedure) gEntry;
//            System.out.println("\tGenerating PR1: " + entry.name);
            ADT_A01_PROCEDURE p = new ADT_A01_PROCEDURE(adt, adt.getModelClassFactory());
            if (entry.codes != null && entry.codes.size() > 0) {
                Code procCode = entry.codes.get(0);
                if (seenProcCodes.contains(procCode.code)) {
//                    System.out.println("\t\tSkipping Dup Procedure Code:" + procCode.display);
                } else {
//                    System.out.println("\t\tAdding Procedure Code:" + procCode.display);
                    seenProcCodes.add(procCode.code);
                    p.getPR1().getProcedureCode().getCne1_Identifier().setValue(procCode.code);
                    p.getPR1().getProcedureCode().getCne2_Text().setValue(procCode.display);
                    p.getPR1().getProcedureCode().getCne3_NameOfCodingSystem().setValue(procCode.system);

                    if (entry.reasons != null && entry.reasons.size() > 0) {
                        Code procAssocDiagCode = entry.reasons.get(0);
//                        System.out.println("\t\tAdding Assoc Diag Code:" + procAssocDiagCode.toString());
                        p.getPR1().getAssociatedDiagnosisCode().getCwe1_Identifier().setValue(procAssocDiagCode.code);
                        p.getPR1().getAssociatedDiagnosisCode().getCwe2_Text().setValue(procAssocDiagCode.display);
                        p.getPR1().getAssociatedDiagnosisCode().getCwe2_Text().setValue(procAssocDiagCode.system);
                    }
                    if (entry.start > 0) {
                        p.getPR1().getPr15_ProcedureDateTime().setValue(new Date(entry.start));
                    }
                    adt.insertPROCEDURE(p, pc++);
                }
            }
        }
    }

    private void processVitals(Person person) throws HL7Exception {
        List<HealthRecord.Encounter> encounters = (List<HealthRecord.Encounter>) person.attributes.get("ehr_encounters");
        AtomicInteger ox = new AtomicInteger(1);
        List<String> seenVitalCodes = new ArrayList();        
        for (Encounter encounter : encounters) {
            if (encounter.observations.size() > 0) {
                for (Observation obs : encounter.observations) {
                    if (obs.observations != null && obs.observations.size() > 0) {
                        for (Observation subObs : obs.observations) {
                            addObservation(subObs, ox, seenVitalCodes);
                        }
                    } else {
                        addObservation(obs, ox, seenVitalCodes);
                    }
                }
            }
        }
    }

    private void addObservation(Observation obs, AtomicInteger ox, List<String> seenVitalCodes) throws DataTypeException, HL7Exception {
        if (obs.codes != null && obs.codes.size() > 0) {
//            System.out.println("\tGenerating OBX: " + obs.toString());            
            OBX v = new OBX(adt, adt.getModelClassFactory());
            v.getObx1_SetIDOBX().setValue(ox.toString());

            Code obscode = obs.codes.get(0);
            //Checking to see if we've already added one of these.  If so, let's keep it simple and stop. 
            if (seenVitalCodes.contains(obscode.code)) {
//                System.out.println("\t\tSkipping redundant Observation Code:" + obscode.display);                
                return;
            }
            //Otherwise, track that we're adding one
            seenVitalCodes.add(obscode.code);
            
//            System.out.println("\t\tAdding Observation Code:" + obscode.display);
            v.getObx3_ObservationIdentifier().getCwe1_Identifier().setValue(obscode.code);
            v.getObx3_ObservationIdentifier().getCwe2_Text().setValue(obscode.display);
            v.getObx3_ObservationIdentifier().getCwe3_NameOfCodingSystem().setValue(obscode.system);

            //Set the Units
            v.getObx6_Units().getCwe2_Text().setValue(obs.unit);
            
            //Generate a slot for the value
            v.insertObx5_ObservationValue(0);
            //Based on the object class of obs.value, generate the appropriate HL7 DataType and populate it into OBX5
            //We set OBX2 to the DataType (e.g., ST or NM)
            if (obs.value instanceof Double) {
                NM nm = new NM(adt);            
                nm.parse(obs.value.toString());
                v.getObx5_ObservationValue()[0].setData(nm);         
                v.getObx2_ValueType().setValue("NM");                  
            } else if (obs.value instanceof HealthRecord.Code) {
                Code c = (HealthRecord.Code)obs.value;
                CWE cwe = new CWE(adt);
                cwe.getCwe1_Identifier().setValue(c.code);
                cwe.getCwe2_Text().setValue(c.display);
                cwe.getCwe3_NameOfCodingSystem().setValue(c.system);
                v.getObx5_ObservationValue()[0].setData(cwe);       
                v.getObx2_ValueType().setValue("CWE");  
            } else if (obs.value instanceof String) {
                ST st = new ST(adt);
                st.setValue(obs.value.toString());
                v.getObx5_ObservationValue()[0].setData(st);
                v.getObx2_ValueType().setValue("ST");
            } else {
                throw new HL7Exception("Unrecognized datatype" + obs.value.getClass().getName());
            }
            //Set the time for the observation
            v.getDateTimeOfTheObservation().setValue(new Date(obs.start));
            //Add it to the  list of observations
            adt.insertOBX(v, 0);
            ox.incrementAndGet();
        }
    }

    private String getStrAttr(Map<String, Object> pattrs, String key) {
        if (pattrs.containsKey(key)) {
            return (String) pattrs.get(key);
        }
        return null;
    }

    private Date getDateAttr(Map<String, Object> pattrs, String key) throws DataTypeException {
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
