package org.mitre.synthea.export;

import static org.mitre.synthea.export.ExportHelper.iso8601Timestamp;

import com.google.gson.JsonObject;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.FactTable;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.DeathModule;
import org.mitre.synthea.modules.Immunizations;
import org.mitre.synthea.modules.LifecycleModule;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.Immunization;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;
import org.mitre.synthea.world.concepts.HealthRecord.Report;
import org.mitre.synthea.world.geography.Location;

/**
 * This exporter attempts to export synthetic patient data into 
 * comma-separated value (CSV) files that align with the Veteran's 
 * Health Administration (VHA) Corporate Data Warehouse (CDW).
 * <p></p>
 * https://www.data.va.gov/dataset/corporate-data-warehouse-cdw
 */
public class CDWExporter {
  /** Number of clinicians to generate. */
  private static final int CLINICIANS = 100;

  /** Temporary attribute to record clinician on a provider encounter. */
  private static final String CLINICIAN_SID = "CLINICIAN_SID";

  /**
   * Table key sequence generators.
   */
  private Map<OutputStreamWriter,AtomicInteger> sids;
  private int sidStart = 1;

  private FactTable sstaff = new FactTable();
  private FactTable maritalStatus = new FactTable();
  private FactTable sta3n = new FactTable();
  private FactTable location = new FactTable();
  // private FactTable appointmentStatus = new FactTable();
  // private FactTable appointmentType = new FactTable();
  private FactTable immunizationName = new FactTable();
  private FactTable reaction = new FactTable();
  private FactTable providerNarrative = new FactTable();
  private FactTable localDrug = new FactTable();
  private FactTable nationalDrug = new FactTable();
  private FactTable dosageForm = new FactTable();
  private FactTable pharmacyOrderableItem = new FactTable();
  private FactTable orderableItem = new FactTable();
  private FactTable orderStatus = new FactTable();
  private FactTable vistaPackage = new FactTable();
  private FactTable collectionsample = new FactTable();
  private FactTable labchemtest = new FactTable();
  private FactTable topography = new FactTable();
  private FactTable institution = new FactTable();
  private FactTable loinc = new FactTable();
  private FactTable cpt = new FactTable();
  private FactTable vitalType = new FactTable();

  /**
   * Writers for patient data.
   */
  private OutputStreamWriter lookuppatient;
  private OutputStreamWriter spatient;
  private OutputStreamWriter spatientaddress;
  private OutputStreamWriter spatientphone;
  private OutputStreamWriter patientrace;
  private OutputStreamWriter patientethnicity;

  /**
   * Writers for encounter data.
   */
  private OutputStreamWriter consult;
  private OutputStreamWriter visit;
  private OutputStreamWriter appointment;
  private OutputStreamWriter inpatient;

  /**
   * Writers for immunization data.
   */
  private OutputStreamWriter immunization;

  /**
   * Writers for allergy data.
   */
  private OutputStreamWriter allergy;
  private OutputStreamWriter allergicreaction;
  private OutputStreamWriter allergycomment;

  /**
   * Writers for condition data.
   */
  private OutputStreamWriter problemlist;
  private OutputStreamWriter vdiagnosis;

  /**
   * Writers for medications data.
   */
  private OutputStreamWriter rxoutpatient;
  private OutputStreamWriter rxoutpatfill;
  private OutputStreamWriter nonvamed;
  private OutputStreamWriter cprsorder; // also required for labs
  private OutputStreamWriter ordereditem; // also required for labs

  /**
   * Writers for diagnostic report data (i.e. labs).
   */
  private OutputStreamWriter labchem;
  private OutputStreamWriter labpanel;
  private OutputStreamWriter patientlabchem;
  private OutputStreamWriter vprocedure;

  /**
   * Writers for procedure data.
   */
  private OutputStreamWriter surgeryProcedureDiagnosisCode;
  private OutputStreamWriter surgeryPRE;

  /**
   * Writers for vital sign Observation data.
   */
  private OutputStreamWriter vitalSign;
  
  /**
   * Charset for specifying the character set of the output files.
   */
  private Charset charset = Charset.forName(Config.get("exporter.encoding"));

  /**
   * System-dependent string for a line break. (\n on Mac, *nix, \r\n on Windows)
   */
  private static final String NEWLINE = System.lineSeparator();

  /**
   * Constructor for the CDWExporter -
   *  initialize the required files and associated writers.
   */
  private CDWExporter() {
    sids = new HashMap<OutputStreamWriter,AtomicInteger>();
    
    try {
      File output = Exporter.getOutputFolder("cdw", null);
      output.mkdirs();
      Path outputDirectory = output.toPath();

      // Patient Data
      lookuppatient = openOutputStreamWriter(outputDirectory, "lookuppatient.csv");
      spatient = openOutputStreamWriter(outputDirectory, "spatient.csv");
      spatientaddress = openOutputStreamWriter(outputDirectory, "spatientaddress.csv");
      spatientphone = openOutputStreamWriter(outputDirectory, "spatientphone.csv");
      patientrace = openOutputStreamWriter(outputDirectory, "patientrace.csv");
      patientethnicity = openOutputStreamWriter(outputDirectory, "patientethnicity.csv");

      // Encounter Data
      consult = openOutputStreamWriter(outputDirectory, "consult.csv");
      visit = openOutputStreamWriter(outputDirectory, "visit.csv");
      appointment = openOutputStreamWriter(outputDirectory, "appointment.csv");
      inpatient = openOutputStreamWriter(outputDirectory, "inpatient.csv");

      // Immunization Data
      immunization = openOutputStreamWriter(outputDirectory, "immunization.csv");

      // Allergy Data
      allergy = openOutputStreamWriter(outputDirectory, "allergy.csv");
      allergicreaction = openOutputStreamWriter(outputDirectory, "allergicreaction.csv");
      allergycomment = openOutputStreamWriter(outputDirectory, "allergycomment.csv");

      // Condition Data
      problemlist = openOutputStreamWriter(outputDirectory, "problemlist.csv");
      vdiagnosis = openOutputStreamWriter(outputDirectory, "vdiagnosis.csv");

      // Medications Data
      rxoutpatient = openOutputStreamWriter(outputDirectory, "rxoutpatient.csv");
      rxoutpatfill = openOutputStreamWriter(outputDirectory, "rxoutpatfill.csv");
      nonvamed = openOutputStreamWriter(outputDirectory, "nonvamed.csv");
      cprsorder = openOutputStreamWriter(outputDirectory, "cprsorder.csv");
      ordereditem = openOutputStreamWriter(outputDirectory, "ordereditem.csv");

      // Diagnotic Report (i.e. Labs) Data
      labchem = openOutputStreamWriter(outputDirectory, "labchem.csv");
      labpanel = openOutputStreamWriter(outputDirectory, "labpanel.csv");
      patientlabchem = openOutputStreamWriter(outputDirectory, "patientlabchem.csv");
      vprocedure = openOutputStreamWriter(outputDirectory, "vprocedure.csv");

      // Procedure Data
      surgeryProcedureDiagnosisCode = openOutputStreamWriter(outputDirectory,
          "surgeryprocedurediagnosiscode.csv");
      surgeryPRE = openOutputStreamWriter(outputDirectory, "surgerypre.csv");

      // Vital Sign Observation Data
      vitalSign = openOutputStreamWriter(outputDirectory, "vitalsign.csv");

      writeCSVHeaders();
    } catch (IOException e) {
      // wrap the exception in a runtime exception.
      // the singleton pattern below doesn't work if the constructor can throw
      // and if these do throw ioexceptions there's nothing we can do anyway
      throw new RuntimeException(e);
    }
  }

  private OutputStreamWriter openOutputStreamWriter(Path outputDirectory, String filename)
      throws IOException {
    File file = outputDirectory.resolve(filename).toFile();
    return new OutputStreamWriter(new FileOutputStream(file), charset);
  }

