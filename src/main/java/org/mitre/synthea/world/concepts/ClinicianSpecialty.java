package org.mitre.synthea.world.concepts;

import java.util.Arrays;

/**
 * ClinicianSpecialty is an enumeration of Clinical Specialties that a
 * Clinician can possess. Each of the values (e.g. "ADDICTION MEDICINE")
 * corresponds to a column header in the primary care facilities file
 * identified by the Synthea property:
 * <p></p>
 * generate.providers.primarycare.default_file = "providers/primary_care_facilities.csv"
 * <p></p>The numeric value in each cell identifies how many of clinicians should be
 * generated with each specialty. These specialties can then be used to select
 * an appropriate clinician for any encounter.
 */
public class ClinicianSpecialty {

  public static final String GENERAL_PRACTICE = "General Practice";
  public static final String GENERAL_SURGERY = "General Surgery";
  public static final String ALLERGY_IMMUNOLOGY = "Allergy/ Immunology";
  public static final String OTOLARYNGOLOGY = "Otolaryngology";
  public static final String ANESTHESIOLOGY = "Anesthesiology";
  public static final String CARDIOVASCULAR_DISEASE = "Cardiovascular Disease (Cardiology)";
  public static final String DERMATOLOGY = "Dermatology";
  public static final String FAMILY_PRACTICE = "Family Practice";
  public static final String INTERVENTIONAL_PAIN_MANAGEMENT = "Interventional Pain Management";
  public static final String GASTROENTEROLOGY = "Gastroenterology";
  public static final String INTERNAL_MEDICINE = "Internal Medicine";
  public static final String OSTEOPATHIC_MANIPULATIVE_MEDICINE = "Osteopathic Manipulative Medicine";
  public static final String NEUROLOGY = "Neurology";
  public static final String NEUROSURGERY = "Neurosurgery";
  public static final String SPEECH_LANGUAGE_PATHOLOGIST = "Speech Language Pathologist";
  public static final String OBSTETRICS_GYNECOLOGY = "Obstetrics & Gynecology";
  public static final String HOSPICE_PALLIATIVE_CARE = "Hospice and Palliative Care";
  public static final String OPHTHALMOLOGY = "Ophthalmology";
  public static final String ORAL_SURGERY_DENTIST_ONLY = "Oral Surgery (Dentist only)";
  public static final String ORTHOPEDIC_SURGERY = "Orthopedic Surgery";
  public static final String CLINICAL_CARDIAC_ELECTROPHYSIOLOGY = "Clinical Cardiac Electrophysiology";
  public static final String PATHOLOGY = "Pathology";
  public static final String SPORTS_MEDICINE = "Sports Medicine";
  public static final String PLASTIC_RECONSTRUCTIVE_SURGERY = "Plastic and Reconstructive Surgery";
  public static final String PHYSICAL_MEDICINE_REHABILITATION = "Physical Medicine and Rehabilitation";
  public static final String PSYCHIATRY = "Psychiatry";
  public static final String GERIATRIC_PSYCHIATRY = "Geriatric Psychiatry";
  public static final String COLORECTAL_SURGERY_PROCTOLOGY = "Colorectal Surgery (Proctology)";
  public static final String PULMONARY_DISEASE = "Pulmonary Disease";
  public static final String DIAGNOSTIC_RADIOLOGY = "Diagnostic Radiology";
  public static final String INTENSIVE_CARDIAC_REHABILITATION = "Intensive Cardiac Rehabilitation";
  public static final String ANESTHESIOLOGY_ASSISTANT = "Anesthesiology Assistant";
  public static final String THORACIC_SURGERY = "Thoracic Surgery";
  public static final String UROLOGY = "Urology";
  public static final String CHIROPRACTIC = "Chiropractic";
  public static final String NUCLEAR_MEDICINE = "Nuclear Medicine";
  public static final String PEDIATRIC_MEDICINE = "Pediatric Medicine";
  public static final String GERIATRIC_MEDICINE = "Geriatric Medicine";
  public static final String NEPHROLOGY = "Nephrology";
  public static final String HAND_SURGERY = "Hand Surgery";
  public static final String OPTOMETRY = "Optometry";
  public static final String CERTIFIED_NURSE_MIDWIFE = "Certified Nurse Midwife";
  public static final String CERTIFIED_REGISTERED_NURSE_ANESTHETIST_CRNA = "Certified Registered Nurse Anesthetist (CRNA)";
  public static final String INFECTIOUS_DISEASE = "Infectious Disease";
  public static final String MAMMOGRAPHY_CENTER = "Mammography Center";
  public static final String ENDOCRINOLOGY = "Endocrinology";
  public static final String INDEPENDENT_DIAGNOSTIC_TESTING_FACILITY_IDTF = "Independent Diagnostic Testing Facility (IDTF)";
  public static final String PODIATRY = "Podiatry";
  public static final String AMBULATORY_SURGICAL_CENTER = "Ambulatory Surgical Center";
  public static final String NURSE_PRACTITIONER = "Nurse Practitioner";
  public static final String MEDICAL_SUPPLY_COMPANY_ORTHOTIST = "Medical Supply Company with Orthotist";
  public static final String MEDICAL_SUPPLY_COMPANY_PROSTHETIST = "Medical Supply Company with Prosthetist";
  public static final String MEDICAL_SUPPLY_COMPANY_ORTHOTIST_PROSTHETIST = "Medical Supply Company with Orthotist-Prosthetist";
  public static final String OTHER_MEDICAL_SUPPLY_COMPANY = "Other Medical Supply Company";
  public static final String INDIVIDUAL_CERTIFIED_ORTHOTIST = "Individual Certified Orthotist";
  public static final String INDIVIDUAL_CERTIFIED_PROSTHETIST = "Individual Certified Prosthetist";
  public static final String INDIVIDUAL_CERTIFIED_PROSTHETIST_ORTHOTIST = "Individual Certified Prosthetist-Orthotist";
  public static final String MEDICAL_SUPPLY_COMPANY_PHARMACIST = "Medical Supply Company with Pharmacist";
  public static final String AMBULANCE_SERVICE_PROVIDER = "Ambulance Service Provider";
  public static final String PUBLIC_HEALTH_WELFARE_AGENCY = "Public Health or Welfare Agency";
  public static final String VOLUNTARY_HEALTH_CHARITABLE_AGENCY = "Voluntary Health or Charitable Agency[1]";
  public static final String PSYCHOLOGIST_CLINICAL = "Psychologist, Clinical";
  public static final String PORTABLE_X_RAY_SUPPLIER = "Portable X-Ray Supplier";
  public static final String AUDIOLOGIST = "Audiologist";
  public static final String PHYSICAL_THERAPIST_PRIVATE_PRACTICE = "Physical Therapist in Private Practice";
  public static final String RHEUMATOLOGY = "Rheumatology";
  public static final String OCCUPATIONAL_THERAPIST_PRIVATE_PRACTICE = "Occupational Therapist in Private Practice";
  public static final String CLINICAL_LABORATORY = "Clinical Laboratory";
  public static final String CLINIC_GROUP_PRACTICE = "Clinic or Group Practice";
  public static final String REGISTERED_DIETITIAN_NUTRITION_PROFESSIONAL = "Registered Dietitian or Nutrition Professional";
  public static final String PAIN_MANAGEMENT = "Pain Management";
  public static final String MASS_IMMUNIZER_ROSTER_BILLER = "Mass Immunizer Roster Biller[2]";
  public static final String RADIATION_THERAPY_CENTER = "Radiation Therapy Center";
  public static final String SLIDE_PREPARATION_FACILITY = "Slide Preparation Facility";
  public static final String PERIPHERAL_VASCULAR_DISEASE = "Peripheral Vascular Disease";
  public static final String VASCULAR_SURGERY = "Vascular Surgery";
  public static final String CARDIAC_SURGERY = "Cardiac Surgery";
  public static final String ADDICTION_MEDICINE = "Addiction Medicine";
  public static final String LICENSED_CLINICAL_SOCIAL_WORKER = "Licensed Clinical Social Worker";
  public static final String CRITICAL_CARE_INTENSIVISTS = "Critical Care (Intensivists)";
  public static final String HEMATOLOGY = "Hematology";
  public static final String HEMATOLOGY_ONCOLOGY = "Hematology-Oncology";
  public static final String PREVENTIVE_MEDICINE = "Preventive Medicine";
  public static final String MAXILLOFACIAL_SURGERY = "Maxillofacial Surgery";
  public static final String NEUROPSYCHIATRY = "Neuropsychiatry";
  public static final String ALL_OTHER_SUPPLIERS = "All Other Suppliers";
  public static final String UNKNOWN_SUPPLIER_PROVIDER_SPECIALTY = "Unknown Supplier/Provider Specialty[4]";
  public static final String CERTIFIED_CLINICAL_NURSE_SPECIALIST = "Certified Clinical Nurse Specialist";
  public static final String MEDICAL_ONCOLOGY = "Medical Oncology";
  public static final String SURGICAL_ONCOLOGY = "Surgical Oncology";
  public static final String RADIATION_ONCOLOGY = "Radiation Oncology";
  public static final String EMERGENCY_MEDICINE = "Emergency Medicine";
  public static final String INTERVENTIONAL_RADIOLOGY = "Interventional Radiology";
  public static final String ADVANCE_DIAGNOSTIC_IMAGING = "Advance Diagnostic Imaging";
  public static final String OPTICIAN = "Optician";
  public static final String PHYSICIAN_ASSISTANT = "Physician Assistant";
  public static final String GYNECOLOGICAL_ONCOLOGY = "Gynecological Oncology";
  public static final String UNDEFINED_PHYSICIAN_TYPE = "Undefined Physician type[6]";
  public static final String HOSPITAL_GENERAL = "Hospital-General";
  public static final String HOSPITAL_ACUTE_CARE = "Hospital-Acute Care";
  public static final String HOSPITAL_CHILDRENS_PPS_EXCLUDED = "Hospital-Children's (PPS excluded)";
  public static final String HOSPITAL_LONG_TERM_PPS_EXCLUDED = "Hospital-Long-Term (PPS excluded)";
  public static final String HOSPITAL_PSYCHIATRIC_PPS_EXCLUDED = "Hospital-Psychiatric (PPS excluded)";
  public static final String HOSPITAL_REHABILITATION_PPS_EXCLUDED = "Hospital-Rehabilitation (PPS excluded)";
  public static final String HOSPITAL_SHORT_TERM_GENERAL_SPECIALTY = "Hospital-Short-Term (General and Specialty)";
  public static final String HOSPITAL_UNITS = "Hospital Units";
  public static final String HOSPITALS = "Hospitals";
  public static final String HOSPITAL_SWING_BED_APPROVED = "Hospital-Swing Bed Approved";
  public static final String HOSPITAL_PSYCHIATRIC_UNIT = "Hospital-Psychiatric Unit";
  public static final String HOSPITAL_REHABILITATION_UNIT = "Hospital-Rehabilitation Unit";
  public static final String HOSPITAL_SPECIALTY_HOSPITAL = "Hospital-Specialty Hospital (cardiac, orthopedic, surgical)";
  public static final String CRITICAL_ACCESS_HOSPITAL = "Critical Access Hospital";
  public static final String SKILLED_NURSING_FACILITY = "Skilled Nursing Facility";
  public static final String INTERMEDIATE_CARE_NURSING_FACILITY = "Intermediate Care Nursing Facility";
  public static final String OTHER_NURSING_FACILITY = "Other Nursing Facility";
  public static final String HOME_HEALTH_AGENCY = "Home Health Agency";
  public static final String HOME_HEALTH_AGENCY_SUBUNIT = "Home Health Agency (Subunit)";
  public static final String PHARMACY = "Pharmacy";
  public static final String MEDICAL_SUPPLY_COMPANY_RESPIRATORY_THERAPIST = "Medical Supply Company with Respiratory Therapist";
  public static final String DEPARTMENT_STORE = "Department Store";
  public static final String GROCERY_STORE = "Grocery Store";
  public static final String INDIAN_HEALTH_SERVICE_FACILITY = "Indian Health Service facility[13]";
  public static final String OXYGEN_SUPPLIER = "Oxygen supplier";
  public static final String PEDORTHIC_PERSONNEL = "Pedorthic personnel";
  public static final String MEDICAL_SUPPLY_COMPANY_PEDORTHIC_PERSONNEL = "Medical supply company with pedorthic personnel";
  public static final String REHABILITATION_AGENCY = "Rehabilitation Agency";
  public static final String ORGAN_PROCUREMENT_ORGANIZATION = "Organ Procurement Organization";
  public static final String COMMUNITY_MENTAL_HEALTH_CENTER = "Community Mental Health Center";
  public static final String COMPREHENSIVE_OUTPATIENT_REHABILITATION_FACILITY = "Comprehensive Outpatient Rehabilitation Facility";
  public static final String END_STAGE_RENAL_DISEASE_FACILITY = "End-Stage Renal Disease Facility";
  public static final String FEDERALLY_QUALIFIED_HEALTH_CENTER = "Federally Qualified Health Center";
  public static final String HOSPICE = "Hospice";
  public static final String HISTOCOMPATIBILITY_LABORATORY = "Histocompatibility Laboratory";
  public static final String OUTPATIENT_PHYSICAL_THERAPY_OCCUPATIONAL_THERAPY_SPEECH_PATHOLOGY_SERVICES = "Outpatient Physical Therapy/Occupational Therapy/Speech Pathology Services";
  public static final String RELIGIOUS_NON_MEDICAL_HEALTH_CARE_INSTITUTION = "Religious Non-Medical Health Care Institution";
  public static final String RURAL_HEALTH_CLINIC = "Rural Health Clinic";
  public static final String OCULARIST = "Ocularist";
  public static final String SLEEP_MEDICINE = "Sleep Medicine";
  public static final String INTERVENTIONAL_CARDIOLOGY = "Interventional Cardiology";
  public static final String DENTIST = "Dentist";
  public static final String HOSPITALIST = "Hospitalist";
  public static final String ADVANCED_HEART_FAILURE_TRANSPLANT_CARDIOLOGY = "Advanced Heart Failure and Transplant Cardiology";
  public static final String MEDICAL_TOXICOLOGY = "Medical Toxicology";
  public static final String HEMATOPOIETIC_CELL_TRANSPLANTATION_CELLULAR_THERAPY = "Hematopoietic Cell Transplantation and Cellular Therapy";
  public static final String MEDICARE_DIABETES_PREVENTIVE_PROGRAM = "Medicare Diabetes Preventive Program";
  public static final String MEDICAL_GENETICS_GENOMICS = "Medical Genetics and Genomics";
  public static final String UNDERSEA_HYPERBARIC_MEDICINE = "Undersea and Hyperbaric Medicine";
  public static final String OPIOID_TREATMENT_PROGRAM = "Opioid Treatment Program";
  public static final String HOME_INFUSION_THERAPY_SERVICES = "Home Infusion Therapy Services";
  public static final String MICROGRAPHIC_DERMATOLOGIC_SURGERY = "Micrographic Dermatologic Surgery";
  public static final String ADULT_CONGENITAL_HEART_DISEASE = "Adult Congenital Heart Disease";

