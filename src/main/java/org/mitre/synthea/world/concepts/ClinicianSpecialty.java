package org.mitre.synthea.world.concepts;

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

  public static final String ADDICTION_MEDICINE = "ADDICTION MEDICINE";
  public static final String ADVANCED_HEART_FAILURE_AND_TRANSPLANT_CARDIOLOGY
      = "ADVANCED HEART FAILURE AND TRANSPLANT CARDIOLOGY";
  public static final String ALLERGY_IMMUNOLOGY = "ALLERGY/IMMUNOLOGY";
  public static final String ANESTHESIOLOGY = "ANESTHESIOLOGY";
  public static final String ANESTHESIOLOGY_ASSISTANT = "ANESTHESIOLOGY ASSISTANT";
  public static final String AUDIOLOGIST = "AUDIOLOGIST";
  public static final String CARDIAC_ELECTROPHYSIOLOGY = "CARDIAC ELECTROPHYSIOLOGY";
  public static final String CARDIAC_SURGERY = "CARDIAC SURGERY";
  public static final String CARDIOLOGY = "CARDIOVASCULAR DISEASE (CARDIOLOGY)";
  public static final String CERTIFIED_NURSE_MIDWIFE = "CERTIFIED NURSE MIDWIFE";
  public static final String CERTIFIED_REGISTERED_NURSE_ANESTHETIST
      = "CERTIFIED REGISTERED NURSE ANESTHETIST";
  public static final String CHIROPRACTIC = "CHIROPRACTIC";
  public static final String CLINICAL_NURSE_SPECIALIST = "CLINICAL NURSE SPECIALIST";
  public static final String CLINICAL_PSYCHOLOGIST = "CLINICAL PSYCHOLOGIST";
  public static final String CLINICAL_SOCIAL_WORKER = "CLINICAL SOCIAL WORKER";
  public static final String PROCTOLOGY = "COLORECTAL SURGERY (PROCTOLOGY)";
  public static final String INTENSIVIST = "CRITICAL CARE (INTENSIVISTS)";
  public static final String DENTIST = "DENTIST";
  public static final String DERMATOLOGY = "DERMATOLOGY";
  public static final String DIAGNOSTIC_RADIOLOGY = "DIAGNOSTIC RADIOLOGY";
  public static final String EMERGENCY_MEDICINE = "EMERGENCY MEDICINE";
  public static final String ENDOCRINOLOGY = "ENDOCRINOLOGY";
  public static final String FAMILY_PRACTICE = "FAMILY PRACTICE";
  public static final String GASTROENTEROLOGY = "GASTROENTEROLOGY";
  public static final String GENERAL_PRACTICE = "GENERAL PRACTICE";
  public static final String GENERAL_SURGERY = "GENERAL SURGERY";
  public static final String GERIATRIC_MEDICINE = "GERIATRIC MEDICINE";
  public static final String GERIATRIC_PSYCHIATRY = "GERIATRIC PSYCHIATRY";
  public static final String GYNECOLOGICAL_ONCOLOGY = "GYNECOLOGICAL ONCOLOGY";
  public static final String HAND_SURGERY = "HAND SURGERY";
  public static final String HEMATOLOGY = "HEMATOLOGY";
  public static final String HEMATOLOGY_ONCOLOGY = "HEMATOLOGY/ONCOLOGY";
  public static final String HEMATOPOIETIC_CELL_TRANSPLANTATION_AND_CELLULAR_TH
      = "HEMATOPOIETIC CELL TRANSPLANTATION AND CELLULAR TH";
  public static final String HOSPICE_AND_PALLIATIVE_CARE = "HOSPICE/PALLIATIVE CARE";
  public static final String HOSPITALIST = "HOSPITALIST";
  public static final String INFECTIOUS_DISEASE = "INFECTIOUS DISEASE";
  public static final String INTERNAL_MEDICINE = "INTERNAL MEDICINE";
  public static final String INTERVENTIONAL_CARDIOLOGY = "INTERVENTIONAL CARDIOLOGY";
  public static final String INTERVENTIONAL_PAIN_MANAGEMENT = "INTERVENTIONAL PAIN MANAGEMENT";
  public static final String INTERVENTIONAL_RADIOLOGY = "INTERVENTIONAL RADIOLOGY";
  public static final String MAXILLOFACIAL_SURGERY = "MAXILLOFACIAL SURGERY";
  public static final String MEDICAL_ONCOLOGY = "MEDICAL ONCOLOGY";
  public static final String NEPHROLOGY = "NEPHROLOGY";
  public static final String NEUROLOGY = "NEUROLOGY";
  public static final String NEUROPSYCHIATRY = "NEUROPSYCHIATRY";
  public static final String NEUROSURGERY = "NEUROSURGERY";
  public static final String NUCLEAR_MEDICINE = "NUCLEAR MEDICINE";
  public static final String NURSE_PRACTITIONER = "NURSE PRACTITIONER";
  public static final String OBSTETRICS_GYNECOLOGY = "OBSTETRICS/GYNECOLOGY";
  public static final String OCCUPATIONAL_THERAPY = "OCCUPATIONAL THERAPY";
  public static final String OPHTHALMOLOGY = "OPHTHALMOLOGY";
  public static final String OPTOMETRY = "OPTOMETRY";
  public static final String ORAL_SURGERY = "ORAL SURGERY";
  public static final String ORTHOPEDIC_SURGERY = "ORTHOPEDIC SURGERY";
  public static final String OSTEOPATHIC_MANIPULATIVE_MEDICINE
      = "OSTEOPATHIC MANIPULATIVE MEDICINE";
  public static final String OTOLARYNGOLOGY = "OTOLARYNGOLOGY";
  public static final String PAIN_MANAGEMENT = "PAIN MANAGEMENT";
  public static final String PATHOLOGY = "PATHOLOGY";
  public static final String PEDIATRIC_MEDICINE = "PEDIATRIC MEDICINE";
  public static final String PERIPHERAL_VASCULAR_DISEASE = "PERIPHERAL VASCULAR DISEASE";
  public static final String PHYSICAL_MEDICINE_AND_REHABILITATION
      = "PHYSICAL MEDICINE AND REHABILITATION";
  public static final String PHYSICAL_THERAPY = "PHYSICAL THERAPY";
  public static final String PHYSICIAN_ASSISTANT = "PHYSICIAN ASSISTANT";
  public static final String PLASTIC_AND_RECONSTRUCTIVE_SURGERY
      = "PLASTIC AND RECONSTRUCTIVE SURGERY";
  public static final String PODIATRY = "PODIATRY";
  public static final String PREVENTATIVE_MEDICINE = "PREVENTATIVE MEDICINE";
  public static final String PSYCHIATRY = "PSYCHIATRY";
  public static final String PULMONARY_DISEASE = "PULMONARY DISEASE";
  public static final String RADIATION_ONCOLOGY = "RADIATION ONCOLOGY";
  public static final String REGISTERED_DIETITIAN_OR_NUTRITION_PROFESSIONAL
      = "REGISTERED DIETITIAN OR NUTRITION PROFESSIONAL";
  public static final String RHEUMATOLOGY = "RHEUMATOLOGY";
  public static final String SLEEP_MEDICINE = "SLEEP MEDICINE";
  public static final String SPEECH_LANGUAGE_PATHOLOGIST = "SPEECH LANGUAGE PATHOLOGIST";
  public static final String SPORTS_MEDICINE = "SPORTS MEDICINE";
  public static final String SURGICAL_ONCOLOGY = "SURGICAL ONCOLOGY";
  public static final String THORACIC_SURGERY = "THORACIC SURGERY";
  public static final String UNDEFINED = "UNDEFINED PHYSICIAN TYPE (SPECIFY)";
  public static final String UROLOGY = "UROLOGY";
  public static final String VASCULAR_SURGERY = "VASCULAR SURGERY";

  /**
   * Get the complete list of all specialties defined in the system.
   * @return List of Strings defined each clinical specialty.
   */
  public static String[] getSpecialties() {
    String[] specialtyList = {"ADDICTION MEDICINE",
      "ADVANCED HEART FAILURE AND TRANSPLANT CARDIOLOGY", "ALLERGY/IMMUNOLOGY", "ANESTHESIOLOGY",
      "ANESTHESIOLOGY ASSISTANT", "AUDIOLOGIST","CARDIAC ELECTROPHYSIOLOGY","CARDIAC SURGERY",
      "CARDIOVASCULAR DISEASE (CARDIOLOGY)", "CERTIFIED NURSE MIDWIFE",
      "CERTIFIED REGISTERED NURSE ANESTHETIST", "CHIROPRACTIC", "CLINICAL NURSE SPECIALIST",
      "CLINICAL PSYCHOLOGIST", "CLINICAL SOCIAL WORKER", "COLORECTAL SURGERY (PROCTOLOGY)",
      "CRITICAL CARE (INTENSIVISTS)", "DENTIST", "DERMATOLOGY",
      "DIAGNOSTIC RADIOLOGY","EMERGENCY MEDICINE", "ENDOCRINOLOGY", "FAMILY PRACTICE",
      "GASTROENTEROLOGY", "GENERAL PRACTICE", "GENERAL SURGERY", "GERIATRIC MEDICINE",
      "GERIATRIC PSYCHIATRY", "GYNECOLOGICAL ONCOLOGY", "HAND SURGERY", "HEMATOLOGY",
      "HEMATOLOGY/ONCOLOGY", "HEMATOPOIETIC CELL TRANSPLANTATION AND CELLULAR TH",
      "HOSPICE/PALLIATIVE CARE", "HOSPITALIST", "INFECTIOUS DISEASE","INTERNAL MEDICINE",
      "INTERVENTIONAL CARDIOLOGY", "INTERVENTIONAL PAIN MANAGEMENT", "INTERVENTIONAL RADIOLOGY",
      "MAXILLOFACIAL SURGERY", "MEDICAL ONCOLOGY", "NEPHROLOGY", "NEUROLOGY", "NEUROPSYCHIATRY",
      "NEUROSURGERY", "NUCLEAR MEDICINE", "NURSE PRACTITIONER", "OBSTETRICS/GYNECOLOGY",
      "OCCUPATIONAL THERAPY", "OPHTHALMOLOGY", "OPTOMETRY", "ORAL SURGERY", "ORTHOPEDIC SURGERY",
      "OSTEOPATHIC MANIPULATIVE MEDICINE", "OTOLARYNGOLOGY", "PAIN MANAGEMENT",
      "PATHOLOGY", "PEDIATRIC MEDICINE", "PERIPHERAL VASCULAR DISEASE",
      "PHYSICAL MEDICINE AND REHABILITATION","PHYSICAL THERAPY", "PHYSICIAN ASSISTANT",
      "PLASTIC AND RECONSTRUCTIVE SURGERY", "PODIATRY", "PREVENTATIVE MEDICINE","PSYCHIATRY",
      "PULMONARY DISEASE", "RADIATION ONCOLOGY", "REGISTERED DIETITIAN OR NUTRITION PROFESSIONAL",
      "RHEUMATOLOGY", "SLEEP MEDICINE", "SPEECH LANGUAGE PATHOLOGIST", "SPORTS MEDICINE",
      "SURGICAL ONCOLOGY", "THORACIC SURGERY", "UNDEFINED PHYSICIAN TYPE (SPECIFY)",
      "UROLOGY", "VASCULAR SURGERY"};

    return specialtyList;
  }

  /**
   * Get the CMS Provider specialty code for CMS Billing.
   * @param specialty One of the static Provider Specialties.
   * @return A two digit alpha-numeric CMS Provider Specialty code.
   */
  public static String getCMSProviderSpecialtyCode(String specialty) {
    switch (specialty) {
      case "ADDICTION MEDICINE":
        return "79";
      case "ADVANCED HEART FAILURE AND TRANSPLANT CARDIOLOGY":
        return "C7";
      case "ALLERGY/IMMUNOLOGY":
        return "03";
      case "ANESTHESIOLOGY":
        return "05";
      case "ANESTHESIOLOGY ASSISTANT":
        return "32";
      case "AUDIOLOGIST":
        return "64";
      case "CARDIAC ELECTROPHYSIOLOGY":
        return "21";
      case "CARDIAC SURGERY":
        return "78";
      case "CARDIOVASCULAR DISEASE (CARDIOLOGY)":
        return "06";
      case "CERTIFIED NURSE MIDWIFE":
        return "42";
      case "CERTIFIED REGISTERED NURSE ANESTHETIST":
        return "43";
      case "CHIROPRACTIC":
        return "35";
      case "CLINICAL NURSE SPECIALIST":
        return "89";
      case "CLINICAL PSYCHOLOGIST":
        return "68";
      case "CLINICAL SOCIAL WORKER":
        return "80";
      case "COLORECTAL SURGERY (PROCTOLOGY)":
        return "28";
      case "CRITICAL CARE (INTENSIVISTS)":
        return "81";
      case "DENTIST":
        return "C5";
      case "DERMATOLOGY":
        return "07";
      case "DIAGNOSTIC RADIOLOGY":
        return "30";
      case "EMERGENCY MEDICINE":
        return "93";
      case "ENDOCRINOLOGY":
        return "46";
      case "FAMILY PRACTICE":
        return "08";
      case "GASTROENTEROLOGY":
        return "10";
      case "GENERAL PRACTICE":
        return "01";
      case "GENERAL SURGERY":
        return "02";
      case "GERIATRIC MEDICINE":
        return "38";
      case "GERIATRIC PSYCHIATRY":
        return "27";
      case "GYNECOLOGICAL ONCOLOGY":
        return "98";
      case "HAND SURGERY":
        return "40";
      case "HEMATOLOGY":
        return "82";
      case "HEMATOLOGY/ONCOLOGY":
        return "83";
      case "HEMATOPOIETIC CELL TRANSPLANTATION AND CELLULAR TH":
        return "C9";
      case "HOSPICE/PALLIATIVE CARE":
        return "17";
      case "HOSPITALIST":
        return "C6";
      case "INFECTIOUS DISEASE":
        return "44";
      case "INTERNAL MEDICINE":
        return "11";
      case "INTERVENTIONAL CARDIOLOGY":
        return "C3";
      case "INTERVENTIONAL PAIN MANAGEMENT":
        return "72";
      case "INTERVENTIONAL RADIOLOGY":
        return "94";
      case "MAXILLOFACIAL SURGERY":
        return "85";
      case "MEDICAL ONCOLOGY":
        return "90";
      case "NEPHROLOGY":
        return "39";
      case "NEUROLOGY":
        return "13";
      case "NEUROPSYCHIATRY":
        return "86";
      case "NEUROSURGERY":
        return "14";
      case "NUCLEAR MEDICINE":
        return "36";
      case "NURSE PRACTITIONER":
        return "50";
      case "OBSTETRICS/GYNECOLOGY":
        return "16";
      case "OCCUPATIONAL THERAPY":
        return "67";
      case "OPHTHALMOLOGY":
        return "18";
      case "OPTOMETRY":
        return "41";
      case "ORAL SURGERY":
        return "19";
      case "ORTHOPEDIC SURGERY":
        return "20";
      case "OSTEOPATHIC MANIPULATIVE MEDICINE":
        return "12";
      case "OTOLARYNGOLOGY":
        return "04";
      case "PAIN MANAGEMENT":
        return "72";
      case "PATHOLOGY":
        return "22";
      case "PEDIATRIC MEDICINE":
        return "37";
      case "PERIPHERAL VASCULAR DISEASE":
        return "76";
      case "PHYSICAL MEDICINE AND REHABILITATION":
        return "25";
      case "PHYSICAL THERAPY":
        return "65";
      case "PHYSICIAN ASSISTANT":
        return "97";
      case "PLASTIC AND RECONSTRUCTIVE SURGERY":
        return "24";
      case "PODIATRY":
        return "48";
      case "PREVENTATIVE MEDICINE":
        return "84";
      case "PSYCHIATRY":
        return "26";
      case "PULMONARY DISEASE":
        return "28";
      case "RADIATION ONCOLOGY":
        return "92";
      case "REGISTERED DIETITIAN OR NUTRITION PROFESSIONAL":
        return "71";
      case "RHEUMATOLOGY":
        return "66";
      case "SLEEP MEDICINE":
        return "C0";
      case "SPEECH LANGUAGE PATHOLOGIST":
        return "15";
      case "SPORTS MEDICINE":
        return "23";
      case "SURGICAL ONCOLOGY":
        return "91";
      case "THORACIC SURGERY":
        return "33";
      case "UNDEFINED PHYSICIAN TYPE (SPECIFY)":
        return "01"; // general practice
      case "UROLOGY":
        return "34";
      case "VASCULAR SURGERY":
        return "77";
      default:
        return "01"; // general practice
    }
  }
}