  /**
   * Write the headers to each of the CSV files.
   * @throws IOException if any IO error occurs
   */
  private void writeCSVHeaders() throws IOException {
    // Fact Tables
    sstaff.setHeader("StaffSID,StaffName");
    maritalStatus.setHeader("MaritalStatusSID,MaritalStatusCode");
    sta3n.setHeader("Sta3n,Sta3nName,TimeZone");
    location.setHeader("LocationSID,LocationName");
    immunizationName.setHeader("ImmunizationNameSID,ImmunizationName,CVXCode,MaxInSeries");
    reaction.setHeader("ReactionSID,Reaction,VUID");
    providerNarrative.setHeader("ProviderNarrativeSID,ProviderNarrative");
    localDrug.setHeader("LocalDrugSID,LocalDrugIEN,Sta3n,LocalDrugNameWithDose,"
        + "NationalDrugSID,NationalDrugNameWithDose,PharmacyOrderableItemSID");
    nationalDrug.setHeader("NationalDrugSID,DrugNameWithDose,DosageFormSID,"
        + "InactivationDate,VUID");
    dosageForm.setHeader("DosageFormSID,DosageFormIEN,DosageForm");
    dosageForm.addFact("1", "1,Once per day."); // Default Dosage
    pharmacyOrderableItem.setHeader("PharmacyOrderableItemSID,PharmacyOrderableItem,SupplyFlag");
    orderableItem.setHeader("OrderableItemSID,OrderableItemName,IVBaseFlag,IVAdditiveFlag");
    orderStatus.setHeader("OrderStatusSID,OrderStatus");
    vistaPackage.setHeader("VistaPackageSID,VistaPackage");
    collectionsample.setHeader("CollectionSampleSID,CollectionSample");
    labchemtest.setHeader("LabChemTestSID,LabChemTestName,CollectionSampleSID");
    topography.setHeader("TopographySID,Topography");
    institution.setHeader("InstitutionSID,Sta3n,InstitutionName,InstitutionCode");
    loinc.setHeader("LOINCSID,LOINC,Component");
    cpt.setHeader("CPTSID,CPTCode,CPTName,CPTDescription");
    vitalType.setHeader("VitalTypeSID,VitalType,VUID");

    // Patient Tables
    lookuppatient.write("PatientSID,Sta3n,PatientIEN,PatientICN,PatientFullCN,"
        + "PatientName,TestPatient");
    lookuppatient.write(NEWLINE);
    spatient.write("PatientSID,PatientName,PatientLastName,PatientFirstName,PatientSSN,Age,"
        + "BirthDateTime,DeceasedFlag,DeathDateTime,Gender,SelfIdentifiedGender,Religion,"
        + "MaritalStatus,MaritalStatusSID,PatientEnteredDateTime");
    spatient.write(NEWLINE);
    spatientaddress.write("SPatientAddressSID,PatientSID,AddressType,NameOfContact,"
        + "RelationshipToPatient,StreetAddress1,StreetAddress2,StreetAddress3,"
        + "City,State,Zip,PostalCode,Country,GISMatchScore,GISStreetSide,"
        + "GISPatientAddressLongitude,GISPatientAddressLatitude,GISFIPSCode");
    spatientaddress.write(NEWLINE);
    spatientphone.write("SPatientPhoneSID,PatientSID,PatientContactType,NameOfContact,"
        + "RelationshipToPatient,PhoneNumber,WorkPhoneNumber,EmailAddress");
    spatientphone.write(NEWLINE);
    patientrace.write("PatientRaceSID,PatientSID,Race");
    patientrace.write(NEWLINE);
    patientethnicity.write("PatientEthnicitySID,PatientSID,Ethnicity");
    patientethnicity.write(NEWLINE);

    // Encounter Tables
    consult.write("ConsultSID,ToRequestServiceSID");
    consult.write(NEWLINE);
    visit.write("VisitSID,VisitDateTime,CreatedByStaffSID,LocationSID,PatientSID");
    visit.write(NEWLINE);
    appointment.write("AppointmentSID,Sta3n,PatientSID,AppointmentDateTime,AppointmentMadeDate,"
        + "AppointmentTypeSID,AppointmentStatus,VisitSID,LocationSID,PurposeOfVisit,"
        + "SchedulingRequestType,FollowUpVisitFlag,LengthOfAppointment,ConsultSID,"
        + "CheckInDateTime,CheckOutDateTime");
    appointment.write(NEWLINE);
    inpatient.write("InpatientSID,PatientSID,AdmitDateTime");
    inpatient.write(NEWLINE);

    // Immunization Table
    immunization.write("ImmunizationSID,ImmunizationIEN,Sta3n,PatientSID,ImmunizationNameSID,"
        + "Series,Reaction,VisitDateTime,ImmunizationDateTime,OrderingStaffSID,ImmunizingStaffSID,"
        + "VisitSID,ImmunizationComments,ImmunizationRemarks");
    immunization.write(NEWLINE);

    // Allergy Tables
    allergy.write("AllergySID,AllergyIEN,Sta3n,PatientSID,AllergyType,AllergicReactant,"
        + "LocalDrugSID,DrugNameWithoutDoseSID,DrugClassSID,ReactantSID,DrugIngredientSID,"
        + "OriginationDateTime,OriginatingStaffSID,ObservedHistorical,Mechanism,VerifiedFlag,"
        + "VerificationDateTime,VerifyingStaffSID,EnteredInErrorFlag");
    allergy.write(NEWLINE);
    allergicreaction.write("AllergicReactionSID,AllergySID,AllergyIEN,Sta3n,ReactionSID");
    allergicreaction.write(NEWLINE);
    allergycomment.write("AllergyCommentSID,AllergySID,AllergyIEN,Sta3n,PatientSID,"
        + "OriginationDateTime,EnteringStaffSID,AllergyComment,CommentEnteredDateTime");
    allergycomment.write(NEWLINE);

    // Condition Tables
    problemlist.write("ProblemListSID,Sta3n,ICD9SID,ICD10SID,PatientSID,ProviderNarrativeSID,"
        + "EnteredDateTime,OnsetDateTime,ProblemListCondition,RecordingProviderSID,"
        + "ResolvedDateTime,SNOMEDCTConceptCode");
    problemlist.write(NEWLINE);
    vdiagnosis.write("VDiagnosisSID,Sta3n,ICD9SID,ICD10SID,PatientSID,VisitSID,"
        + "VisitDateTime,VDiagnosisDateTime,ProviderNarrativeSID,ProblemListSID,"
        + "OrderingProviderSID,EncounterProviderSID");
    vdiagnosis.write(NEWLINE);

    // Medications Tables
    rxoutpatient.write("RxOutpatSID,Sta3n,RxNumber,IssueDate,CancelDate,FinishingDateTime,"
        + "PatientSID,ProviderSID,EnteredByStaffSID,LocalDrugSID,NationalDrugSID,"
        + "PharmacyOrderableItemSID,MaxRefills,RxStatus,OrderedQuantity");
    rxoutpatient.write(NEWLINE);
    rxoutpatfill.write("RxOutpatFillSID,RxOutpatSID,Qty,DaysSupply");
    rxoutpatfill.write(NEWLINE);
    nonvamed.write("NonVAMedSID,PatientSID,NonVAMedIEN,Sta3n,LocalDrugSID,Dosage,"
        + "MedicationRoute,Schedule,NonVAMedStatus,CPRSOrderSID,StartDateTime,"
        + "DocumentedDateTime,NonVAMedComments");
    nonvamed.write(NEWLINE);
    cprsorder.write("CPRSOrderID,Sta3n,PatientSID,OrderStaffSID,EnteredByStaffSID,"
        + "EnteredDateTime,OrderStatusSID,VistaPackageSID,OrderStartDateTime,OrderStopDateTime,"
        + "PackageReference");
    cprsorder.write(NEWLINE);
    ordereditem.write("OrderedItemSID,CPRSOrderSID,OrderableItemSID");
    ordereditem.write(NEWLINE);

    // Diagnostic Report Tables
    labchem.write("LabChemSID,Sta3n,LabPanelSID,LabChemTestSID,PatientSID,StaffSID,"
        + "LabChemSpecimenDateTime,LabChemResultValue,LOINCSID,Units,Abnormal,RefHigh,RefLow");
    labchem.write(NEWLINE);
    labpanel.write("LabPanelSID,LabPanelIEN,PatientSID");
    labpanel.write(NEWLINE);
    patientlabchem.write("LabChemSID,Sta3n,LabPanelSID,PatientSID,LabChemSpecimenDateTime,"
        + "LabChemCompleteDateTime,TopographySID,AccessionInstitutionSID");
    patientlabchem.write(NEWLINE);
    vprocedure.write("VProcedureSID,PatientSID,VisitSID,CPRSOrderSID");
    vprocedure.write(NEWLINE);

    // Procedure Tables
    surgeryProcedureDiagnosisCode.write("SurgeryProcedureDiagnosisCodeSID,SurgerySID,Sta3n,"
        + "PrincipalCPTSID,PatientSID,SurgeryDateTime,PrincipalPostOpICD9SID,"
        + "PrincipalPostOpICD10SID,CodingCompleteFlag");
    surgeryProcedureDiagnosisCode.write(NEWLINE);
    surgeryPRE.write("SurgerySID,VisitSID,NonORLocationSID,SurgeryCancelReasonSID,CPRSOrderSID");
    surgeryPRE.write(NEWLINE);

    // Vital Sign Observation Tables
    vitalSign.write("VitalSignSID,Sta3n,VitalSignTakenDateTime,PatientSID,VitalTypeSID,VitalResult,"
        + "Systolic,Diastolic,SupplementalO2,LocationSID,StaffSID,EnteredInErrorFlag");
    vitalSign.write(NEWLINE);
  }