  /**
   * Get the complete list of all specialties defined in the system.
   *
   * @return List of Strings defined each clinical specialty.
   */
  public static String[] getSpecialties() {
    String[] specialtyList = {"General Practice", "General Surgery", "Allergy/ Immunology",
      "Otolaryngology", "Anesthesiology", "Cardiovascular Disease (Cardiology)", "Dermatology",
      "Family Practice", "Interventional Pain Management", "Gastroenterology", "Internal Medicine",
      "Osteopathic Manipulative Medicine", "Neurology", "Neurosurgery",
      "Speech Language Pathologist", "Obstetrics & Gynecology", "Hospice and Palliative Care",
      "Ophthalmology", "Oral Surgery (Dentist only)", "Orthopedic Surgery",
      "Clinical Cardiac Electrophysiology", "Pathology", "Sports Medicine",
      "Plastic and Reconstructive Surgery", "Physical Medicine and Rehabilitation", "Psychiatry",
      "Geriatric Psychiatry", "Colorectal Surgery (Proctology)", "Pulmonary Disease",
      "Diagnostic Radiology", "Intensive Cardiac Rehabilitation", "Anesthesiology Assistant",
      "Thoracic Surgery", "Urology", "Chiropractic", "Nuclear Medicine", "Pediatric Medicine",
      "Geriatric Medicine", "Nephrology", "Hand Surgery", "Optometry", "Certified Nurse Midwife",
      "Certified Registered Nurse Anesthetist (CRNA)", "Infectious Disease", "Mammography Center",
      "Endocrinology", "Independent Diagnostic Testing Facility (IDTF)", "Podiatry",
      "Ambulatory Surgical Center", "Nurse Practitioner", "Medical Supply Company with Orthotist",
      "Medical Supply Company with Prosthetist",
      "Medical Supply Company with Orthotist-Prosthetist", "Other Medical Supply Company",
      "Individual Certified Orthotist", "Individual Certified Prosthetist",
      "Individual Certified Prosthetist-Orthotist", "Medical Supply Company with Pharmacist",
      "Ambulance Service Provider", "Public Health or Welfare Agency",
      "Voluntary Health or Charitable Agency[1]", "Psychologist, Clinical",
      "Portable X-Ray Supplier", "Audiologist", "Physical Therapist in Private Practice",
      "Rheumatology", "Occupational Therapist in Private Practice", "Clinical Laboratory",
      "Clinic or Group Practice", "Registered Dietitian or Nutrition Professional",
      "Pain Management", "Mass Immunizer Roster Biller[2]", "Radiation Therapy Center",
      "Slide Preparation Facility", "Peripheral Vascular Disease", "Vascular Surgery",
      "Cardiac Surgery", "Addiction Medicine", "Licensed Clinical Social Worker",
      "Critical Care (Intensivists)", "Hematology", "Hematology-Oncology", "Preventive Medicine",
      "Maxillofacial Surgery", "Neuropsychiatry", "All Other Suppliers",
      "Unknown Supplier/Provider Specialty[4]", "Certified Clinical Nurse Specialist",
      "Medical Oncology", "Surgical Oncology", "Radiation Oncology", "Emergency Medicine",
      "Interventional Radiology", "Advance Diagnostic Imaging", "Optician", "Physician Assistant",
      "Gynecological Oncology", "Undefined Physician type[6]", "Hospital-General",
      "Hospital-Acute Care", "Hospital-Children's (PPS excluded)",
      "Hospital-Long-Term (PPS excluded)", "Hospital-Psychiatric (PPS excluded)",
      "Hospital-Rehabilitation (PPS excluded)", "Hospital-Short-Term (General and Specialty)",
      "Hospital Units", "Hospitals", "Hospital-Swing Bed Approved", "Hospital-Psychiatric Unit",
      "Hospital-Rehabilitation Unit", "Hospital-Specialty Hospital (cardiac, orthopedic, surgical)",
      "Critical Access Hospital", "Skilled Nursing Facility", "Intermediate Care Nursing Facility",
      "Other Nursing Facility", "Home Health Agency", "Home Health Agency (Subunit)", "Pharmacy",
      "Medical Supply Company with Respiratory Therapist", "Department Store", "Grocery Store",
      "Indian Health Service facility[13]", "Oxygen supplier", "Pedorthic personnel",
      "Medical supply company with pedorthic personnel", "Rehabilitation Agency",
      "Organ Procurement Organization", "Community Mental Health Center",
      "Comprehensive Outpatient Rehabilitation Facility", "End-Stage Renal Disease Facility",
      "Federally Qualified Health Center", "Hospice", "Histocompatibility Laboratory",
      "Outpatient Physical Therapy/Occupational Therapy/Speech Pathology Services",
      "Religious Non-Medical Health Care Institution", "Rural Health Clinic", "Ocularist",
      "Sleep Medicine", "Interventional Cardiology", "Dentist", "Hospitalist",
      "Advanced Heart Failure and Transplant Cardiology", "Medical Toxicology",
      "Hematopoietic Cell Transplantation and Cellular Therapy",
      "Medicare Diabetes Preventive Program", "Medical Genetics and Genomics",
      "Undersea and Hyperbaric Medicine", "Opioid Treatment Program",
      "Home Infusion Therapy Services", "Micrographic Dermatologic Surgery",
      "Adult Congenital Heart Disease"};
    return specialtyList;
  }

  /**
   * Checks whether the provided speciality is in the list of clinical specialties.
   *
   * @param specialty to check
   * @return true if it is in the list, false otherwise
   */
  public static boolean validateSpecialty(String specialty) {
    return Arrays.asList(getSpecialties()).contains(specialty);
  }

  /**
   * Get the CMS Provider specialty code for CMS Billing.
   *
   * @param specialty One of the static Provider Specialties.
   * @return A two digit alpha-numeric CMS Provider Specialty code.
   */
  public static String getCMSProviderSpecialtyCode(String specialty) {
    switch (specialty) {
      case "General Practice":
        return "01";
      case "General Surgery":
        return "02";
      case "Allergy/ Immunology":
        return "03";
      case "Otolaryngology":
        return "04";
      case "Anesthesiology":
        return "05";
      case "Cardiovascular Disease (Cardiology)":
        return "06";
      case "Dermatology":
        return "07";
      case "Family Practice":
        return "08";
      case "Interventional Pain Management":
        return "09";
      case "Gastroenterology":
        return "10";
      case "Internal Medicine":
        return "11";
      case "Osteopathic Manipulative Medicine":
        return "12";
      case "Neurology":
        return "13";
      case "Neurosurgery":
        return "14";
      case "Speech Language Pathologist":
        return "15";
      case "Obstetrics & Gynecology":
        return "16";
      case "Hospice and Palliative Care":
        return "17";
      case "Ophthalmology":
        return "18";
      case "Oral Surgery (Dentist only)":
        return "19";
      case "Orthopedic Surgery":
        return "20";
      case "Clinical Cardiac Electrophysiology":
        return "21";
      case "Pathology":
        return "22";
      case "Sports Medicine":
        return "23";
      case "Plastic and Reconstructive Surgery":
        return "24";
      case "Physical Medicine and Rehabilitation":
        return "25";
      case "Psychiatry":
        return "26";
      case "Geriatric Psychiatry":
        return "27";
      case "Colorectal Surgery (Proctology)":
        return "28";
      case "Pulmonary Disease":
        return "29";
      case "Diagnostic Radiology":
        return "30";
      case "Intensive Cardiac Rehabilitation":
        return "31";
      case "Anesthesiology Assistant":
        return "32";
      case "Thoracic Surgery":
        return "33";
      case "Urology":
        return "34";
      case "Chiropractic":
        return "35";
      case "Nuclear Medicine":
        return "36";
      case "Pediatric Medicine":
        return "37";
      case "Geriatric Medicine":
        return "38";
      case "Nephrology":
        return "39";
      case "Hand Surgery":
        return "40";
      case "Optometry":
        return "41";
      case "Certified Nurse Midwife":
        return "42";
      case "Certified Registered Nurse Anesthetist (CRNA)":
        return "43";
      case "Infectious Disease":
        return "44";
      case "Mammography Center":
        return "45";
      case "Endocrinology":
        return "46";
      case "Independent Diagnostic Testing Facility (IDTF)":
        return "47";
      case "Podiatry":
        return "48";
      case "Ambulatory Surgical Center":
        return "49";
      case "Nurse Practitioner":
        return "50";
      case "Medical Supply Company with Orthotist":
        return "51";
      case "Medical Supply Company with Prosthetist":
        return "52";
      case "Medical Supply Company with Orthotist-Prosthetist":
        return "53";
      case "Other Medical Supply Company":
        return "54";
      case "Individual Certified Orthotist":
        return "55";
      case "Individual Certified Prosthetist":
        return "56";
      case "Individual Certified Prosthetist-Orthotist":
        return "57";
      case "Medical Supply Company with Pharmacist":
        return "58";
      case "Ambulance Service Provider":
        return "59";
      case "Public Health or Welfare Agency":
        return "60";
      case "Voluntary Health or Charitable Agency[1]":
        return "61";
      case "Psychologist, Clinical":
        return "62";
      case "Portable X-Ray Supplier":
        return "63";
      case "Audiologist":
        return "64";
      case "Physical Therapist in Private Practice":
        return "65";
      case "Rheumatology":
        return "66";
      case "Occupational Therapist in Private Practice":
        return "67";
      case "Clinical Laboratory":
        return "69";
      case "Clinic or Group Practice":
        return "70";
      case "Registered Dietitian or Nutrition Professional":
        return "71";
      case "Pain Management":
        return "72";
      case "Mass Immunizer Roster Biller[2]":
        return "73";
      case "Radiation Therapy Center":
        return "74";
      case "Slide Preparation Facility":
        return "75";
      case "Peripheral Vascular Disease":
        return "76";
      case "Vascular Surgery":
        return "77";
      case "Cardiac Surgery":
        return "78";
      case "Addiction Medicine":
        return "79";
      case "Licensed Clinical Social Worker":
        return "80";
      case "Critical Care (Intensivists)":
        return "81";
      case "Hematology":
        return "82";
      case "Hematology-Oncology":
        return "83";
      case "Preventive Medicine":
        return "84";
      case "Maxillofacial Surgery":
        return "85";
      case "Neuropsychiatry":
        return "86";
      case "All Other Suppliers":
        return "87[3]";
      case "Unknown Supplier/Provider Specialty[4]":
        return "88";
      case "Certified Clinical Nurse Specialist":
        return "89";
      case "Medical Oncology":
        return "90";
      case "Surgical Oncology":
        return "91";
      case "Radiation Oncology":
        return "92";
      case "Emergency Medicine":
        return "93";
      case "Interventional Radiology":
        return "94";
      case "Advance Diagnostic Imaging":
        return "95";
      case "Optician":
        return "96[5]";
      case "Physician Assistant":
        return "97";
      case "Gynecological Oncology":
        return "98";
      case "Undefined Physician type[6]":
        return "99";
      case "Hospital-General":
        return "A0[7]";
      case "Hospital-Acute Care":
        return "A0[7]";
      case "Hospital-Children's (PPS excluded)":
        return "A0[7]";
      case "Hospital-Long-Term (PPS excluded)":
        return "A0[7]";
      case "Hospital-Psychiatric (PPS excluded)":
        return "A0[7]";
      case "Hospital-Rehabilitation (PPS excluded)":
        return "A0[7]";
      case "Hospital-Short-Term (General and Specialty)":
        return "A0[7]";
      case "Hospital Units":
        return "A0[7]";
      case "Hospitals":
        return "A0[7]";
      case "Hospital-Swing Bed Approved":
        return "A0[7]";
      case "Hospital-Psychiatric Unit":
        return "A0[7]";
      case "Hospital-Rehabilitation Unit":
        return "A0[7]";
      case "Hospital-Specialty Hospital (cardiac, orthopedic, surgical)":
        return "A0[7]";
      case "Critical Access Hospital":
        return "A0[7]";
      case "Skilled Nursing Facility":
        return "A1[8]";
      case "Intermediate Care Nursing Facility":
        return "A2[9]";
      case "Other Nursing Facility":
        return "A3[10]";
      case "Home Health Agency":
        return "A4[11]";
      case "Home Health Agency (Subunit)":
        return "A4[11]";
      case "Pharmacy":
        return "A5";
      case "Medical Supply Company with Respiratory Therapist":
        return "A6";
      case "Department Store":
        return "A7";
      case "Grocery Store":
        return "A8";
      case "Indian Health Service facility[13]":
        return "A9[12]";
      case "Oxygen supplier":
        return "B1";
      case "Pedorthic personnel":
        return "B2";
      case "Medical supply company with pedorthic personnel":
        return "B3";
      case "Rehabilitation Agency":
        return "B4[14]";
      case "Organ Procurement Organization":
        return "B4[14]";
      case "Community Mental Health Center":
        return "B4[14]";
      case "Comprehensive Outpatient Rehabilitation Facility":
        return "B4[14]";
      case "End-Stage Renal Disease Facility":
        return "B4[14]";
      case "Federally Qualified Health Center":
        return "B4[14]";
      case "Hospice":
        return "B4[14]";
      case "Histocompatibility Laboratory":
        return "B4[14]";
      case "Outpatient Physical Therapy/Occupational Therapy/Speech Pathology Services":
        return "B4[14]";
      case "Religious Non-Medical Health Care Institution":
        return "B4[14]";
      case "Rural Health Clinic":
        return "B4[14]";
      case "Ocularist":
        return "B5";
      case "Sleep Medicine":
        return "C0";
      case "Interventional Cardiology":
        return "C3";
      case "Dentist":
        return "C5";
      case "Hospitalist":
        return "C6";
      case "Advanced Heart Failure and Transplant Cardiology":
        return "C7";
      case "Medical Toxicology":
        return "C8";
      case "Hematopoietic Cell Transplantation and Cellular Therapy":
        return "C9";
      case "Medicare Diabetes Preventive Program":
        return "D1";
      case "Medical Genetics and Genomics":
        return "D3";
      case "Undersea and Hyperbaric Medicine":
        return "D4";
      case "Opioid Treatment Program":
        return "D5";
      case "Home Infusion Therapy Services":
        return "D6";
      case "Micrographic Dermatologic Surgery":
        return "D7";
      case "Adult Congenital Heart Disease":
        return "D8";
      default:
        return "01"; // general practice
    }
  }
}