  /**
   * Generate a list of practicing Clinicians.
   * This is temporary until Provider organizations have associated
   * Clinician agents.
   */
  private void generateClinicians() {
    Random random = new Random(999L);
    for (int i = 0; i < CLINICIANS; i++) {
      Person clinician = new Person(random.nextLong());
      if (random.nextBoolean()) {
        clinician.attributes.put(Person.GENDER, "M");
      } else {
        clinician.attributes.put(Person.GENDER, "F");
      }
      clinician.attributes.put(Person.RACE, "unknown");
      clinician.attributes.put(Person.ETHNICITY, "unknown");
      clinician.attributes.put(Person.FIRST_LANGUAGE, "English");
      LifecycleModule.birth(clinician, 0L);
      String name = "Dr. " + clinician.attributes.get(Person.FIRST_NAME);
      name += " " + clinician.attributes.get(Person.LAST_NAME);
      sstaff.addFact("" + i, clean(name));
    }
  }

  /**
   *  Thread safe singleton pattern adopted from
   *  https://stackoverflow.com/questions/7048198/thread-safe-singletons-in-java
   */
  private static class SingletonHolder {
    /**
     * Singleton instance of the CDWExporter.
     */
    private static final CDWExporter instance = new CDWExporter();
  }

  /**
   * Get the current instance of the CDWExporter.
   * @return the current instance of the CDWExporter.
   */
  public static CDWExporter getInstance() {
    return SingletonHolder.instance;
  }

  /**
   * Set the sequence generator key starting values.
   * Useful to ensure states do not generate
   * overlapping or colliding values.
   * @param id The start of the sequence generators.
   */
  public void setKeyStart(int id) {
    sidStart = id;

    // Dim tables have smaller key ranges: only a 2 byte integer -- max of 32K
    id = (id / 2500); // this gives a range of 400 entries per state without collisions.
    if (id == 0) {
      // We don't want to have any keys with zero, because certain queries ignore them.
      id = 1;
    }

    sstaff.setNextId(id);
    generateClinicians();
    maritalStatus.setNextId(id);
    sta3n.setNextId(id);
    location.setNextId(id);
    // appointmentStatus.setNextId(id);
    // appointmentType.setNextId(id);
    immunizationName.setNextId(id);
    reaction.setNextId(id);
    providerNarrative.setNextId(id);
    localDrug.setNextId(id);
    nationalDrug.setNextId(id);
    dosageForm.setNextId(id);
    pharmacyOrderableItem.setNextId(id);
    orderableItem.setNextId(id);
    orderStatus.setNextId(id);
    vistaPackage.setNextId(id);
    collectionsample.setNextId(id);
    labchemtest.setNextId(id);
    topography.setNextId(id);
    institution.setNextId(id);
    loinc.setNextId(id);
    cpt.setNextId(id);
    vitalType.setNextId(id);
  }

  /**
   * Add a single Person's health record info to the CSV records.
   * @param person Person to write record data for
   * @param time Time the simulation ended
   * @throws IOException if any IO error occurs
   */
  public void export(Person person, long time) throws IOException {
    // Ignore civilians, only consider the veteran population.
    if (!person.attributes.containsKey("veteran")) {
      return;
    }
    int primarySta3n = -1;
    Provider provider = person.getProvider(EncounterType.WELLNESS, time);
    if (provider != null) {
      String state = Location.getStateName(provider.state);
      String tz = Location.getTimezoneByState(state);
      primarySta3n = sta3n.addFact(provider.id, clean(provider.name) + "," + tz);
      location.addFact(provider.id,  clean(provider.name));
    }

    int personID = patient(person, primarySta3n, time);

    for (Encounter encounter : person.record.encounters) {
      int encounterID = encounter(personID, person, encounter, primarySta3n);

      for (HealthRecord.Entry condition : encounter.conditions) {
        condition(personID, encounterID, encounter, condition, primarySta3n);
      }

      for (HealthRecord.Entry allergy : encounter.allergies) {
        allergy(personID, person, encounterID, encounter, allergy, primarySta3n);
      }

      for (HealthRecord.Report report : encounter.reports) {
        // Ignore death certificates
        if (!DeathModule.DEATH_CERTIFICATE.equals(report.codes.get(0))) {
          report(personID, encounterID, encounter, primarySta3n, report);
        }
      }

      for (Observation observation : encounter.observations) {
        observation(personID, encounterID, encounter, primarySta3n, observation);
      }

      for (Procedure procedure : encounter.procedures) {
        procedure(personID, encounterID, encounter, procedure, primarySta3n);
      }

      for (Medication medication : encounter.medications) {
        medication(personID, encounterID, encounter, medication, primarySta3n);
      }

      for (Immunization immunization : encounter.immunizations) {
        immunization(personID, person, encounterID, encounter, immunization, primarySta3n);
      }
    }

    // Patient Data
    lookuppatient.flush();
    spatient.flush();
    spatientaddress.flush();
    spatientphone.flush();
    patientrace.flush();
    patientethnicity.flush();

    // Encounter Data
    consult.flush();
    visit.flush();
    appointment.flush();
    inpatient.flush();

    // Immunization Data
    immunization.flush();

    // Allergy Data
    allergy.flush();
    allergicreaction.flush();
    allergycomment.flush();

    // Condition Data
    problemlist.flush();
    vdiagnosis.flush();

    // Medication Data
    rxoutpatient.flush();
    rxoutpatfill.flush();
    nonvamed.flush();
    cprsorder.flush();
    ordereditem.flush();

    // Diagnostic Report Data
    labchem.flush();
    labpanel.flush();
    patientlabchem.flush();
    vprocedure.flush();

    // Procedure Data
    surgeryProcedureDiagnosisCode.flush();
    surgeryPRE.flush();

    // Vital Sign Observation Data
    vitalSign.flush();
  }

  /**
   * Fact Tables should only be written after all patients have completed export.
   */
  public void writeFactTables() {
    try {
      File output = Exporter.getOutputFolder("cdw", null);
      output.mkdirs();
      Path outputDirectory = output.toPath();
      sstaff.write(openOutputStreamWriter(outputDirectory, "sstaff.csv"));
      maritalStatus.write(openOutputStreamWriter(outputDirectory, "maritalstatus.csv"));
      sta3n.write(openOutputStreamWriter(outputDirectory, "sta3n.csv"));
      location.write(openOutputStreamWriter(outputDirectory, "location.csv"));
      immunizationName.write(openOutputStreamWriter(outputDirectory, "immunizationname.csv"));
      reaction.write(openOutputStreamWriter(outputDirectory, "reaction.csv"));
      providerNarrative.write(openOutputStreamWriter(outputDirectory, "providernarrative.csv"));
      localDrug.write(openOutputStreamWriter(outputDirectory, "localdrug.csv"));
      nationalDrug.write(openOutputStreamWriter(outputDirectory, "nationaldrug.csv"));
      dosageForm.write(openOutputStreamWriter(outputDirectory, "dosageform.csv"));
      pharmacyOrderableItem.write(
          openOutputStreamWriter(outputDirectory, "pharmacyorderableitem.csv"));
      orderableItem.write(openOutputStreamWriter(outputDirectory, "orderableitem.csv"));
      orderStatus.write(openOutputStreamWriter(outputDirectory, "orderstatus.csv"));
      vistaPackage.write(openOutputStreamWriter(outputDirectory, "vistapackage.csv"));
      collectionsample.write(openOutputStreamWriter(outputDirectory, "collectionsample.csv"));
      labchemtest.write(openOutputStreamWriter(outputDirectory, "labchemtest.csv"));
      topography.write(openOutputStreamWriter(outputDirectory, "topography.csv"));
      institution.write(openOutputStreamWriter(outputDirectory, "institution.csv"));
      loinc.write(openOutputStreamWriter(outputDirectory, "loinc.csv"));
      cpt.write(openOutputStreamWriter(outputDirectory, "cpt.csv"));
      vitalType.write(openOutputStreamWriter(outputDirectory, "vitaltype.csv"));
    } catch (IOException e) {
      // wrap the exception in a runtime exception.
      // the singleton pattern below doesn't work if the constructor can throw
      // and if these do throw ioexceptions there's nothing we can do anyway
      throw new RuntimeException(e);
    }
  }

  /**
   * Record a Patient.
   *
   * @param person Person to write data for
   * @param sta3n The primary station ID for this patient
   * @param time Time the simulation ended, to calculate age/deceased status
   * @return the patient's ID, to be referenced as a "foreign key" if necessary
   * @throws IOException if any IO error occurs
   */
  private int patient(Person person, int sta3n, long time) throws IOException {
    // Generate full name and ID
    StringBuilder s = new StringBuilder();
    if (person.attributes.containsKey(Person.NAME_PREFIX)) {
      s.append(person.attributes.get(Person.NAME_PREFIX)).append(' ');
    }
    s.append(person.attributes.get(Person.FIRST_NAME)).append(' ');
    s.append(person.attributes.get(Person.LAST_NAME));
    if (person.attributes.containsKey(Person.NAME_SUFFIX)) {
      s.append(' ').append(person.attributes.get(Person.NAME_SUFFIX));
    }
    String patientName = s.toString();
    int personID = getNextKey(spatient);

    // lookuppatient.write("PatientSID,Sta3n,PatientIEN,PatientICN,PatientFullCN,"
    //     + "PatientName,TestPatient");
    s.setLength(0);
    s.append(personID).append(',');
    s.append(sta3n).append(',');
    s.append(personID).append(',');
    s.append(personID).append(',');
    s.append(personID).append(',');
    s.append(patientName).append(",1");
    s.append(NEWLINE);
    write(s.toString(), lookuppatient);

    // spatient.write("PatientSID,PatientName,PatientLastName,PatientFirstName,PatientSSN,Age,"
    //     + "BirthDateTime,DeceasedFlag,DeathDateTime,Gender,SelfIdentifiedGender,Religion,"
    //     + "MaritalStatus,MaritalStatusSID,PatientEnteredDateTime");
    s.setLength(0);
    s.append(personID).append(',');
    s.append(patientName);
    s.append(',').append(clean((String) person.attributes.getOrDefault(Person.LAST_NAME, "")));
    s.append(',').append(clean((String) person.attributes.getOrDefault(Person.FIRST_NAME, "")));
    s.append(',').append(clean((String) person.attributes.getOrDefault(Person.IDENTIFIER_SSN, "")));

    boolean alive = person.alive(time);
    int age = 0;
    if (alive) {
      age = person.ageInYears(time);
    } else {
      age = person.ageInYears((Long) person.attributes.get(Person.DEATHDATE));
    }
    s.append(',').append(age);
    s.append(',').append(iso8601Timestamp((long) person.attributes.get(Person.BIRTHDATE)));

    if (alive) {
      s.append(',').append('N').append(',');
    } else {
      s.append(',').append('Y');
      s.append(',').append(iso8601Timestamp((Long) person.attributes.get(Person.DEATHDATE)));
    }
    if (person.attributes.get(Person.GENDER).equals("M")) {
      s.append(",M,Male");
    } else {
      s.append(",F,Female");      
    }
    
    s.append(",None"); // Religion
    
    // Currently there are no divorces or widows
    // Legal codes: (D)ivorced, (N)ever Married, (S)eperated, (W)idowed, (M)arried, (U)nknown
    String marital = ((String) person.attributes.get(Person.MARITAL_STATUS));
    if (marital != null) {
      if (marital.equals("M")) {
        s.append(",Married");
      } else {
        marital = "N";
        s.append(",Never Married");
      }
    } else {
      marital = "U";
      s.append(",Unknown");
    }
    s.append(',').append(maritalStatus.addFact(marital, marital));
    
    // TODO Need an enlistment date or date they became a veteran.
    s.append(',').append(iso8601Timestamp(time - Utilities.convertTime("years", 10)));
    s.append(NEWLINE);
    write(s.toString(), spatient);
    
    //  spatientaddress.write("SPatientAddressSID,PatientSID,AddressType,NameOfContact,"
    //  + "RelationshipToPatient,StreetAddress1,StreetAddress2,StreetAddress3,"
    //  + "City,State,Zip,PostalCode,Country,GISMatchScore,GISStreetSide,"
    //  + "GISPatientAddressLongitude,GISPatientAddressLatitude,GISFIPSCode");
    s.setLength(0);
    s.append(getNextKey(spatientaddress)).append(',');
    s.append(personID).append(',');
    s.append("Legal Residence").append(',');
    s.append(person.attributes.get(Person.FIRST_NAME)).append(' ');
    s.append(person.attributes.get(Person.LAST_NAME)).append(',');
    s.append("Self").append(',');
    s.append(person.attributes.get(Person.ADDRESS)).append(",,,");
    s.append(person.attributes.get(Person.CITY)).append(',');
    s.append(person.attributes.get(Person.STATE)).append(',');
    s.append(person.attributes.get(Person.ZIP)).append(',');
    s.append(person.attributes.get(Person.ZIP)).append(",USA,,,");

    Point2D.Double coord = person.getLonLat();
    if (coord != null) {
      s.append(coord.x).append(',').append(coord.y).append(',');
    } else {
      s.append(",,");
    }
    s.append(NEWLINE);
    write(s.toString(), spatientaddress);
    
    //spatientphone.write("SPatientPhoneSID,PatientSID,PatientContactType,NameOfContact,"
    //  + "RelationshipToPatient,PhoneNumber,WorkPhoneNumber,EmailAddress");
    s.setLength(0);
    s.append(getNextKey(spatientphone)).append(',');
    s.append(personID).append(',');
    s.append("Patient Cell Phone").append(',');
    s.append(person.attributes.get(Person.FIRST_NAME)).append(' ');
    s.append(person.attributes.get(Person.LAST_NAME)).append(',');
    s.append("Self").append(',');
    s.append(person.attributes.get(Person.TELECOM)).append(",,");
    s.append(NEWLINE);
    write(s.toString(), spatientphone);

    if (person.randBoolean()) {
      // Add an email address
      s.setLength(0);
      s.append(getNextKey(spatientphone)).append(',');
      s.append(personID).append(',');
      s.append("Patient Email").append(',');
      s.append(person.attributes.get(Person.FIRST_NAME)).append(' ');
      s.append(person.attributes.get(Person.LAST_NAME)).append(',');
      s.append("Self").append(',');
      s.append(",,");
      s.append(person.attributes.get(Person.FIRST_NAME)).append('.');
      s.append(person.attributes.get(Person.LAST_NAME)).append("@email.example");
      s.append(NEWLINE);
      write(s.toString(), spatientphone);
    }

    //patientrace.write("PatientRaceSID,PatientSID,Race");
    String race = (String) person.attributes.get(Person.RACE);
    String ethnicity = (String) person.attributes.get(Person.ETHNICITY);
    if (race.equals("white") && !ethnicity.equals("hispanic")) {
      race = "WHITE NOT OF HISP ORIG";
    } else if (race.equals("white") && ethnicity.equals("hispanic")) {
      race = "WHITE";
    } else if (race.equals("black")) {
      race = "BLACK OR AFRICAN AMERICAN";
    } else if (race.equals("asian")) {
      race = "ASIAN";
    } else if (race.equals("native")) {
      if (person.attributes.get(Person.STATE).equals("Hawaii")) {
        race = "NATIVE HAWAIIAN OR OTHER PACIFIC ISLANDER";
      } else {
        race = "AMERICAN INDIAN OR ALASKA NATIVE";
      }
    } else { // race.equals("other")
      race = "ASIAN";
    }
    s.setLength(0);
    s.append(getNextKey(patientrace)).append(',');
    s.append(personID).append(',');
    s.append(race);
    s.append(NEWLINE);
    write(s.toString(), patientrace);

    //patientethnicity.write("PatientEthnicitySID,PatientSID,Ethnicity");
    s.setLength(0);
    s.append(getNextKey(patientethnicity)).append(',');
    s.append(personID).append(',');
    race = (String) person.attributes.get(Person.RACE);
    if (race.equals("hispanic")) {
      s.append("HISPANIC OR LATINO");
    } else {
      s.append("NOT HISPANIC OR LATINO");
    }
    s.append(NEWLINE);
    write(s.toString(), patientethnicity);

    return personID;
  }

  /**
   * Write a single Encounter to the tables.
   *
   * @param personID The ID of the person that had this encounter
   * @param person The person attending the encounter
   * @param encounter The encounter itself
   * @param primarySta3n The primary home sta3n for the patient
   * @return The encounter ID, to be referenced as a "foreign key" if necessary
   * @throws IOException if any IO error occurs
   */
  private int encounter(int personID, Person person, Encounter encounter, int primarySta3n)
      throws IOException {
    StringBuilder s = new StringBuilder();

    // consult.write("ConsultSID,ToRequestServiceSID");
    int consultSid = getNextKey(consult);
    s.append(consultSid).append(',').append(consultSid).append(NEWLINE);
    write(s.toString(), consult);

    // visit.write("VisitSID,VisitDateTime,CreatedByStaffSID,LocationSID,PatientSID");
    int visitSid = getNextKey(visit);
    int staffSid = person.randInt(CLINICIANS) + (sidStart / 10_000);
    if (encounter.provider != null) {
      encounter.provider.attributes.put(CLINICIAN_SID, staffSid);
    }

    s.setLength(0);
    s.append(visitSid).append(',');
    s.append(iso8601Timestamp(encounter.start)).append(',');
    s.append(staffSid).append(','); // CreatedByStaffID
    Integer locationSid = null;
    if (encounter.provider != null) {
      locationSid = location.addFact(encounter.provider.id, clean(encounter.provider.name));
      s.append(locationSid).append(',');
    } else {
      s.append(primarySta3n).append(',');
    }
    s.append(personID);
    s.append(NEWLINE);
    write(s.toString(), visit);

    // appointment.write("AppointmentSID,Sta3n,PatientSID,AppointmentDateTime,AppointmentMadeDate,"
    //    + "AppointmentTypeSID,AppointmentStatus,VisitSID,LocationSID,PurposeOfVisit,"
    //    + "SchedulingRequestType,FollowUpVisitFlag,LengthOfAppointment,ConsultSID,"
    //    + "CheckInDateTime,CheckOutDateTime");
    s.setLength(0);
    s.append(getNextKey(appointment)).append(',');
    if (encounter.provider != null) {
      String state = Location.getStateName(encounter.provider.state);
      String tz = Location.getTimezoneByState(state);
      s.append(sta3n.addFact(encounter.provider.id, clean(encounter.provider.name) + "," + tz));
    } else {
      s.append(primarySta3n);
    }
    s.append(',');
    s.append(personID).append(',');
    s.append(iso8601Timestamp(encounter.start)).append(',');
    s.append(iso8601Timestamp(encounter.start)).append(',');
    s.append(",,"); // skip: AppointmentTypeSID, AppointmentStatus
    s.append(visitSid).append(',');
    if (locationSid != null) {
      s.append(locationSid).append(',');
    } else {
      s.append(primarySta3n).append(",");
    }
    s.append("3,"); // 3:SCHEDULED VISIT
    s.append(person.rand(new String[] {"N", "C", "P", "W", "M", "A", "O"})).append(',');
    s.append(person.randInt(1)).append(',');
    s.append((encounter.stop - encounter.start) / (60 * 1000)).append(',');
    s.append(consultSid).append(',');
    s.append(iso8601Timestamp(encounter.start)).append(',');
    s.append(iso8601Timestamp(encounter.stop)).append(NEWLINE);
    write(s.toString(), appointment);

    if (encounter.type.equalsIgnoreCase(EncounterType.INPATIENT.toString())) {
      // inpatient.write("InpatientSID,PatientSID,AdmitDateTime");
      s.setLength(0);
      s.append(getNextKey(inpatient)).append(',');
      s.append(personID).append(',');
      s.append(iso8601Timestamp(encounter.start)).append(NEWLINE);
      write(s.toString(), inpatient);
    }

    return visitSid;
  }

  /**
   * Write a single Condition to the tables.
   *
   * @param personID ID of the person that has the condition.
   * @param encounterID ID of the encounter where the condition was diagnosed
   * @param encounter The encounter
   * @param condition The condition itself
   * @param primarySta3n The primary home sta3n for the patient
   * @throws IOException if any IO error occurs
   */
  private void condition(int personID, int encounterID, Encounter encounter,
      Entry condition, int primarySta3n) throws IOException {
    StringBuilder s = new StringBuilder();
    Integer sta3nValue = null;
    Integer providerSID = (sidStart / 10_000);
    if (encounter.provider != null) {
      String state = Location.getStateName(encounter.provider.state);
      String tz = Location.getTimezoneByState(state);
      sta3nValue = sta3n.addFact(encounter.provider.id, clean(encounter.provider.name) + "," + tz);
      providerSID = (Integer) encounter.provider.attributes.get(CLINICIAN_SID);
    }

    Code code = condition.codes.get(0);
    int snomedSID = providerNarrative.addFact(code.code, clean(code.display));

    // problemlist.write("ProblemListSID,Sta3n,ICD9SID,ICD10SID,PatientSID,ProviderNarrativeSID,"
    //    + "EnteredDateTime,OnsetDateTime,ProblemListCondition,RecordingProviderSID,"
    //    + "ResolvedDateTime,SNOMEDCTConceptCode");
    int problemListSid = getNextKey(problemlist);
    s.append(problemListSid).append(',');
    if (sta3nValue != null) {
      s.append(sta3nValue);
    } else {
      s.append(primarySta3n);
    }
    s.append(',');
    s.append(",,"); // skip icd 9 and icd 10
    s.append(personID).append(',');
    s.append(snomedSID).append(','); // snomed display is jammed into narrative.
    s.append(iso8601Timestamp(encounter.start)).append(',');
    s.append(iso8601Timestamp(condition.start)).append(',');
    s.append("P,");
    s.append(providerSID).append(','); // RecordingProviderSID
    if (condition.stop != 0L) {
      s.append(iso8601Timestamp(condition.stop));
    }
    s.append(',');
    s.append(code.code);
    s.append(NEWLINE);
    write(s.toString(), problemlist);

    // vdiagnosis.write("VDiagnosisSID,Sta3n,ICD9SID,ICD10SID,PatientSID,VisitSID,"
    //    + "VisitDateTime,VDiagnosisDateTime,ProviderNarrativeSID,ProblemListSID,"
    //    + "OrderingProviderSID,EncounterProviderSID");
    s.setLength(0);
    s.append(getNextKey(vdiagnosis));
    s.append(',');
    if (sta3nValue != null) {
      s.append(sta3nValue);
    } else {
      s.append(primarySta3n);
    }
    s.append(',');
    s.append(",,"); // skip icd 9 and icd 10
    s.append(personID).append(',');
    s.append(encounterID).append(',');
    s.append(iso8601Timestamp(encounter.start)).append(',');
    s.append(iso8601Timestamp(condition.start)).append(',');
    s.append(snomedSID).append(','); // snomed display is jammed into narrative.
    s.append(problemListSid).append(',');
    s.append(providerSID).append(','); // OrderingProviderSID
    s.append(providerSID).append(','); // EncounterProviderSID
    s.append(NEWLINE);
    write(s.toString(), vdiagnosis);
  }

  /**
   * Write a single Allergy to the tables.
   *
   * @param personID ID of the person that has the allergy.
   * @param person The person
   * @param encounterID ID of the encounter where the allergy was diagnosed
   * @param encounter The encounter
   * @param allergyEntry The allergy itself
   * @param primarySta3n The primary home sta3n for the patient
   * @throws IOException if any IO error occurs
   */
  private void allergy(int personID, Person person, int encounterID, Encounter encounter,
      Entry allergyEntry, int primarySta3n) throws IOException {
    StringBuilder s = new StringBuilder();

    Integer sta3nValue = null;
    Integer providerSID = (sidStart / 10_000);
    if (encounter.provider != null) {
      String state = Location.getStateName(encounter.provider.state);
      String tz = Location.getTimezoneByState(state);
      sta3nValue = sta3n.addFact(encounter.provider.id, clean(encounter.provider.name) + "," + tz);
      providerSID = (Integer) encounter.provider.attributes.get(CLINICIAN_SID);
    }
    Code code = allergyEntry.codes.get(0);
    boolean food = code.display.matches(".*(nut|peanut|milk|dairy|eggs|shellfish|wheat).*");

    // allergy.write("AllergySID,AllergyIEN,Sta3n,PatientSID,AllergyType,AllergicReactant,"
    //     + "LocalDrugSID,DrugNameWithoutDoseSID,DrugClassSID,ReactantSID,DrugIngredientSID,"
    //     + "OriginationDateTime,OriginatingStaffSID,ObservedHistorical,Mechanism,VerifiedFlag,"
    //     + "VerificatiionDateTime,VerifyingStaffSID,EnteredInErrorFlag");
    int allergySID = getNextKey(allergy);
    s.append(allergySID).append(',');
    s.append(allergySID).append(',');
    if (encounter.provider != null) {
      s.append(sta3nValue);
    } else {
      s.append(primarySta3n);
    }
    s.append(',');
    s.append(personID).append(',');
    if (food) {
      s.append('F').append(','); // F: Food allergy
    } else {
      s.append('O').append(','); // O: Other
    }
    s.append(clean(code.display)).append(','); // AllergicReactant
    s.append(','); // LocalDrugSID
    s.append(','); // DrugNameWithoutDoseSID
    s.append(','); // DrugClassSID
    s.append(','); // ReactantSID
    s.append(','); // DrugIngredientSID
    s.append(iso8601Timestamp(allergyEntry.start)).append(',');
    s.append(providerSID).append(','); // OriginatingStaffSID
    s.append(person.rand(new String[] {"o", "h"})).append(',');
    s.append("A,");
    s.append("1,"); // Verified
    s.append(iso8601Timestamp(allergyEntry.start)).append(',');
    s.append(providerSID).append(','); // VerifyingStaffSID
    s.append(',');
    s.append(NEWLINE);
    write(s.toString(), allergy);

    // allergyreaction.write("AllergicReactionSID,AllergySID,AllergyIEN,Sta3n,ReactionSID");
    String reactionDisplay = person.rand(
        new String[] {"Sneezing and Coughing", "Inflammation of Skin",
            "Itchy Watery Eyes", "Difficulty Breathing"});
    s.setLength(0);
    int allergyreactionSID = getNextKey(allergicreaction);
    s.append(allergyreactionSID).append(',');
    s.append(allergySID).append(',');
    s.append(allergySID).append(',');
    if (encounter.provider != null) {
      s.append(sta3nValue);
    } else {
      s.append(primarySta3n);
    }
    s.append(',');
    s.append(reaction.addFact(reactionDisplay, reactionDisplay + "," + allergyreactionSID));
    s.append(NEWLINE);
    write(s.toString(), allergicreaction);

    // allergycomment.write("AllergyCommentSID,AllergySID,AllergyIEN,Sta3n,PatientSID,"
    //    + "OriginationDateTime,EnteringStaffSID,AllergyComment,CommentEnteredDateTime");
    s.setLength(0);
    int allergyCommentSid = getNextKey(allergycomment);
    s.append(allergyCommentSid).append(',');
    s.append(allergySID).append(',');
    s.append(allergySID).append(',');
    if (encounter.provider != null) {
      s.append(sta3nValue);
    } else {
      s.append(primarySta3n);
    }
    s.append(',');
    s.append(personID).append(',');
    s.append(iso8601Timestamp(allergyEntry.start)).append(',');
    s.append(providerSID).append(','); // EnteringStaffSID
    s.append(clean(code.display)).append(',');
    s.append(iso8601Timestamp(allergyEntry.start));
    s.append(NEWLINE);
    write(s.toString(), allergycomment);
  }

  /**
   * Write a DiagnosticReport to the tables.
   *
   * @param personID The ID of the person that had this encounter
   * @param encounterID The ID of the encounter
   * @param encounter The encounter
   * @param primarySta3n The primary home sta3n for the patient
   * @param report The diagnostic lab report
   * @throws IOException if any IO error occurs
   */
  private void report(int personID, int encounterID, Encounter encounter,
      int primarySta3n, Report report) throws IOException {
    StringBuilder s = new StringBuilder();

    Integer sta3nValue = null;
    Integer providerSID = (sidStart / 10_000);
    if (encounter.provider != null) {
      String state = Location.getStateName(encounter.provider.state);
      String tz = Location.getTimezoneByState(state);
      sta3nValue = sta3n.addFact(encounter.provider.id, clean(encounter.provider.name) + "," + tz);
      providerSID = (Integer) encounter.provider.attributes.get(CLINICIAN_SID);
    }

    // cprsorder.write("CPRSOrderID,Sta3n,PatientSID,OrderStaffSID,EnteredByStaffSID,"
    //   + "EnteredDateTime,OrderStatusSID,VistaPackageSID,OrderStartDateTime,OrderStopDateTime,"
    //   + "PackageReference");
    int cprsSID = getNextKey(cprsorder);
    s.setLength(0);
    s.append(cprsSID).append(',');
    if (sta3nValue != null) {
      s.append(sta3nValue);
    } else {
      s.append(primarySta3n);
    }
    s.append(',');
    s.append(personID).append(',');
    s.append(providerSID).append(","); // OrderStaffSID
    s.append(providerSID).append(","); // EnteredByStaffSID
    s.append(iso8601Timestamp(report.start)).append(',');
    int orderStatusSID = orderStatus.addFact("COMPLETED", "COMPLETED");
    s.append(orderStatusSID).append(',');
    int vistaPackageSID = vistaPackage.addFact("DIAGNOSTIC LABORATORY", "DIAGNOSTIC LABORATORY");
    s.append(vistaPackageSID).append(',');
    s.append(iso8601Timestamp(report.start)).append(',');
    if (report.stop != 0L) {
      s.append(iso8601Timestamp(report.stop));
    }
    s.append(',');
    s.append("LAB_" + cprsSID); // PackageReference joins to LabPanel.LabPanelIEN
    s.append(NEWLINE);
    write(s.toString(), cprsorder);

    // orderableItem.setHeader("OrderableItemSID,OrderableItemName,IVBaseFlag,IVAdditiveFlag");
    Code code = report.codes.get(0);
    int orderableItemSID = orderableItem.addFact(code.code, clean(code.display) + ",0,0");

    // ordereditem.write("OrderedItemSID,CPRSOrderSID,OrderableItemSID");
    s.setLength(0);
    s.append(cprsSID).append(",");
    s.append(cprsSID).append(",");
    s.append(orderableItemSID).append(NEWLINE);
    write(s.toString(), ordereditem);

    // vprocedure.write("VProcedureSID,PatientSID,VisitSID,CPRSOrderSID");
    s.setLength(0);
    s.append(getNextKey(vprocedure)).append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');
    s.append(cprsSID);
    s.append(NEWLINE);
    write(s.toString(), vprocedure);

    // loinc.setHeader("LOINCSID,LOINC,Component");
    loinc.addFact(code.code, code.code + "," + clean(code.display));

    // labpanel.write("LabPanelSID,LabPanelIEN,PatientSID");
    s.setLength(0);
    int labpanelSID = getNextKey(labpanel);
    s.append(labpanelSID).append(',');
    s.append("LAB_" + cprsSID).append(','); // LabPanelIEN joins to CPRSOrder.PackageReference
    s.append(personID);
    s.append(NEWLINE);
    write(s.toString(), labpanel);

    // collectionsample.setHeader("CollectionSampleSID,CollectionSample");
    int sampleSID = collectionsample.addFact(code.code, clean(code.display) + " Sample");

    // labchem.write("LabChemSID,Sta3n,LabPanelSID,LabChemTestSID,PatientSID,StaffSID,"
    // + "LabChemSpecimenDateTime,LabChemResultValue,LOINCSID,Units,Abnormal,RefHigh,RefLow");
    for (Observation observation : report.observations) {
      Code obscode = observation.codes.get(0);
      // labchemtest.setHeader("LabChemTestSID,LabChemTestName,CollectionSampleSID");
      int labchemtestSID = labchemtest.addFact(obscode.code,
          clean(obscode.display) + "," + sampleSID);
      int labchemSID = getNextKey(labchem);
      s.setLength(0);
      s.append(labchemSID).append(',');
      if (sta3nValue != null) {
        s.append(sta3nValue);
      } else {
        s.append(primarySta3n);
      }
      s.append(',');
      s.append(labpanelSID).append(',');
      s.append(labchemtestSID).append(',');
      s.append(personID).append(',');
      s.append(providerSID).append(","); // StaffSID
      s.append(iso8601Timestamp(observation.start)).append(',');
      s.append(ExportHelper.getObservationValue(observation)).append(',');
      int loincSID = loinc.addFact(obscode.code, obscode.code + "," + clean(obscode.display));
      s.append(loincSID).append(',');
      s.append(observation.unit).append(',');
      s.append(','); // Abnormal
      s.append(','); // RefHigh, RefLow
      s.append(NEWLINE);
      write(s.toString(), labchem);
    }

    // institution.setHeader("InstitutionSID,Sta3n,InstitutionName,InstitutionCode");
    int institutionSID = 0;
    s.setLength(0);
    if (sta3nValue != null) {
      s.append(sta3nValue).append(',');
      s.append("Diagnostic Laboratory").append(',');
      s.append(sta3nValue);
      institutionSID = institution.addFact("" + sta3nValue, s.toString());
    } else {
      s.append(primarySta3n).append(',');
      s.append("Diagnostic Laboratory").append(',');
      s.append(primarySta3n);
      institutionSID = institution.addFact("" + primarySta3n, s.toString());
    }

    // topography.setHeader("TopographySID,Topography");
    int topographySID = topography.addFact(code.code, clean(code.display) + " Specimen");

    // patientlabchem.write("LabChemSID,Sta3n,LabPanelSID,PatientSID,LabChemSpecimenDateTime,"
    // + "LabChemCompleteDateTime,TopographySID,AccessionInstitutionSID");
    s.setLength(0);
    int patientlabchemSID = getNextKey(patientlabchem);
    s.append(patientlabchemSID).append(',');
    if (sta3nValue != null) {
      s.append(sta3nValue);
    } else {
      s.append(primarySta3n);
    }
    s.append(',');
    s.append(labpanelSID).append(',');
    s.append(personID).append(',');
    s.append(iso8601Timestamp(report.start)).append(','); // LabChemSpecimenDateTime
    s.append(iso8601Timestamp(report.start)).append(','); // LabChemCompleteDateTime
    s.append(topographySID).append(',');
    s.append(institutionSID);
    s.append(NEWLINE);
    write(s.toString(), patientlabchem);
  }

  private static final Map<String,String> VITALS = vitalSignCodes();

  private static Map<String,String> vitalSignCodes() {
    Map<String,String> codes = new HashMap<String,String>();
    codes.put("29463-7", "4500639");
    codes.put("85354-9", "4500634");
    codes.put("72514-3", "4500635");
    codes.put("8302-2", "4688724");
    return codes;
  }

  /**
   * Write a single Observation to the tables.
   *
   * @param personID ID of the person to whom the observation applies.
   * @param encounterID The ID of the encounter
   * @param encounter The encounter
   * @param primarySta3n The primary home sta3n for the patient
   * @param observation The observation itself
   * @throws IOException if any IO error occurs
   */
  private void observation(int personID, int encounterID, Encounter encounter,
      int primarySta3n, Observation observation) throws IOException {
    String code = observation.codes.get(0).code;

    if (!VITALS.containsKey(code)) {
      return;
    }

    Integer sta3nValue = primarySta3n;
    Integer providerSID = (sidStart / 10_000);
    Integer locationSID = null;
    if (encounter.provider != null) {
      String state = Location.getStateName(encounter.provider.state);
      String tz = Location.getTimezoneByState(state);
      sta3nValue = sta3n.addFact(encounter.provider.id, clean(encounter.provider.name) + "," + tz);
      locationSID = location.addFact(encounter.provider.id, clean(encounter.provider.name));
      providerSID = (Integer) encounter.provider.attributes.get(CLINICIAN_SID);
    }

    // vitalType.setHeader("VitalTypeSID,VitalType,VUID");
    int vitalTypeSID = vitalType.addFact(code, code + "," + VITALS.get(code));

    // "VitalSignSID,Sta3n,VitalSignTakenDateTime,PatientSID,VitalTypeSID,VitalResult"
    // "Systolic,Diastolic,SupplementalO2,LocationSID,StaffSID,EnteredInErrorFlag"
    StringBuilder s = new StringBuilder();
    s.append(getNextKey(vitalSign)).append(',');
    s.append(sta3nValue).append(',');
    s.append(iso8601Timestamp(observation.start)).append(',');
    s.append(personID).append(',');
    s.append(vitalTypeSID).append(',');
    String value = null;
    switch (code) {
      case "85354-9": // blood pressure
        s.append(',');
        value = ExportHelper.getObservationValue(observation, "8480-6"); // systolic
        if (value != null && value.indexOf('.') >= 0) {
          value = value.substring(0, value.indexOf('.'));
          s.append(value);
        }
        s.append(',');
        value = ExportHelper.getObservationValue(observation, "8462-4"); // diastolic
        if (value != null && value.indexOf('.') >= 0) {
          value = value.substring(0, value.indexOf('.'));
          s.append(value);
        }
        s.append(',');
        break;
      case "29463-7": // weight
        // convert from kg to lbs
        value = String.format(Locale.US, "%.1f", ((Double) observation.value * 2.20462));
        s.append(value).append(",,,");
        break;
      case "8302-2": // height
        // convert from cm to inches
        value = String.format(Locale.US, "%.1f", ((Double) observation.value * 0.393701));
        s.append(value).append(",,,");
        break;
      case "72514-3": // pain
        value = String.format("%d", StrictMath.round((Double) observation.value));
        s.append(value).append(",,,");
        break;
      default:
        s.append(",,,");
    }
    s.append(','); // SupplementalO2
    s.append(locationSID).append(",");
    s.append(providerSID).append(",");
    s.append(","); // EnteredInErrorFlag
    s.append(NEWLINE);
    write(s.toString(), vitalSign);
  }

  /**
   * Write a Procedure to the tables.
   *
   * @param personID ID of the person on whom the procedure was performed.
   * @param encounterID ID of the encounter where the procedure was performed
   * @param encounter The encounter
   * @param procedure The procedure itself
   * @param primarySta3n The primary home sta3n for the patient
   * @throws IOException if any IO error occurs
   */
  private void procedure(int personID, int encounterID, Encounter encounter,
      Procedure procedure, int primarySta3n) throws IOException {
    StringBuilder s = new StringBuilder();

    Integer sta3nValue = primarySta3n;
    Integer providerSID = (sidStart / 10_000);
    if (encounter.provider != null) {
      String state = Location.getStateName(encounter.provider.state);
      String tz = Location.getTimezoneByState(state);
      sta3nValue = sta3n.addFact(encounter.provider.id, clean(encounter.provider.name) + "," + tz);
      providerSID = (Integer) encounter.provider.attributes.get(CLINICIAN_SID);
    }

    // cprsorder.write("CPRSOrderID,Sta3n,PatientSID,OrderStaffSID,EnteredByStaffSID,"
    //   + "EnteredDateTime,OrderStatusSID,VistaPackageSID,OrderStartDateTime,OrderStopDateTime,"
    //   + "PackageReference");
    int cprsSID = getNextKey(cprsorder);
    s.setLength(0);
    s.append(cprsSID).append(',');
    s.append(sta3nValue).append(',');
    s.append(personID).append(',');
    s.append(providerSID).append(","); // OrderStaffSID
    s.append(providerSID).append(","); // EnteredByStaffSID
    s.append(iso8601Timestamp(procedure.start)).append(',');
    int orderStatusSID = orderStatus.addFact("COMPLETED", "COMPLETED");
    s.append(orderStatusSID).append(',');
    int vistaPackageSID = vistaPackage.addFact("PROCEDURE", "PROCEDURE");
    s.append(vistaPackageSID).append(',');
    s.append(iso8601Timestamp(procedure.start)).append(',');
    if (procedure.stop != 0L) {
      s.append(iso8601Timestamp(procedure.stop));
    }
    s.append(',');
    s.append("PROCEDURE");
    s.append(NEWLINE);
    write(s.toString(), cprsorder);

    //surgeryPRE.write("SurgerySID,VisitSID,NonORLocationSID,SurgeryCancelReasonSID,CPRSOrderSID")
    int surgerySID = getNextKey(surgeryPRE);
    s.setLength(0);
    s.append(surgerySID).append(',');
    s.append(encounterID).append(',');
    s.append(sta3nValue).append(',');
    s.append(','); // SurgeryCancelReasonSID
    s.append(cprsSID);
    s.append(NEWLINE);
    write(s.toString(), surgeryPRE);

    Code code = procedure.codes.get(0);
    //Code reason = procedure.reasons.get(0);
    //cpt.setHeader("CPTSID,CPTCode,CPTName,CPTDescription");
    int cptSID = cpt.addFact(code.code, "XXXXX," + clean(code.display) + "," + clean(code.display));

    //surgeryProcedureDiagnosisCode.write("SurgeryProcedureDiagnosisCodeSID,SurgerySID,Sta3n,"
    //    + "PrincipalCPTSID,PatientSID,SurgeryDateTime,PrincipalPostOpICD9SID,"
    //    + "PrincipalPostOpICD10SID,CodingCompleteFlag");
    int spdcSID = getNextKey(surgeryProcedureDiagnosisCode);
    s.setLength(0);
    s.append(spdcSID).append(',');
    s.append(surgerySID).append(',');
    s.append(sta3nValue).append(',');
    s.append(cptSID).append(',');
    s.append(personID).append(',');
    s.append(iso8601Timestamp(procedure.start)).append(',');
    s.append(','); // PrincipalPostOpICD9SID
    s.append(','); // PrincipalPostOpICD10SID
    s.append('1'); // CodingCompleteFlag
    s.append(NEWLINE);
    write(s.toString(), surgeryProcedureDiagnosisCode);
  }

  /**
   * Write a single Medication to the tables.
   *
   * @param personID ID of the person prescribed the medication.
   * @param encounterID ID of the encounter where the medication was prescribed
   * @param encounter The encounter
   * @param medication The medication itself
   * @param primarySta3n The primary home sta3n for the patient
   * @throws IOException if any IO error occurs
   */
  private void medication(int personID, int encounterID, Encounter encounter,
      Medication medication, int primarySta3n) throws IOException {
    StringBuilder s = new StringBuilder();

    Integer sta3nValue = primarySta3n;
    Integer providerSID = (sidStart / 10_000);
    if (encounter.provider != null) {
      String state = Location.getStateName(encounter.provider.state);
      String tz = Location.getTimezoneByState(state);
      sta3nValue = sta3n.addFact(encounter.provider.id, clean(encounter.provider.name) + "," + tz);
      providerSID = (Integer) encounter.provider.attributes.get(CLINICIAN_SID);
    }
    Code code = medication.codes.get(0);

    // pharmacyOrderableItem ("PharmacyOrderableItemSID,PharmacyOrderableItem,SupplyFlag");
    int pharmSID = pharmacyOrderableItem.addFact(code.code, clean(code.display) + ",1");

    // dosageForm.setHeader("DosageFormSID,DosageFormIEN,DosageForm");
    Integer dosageSID = 1; // Default Dosage SID
    if (medication.prescriptionDetails != null
        && medication.prescriptionDetails.has("dosage")) {
      JsonObject dosage = medication.prescriptionDetails.get("dosage").getAsJsonObject();
      s.setLength(0);
      s.append(dosage.get("amount").getAsInt());
      s.append(" dose(s) ");
      s.append(dosage.get("frequency").getAsInt());
      s.append(" time(s) per ");
      s.append(dosage.get("period").getAsInt());
      s.append(" ");
      s.append(dosage.get("unit").getAsString());
      dosageSID = dosageForm.addFact(code.code, pharmSID + "," + s.toString());
    }

    // nationalDrug.setHeader("NationalDrugSID,DrugNameWithDose,DosageFormSID,"
    //    + "InactivationDate,VUID");
    s.setLength(0);
    s.append(clean(code.display));
    s.append(',');
    s.append(dosageSID);
    s.append(",,");
    s.append(code.code);
    int ndrugSID = nationalDrug.addFact(code.code, s.toString());

    // localDrug.setHeader("LocalDrugSID,LocalDrugIEN,Sta3n,LocalDrugNameWithDose,"
    //    + "NationalDrugSID,NationalDrugNameWithDose,PharmacyOrderableItemSID");
    s.setLength(0);
    s.append(ndrugSID).append(',');
    s.append(sta3nValue).append(',');
    s.append(clean(code.display)).append(',');
    s.append(ndrugSID).append(',');
    s.append(clean(code.display)).append(',');
    s.append(pharmSID);
    final int ldrugSID = localDrug.addFact(code.code, s.toString());

    // rxoutpatient.write("RxOutpatSID,Sta3n,RxNumber,IssueDate,CancelDate,FinishingDateTime,"
    //    + "PatientSID,ProviderSID,EnteredByStaffSID,LocalDrugSID,NationalDrugSID,"
    //    + "PharmacyOrderableItemSID,MaxRefills,RxStatus,OrderedQuantity");
    s.setLength(0);
    int rxNum = getNextKey(rxoutpatient);
    s.append(rxNum).append(',');
    s.append(sta3nValue).append(',');
    s.append(rxNum).append(',');
    s.append(iso8601Timestamp(medication.start)).append(',');
    if (medication.stop != 0L) {
      s.append(iso8601Timestamp(medication.stop));
    }
    s.append(',');
    if (medication.prescriptionDetails != null
        && medication.prescriptionDetails.has("duration")) {
      JsonObject duration = medication.prescriptionDetails.get("duration").getAsJsonObject();
      long time = Utilities.convertTime(
          duration.get("unit").getAsString(), duration.get("quantity").getAsLong());
      s.append(iso8601Timestamp(medication.start + time));
    }
    s.append(',');
    s.append(personID).append(',');
    s.append(providerSID).append(","); // Provider
    s.append(providerSID).append(","); // Entered by staff
    s.append(ldrugSID).append(',');
    s.append(ndrugSID).append(',');
    s.append(pharmSID).append(',');
    if (medication.prescriptionDetails != null
        && medication.prescriptionDetails.has("refills")) {
      s.append(medication.prescriptionDetails.get("refills").getAsInt());
    }
    s.append(',');
    if (medication.stop == 0L) {
      s.append("ACTIVE,");
    } else {
      s.append("EXPIRED,");
    }
    s.append(NEWLINE);
    write(s.toString(), rxoutpatient);

    // rxoutpatfill.write("RxOutpatFillSID,RxOutpatSID,Qty,DaysSupply");
    s.setLength(0);
    s.append(rxNum).append(',');
    s.append(rxNum).append(',');
    s.append("1,30");
    s.append(NEWLINE);
    write(s.toString(), rxoutpatfill);

    // cprsorder.write("CPRSOrderID,Sta3n,PatientSID,OrderStaffSID,EnteredByStaffSID,"
    //    + "EnteredDateTime,OrderStatusSID,VistaPackageSID,OrderStartDateTime,OrderStopDateTime,"
    //    + "PackageReference");
    int cprsSID = getNextKey(cprsorder);
    s.setLength(0);
    s.append(cprsSID).append(',');
    s.append(sta3nValue).append(',');
    s.append(personID).append(',');
    s.append(providerSID).append(","); // OrderStaffSID
    s.append(providerSID).append(","); // EnteredByStaffSID
    s.append(iso8601Timestamp(medication.start)).append(',');
    int orderStatusSID = -1;
    if (medication.stop != 0L) {
      orderStatusSID = orderStatus.addFact("EXPIRED", "EXPIRED");
    } else {
      orderStatusSID = orderStatus.addFact("ACTIVE", "ACTIVE");
    }
    s.append(orderStatusSID).append(',');
    s.append(vistaPackage.addFact("OUTPATIENT PHARMACY", "OUTPATIENT PHARMACY")).append(',');
    s.append(iso8601Timestamp(medication.start)).append(',');
    if (medication.stop != 0L) {
      s.append(iso8601Timestamp(medication.stop));
    }
    s.append(',');
    s.append("OUTPATIENT PHARMACY");
    s.append(NEWLINE);
    write(s.toString(), cprsorder);

    // orderableItem ("OrderableItemSID,OrderableItemName,IVBaseFlag,IVAdditiveFlag");
    int orderSID = orderableItem.addFact(code.code, clean(code.display) + ",0,0");

    // ordereditem.write("OrderedItemSID,CPRSOrderSID,OrderableItemSID");
    s.setLength(0);
    s.append(cprsSID).append(",");
    s.append(cprsSID).append(",");
    s.append(orderSID).append(NEWLINE);
    write(s.toString(), ordereditem);

    // nonvamed.write("NonVAMedSID,PatientSID,NonVAMedIEN,Sta3n,LocalDrugSID,Dosage,"
    //    + "MedicationRoute,Schedule,NonVAMedStatus,CPRSOrderSID,StartDateTime,"
    //    + "DocumentedDateTime,NonVAMedComments");
    s.setLength(0);
    int nonvamedSID = getNextKey(nonvamed);
    s.append(nonvamedSID).append(',');
    s.append(personID).append(',');
    s.append(nonvamedSID).append(',');
    s.append(sta3nValue).append(',');
    s.append(ldrugSID).append(',');
    if (dosageSID != null) {
      String fact = dosageForm.getFactById(dosageSID);
      s.append(fact.substring(fact.indexOf(',') + 1));
    }
    s.append(',');
    s.append("As directed by physician.,"); // MedicationRoute
    s.append("As directed by physician.,"); // Schedule
    s.append(orderStatus.getFactById(orderStatusSID)).append(',');
    s.append(cprsSID).append(',');
    s.append(iso8601Timestamp(medication.start)).append(',');
    s.append(iso8601Timestamp(medication.start)).append(',');
    s.append(clean(code.display));
    s.append(NEWLINE);
    write(s.toString(), nonvamed);
  }

  /**
   * Write a single Immunization to the tables.
   *
   * @param personID ID of the person on whom the immunization was performed.
   * @param person The person
   * @param encounterID ID of the encounter where the immunization was performed
   * @param encounter The encounter itself
   * @param immunization The immunization itself
   * @param primarySta3n The primary home sta3n for the patient
   * @throws IOException if any IO error occurs
   */
  private void immunization(int personID, Person person, int encounterID, Encounter encounter,
      Immunization immunizationEntry, int primarySta3n) throws IOException  {
    StringBuilder s = new StringBuilder();

    // immunization.write("ImmunizationSID,ImmunizationIEN,Sta3n,PatientSID,ImmunizationNameSID,"
    // + "Series,Reaction,VisitDateTime,ImmunizationDateTime,OrderingStaffSID,ImmunizingStaffSID,"
    // + "VisitSID,ImmunizationComments,ImmunizationRemarks");
    int immunizationSid = getNextKey(immunization);
    s.append(immunizationSid).append(',');
    s.append(immunizationSid).append(','); // ImmunizationIEN
    Integer providerSID = (sidStart / 10_000);
    if (encounter.provider != null) {
      String state = Location.getStateName(encounter.provider.state);
      String tz = Location.getTimezoneByState(state);
      s.append(sta3n.addFact(encounter.provider.id, clean(encounter.provider.name) + "," + tz));
      providerSID = (Integer) encounter.provider.attributes.get(CLINICIAN_SID);
    } else {
      s.append(primarySta3n);
    }
    s.append(',');
    s.append(personID).append(',');
    Code cvx = immunizationEntry.codes.get(0);
    int maxInSeries = Immunizations.getMaximumDoses(cvx.code);
    s.append(
        immunizationName.addFact(
            cvx.code, clean(cvx.display) + "," + cvx.code + "," + maxInSeries));
    int series = immunizationEntry.series;
    if (series == maxInSeries) {
      s.append(",C,");
    } else {
      s.append(",B,");
    }
    s.append(person.randInt(12)).append(','); // Reaction
    s.append(iso8601Timestamp(immunizationEntry.start)).append(',');
    s.append(iso8601Timestamp(immunizationEntry.start)).append(',');
    s.append(providerSID).append(","); // OrderingStaffSID
    s.append(providerSID).append(","); // ImmunizingStaffSID
    s.append(encounterID).append(',');
    // Comment
    s.append("Dose #" + series + " of " + maxInSeries + " of "
        + clean(cvx.display) + " vaccine administered.,");
    // Remark
    s.append("Dose #" + series + " of " + maxInSeries + " of "
        + clean(cvx.display) + " vaccine administered.");
    s.append(NEWLINE);
    write(s.toString(), immunization);
  }

  private int getNextKey(OutputStreamWriter table) {
    synchronized (sids) {
      return sids.computeIfAbsent(table, k -> new AtomicInteger(sidStart)).getAndIncrement();
    }
  }
  
  /**
   * Replaces commas and line breaks in the source string with a single space.
   * Null is replaced with the empty string.
   */
  private static String clean(String src) {
    if (src == null) {
      return "";
    } else {
      return src.replaceAll("\\r\\n|\\r|\\n|,", " ").trim();
    }
  }

  /**
   * Helper method to write a line to a File.
   * Extracted to a separate method here to make it a little easier to replace implementations.
   *
   * @param line The line to write
   * @param writer The place to write it
   * @throws IOException if an I/O error occurs
   */
  private static void write(String line, OutputStreamWriter writer) throws IOException {
    synchronized (writer) {
      writer.write(line);
      writer.flush();
    }
  }
}
