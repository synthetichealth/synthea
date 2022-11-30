package org.mitre.synthea.export.rif;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.mitre.synthea.export.rif.identifiers.CLIA;
import org.mitre.synthea.export.rif.identifiers.HICN;
import org.mitre.synthea.export.rif.identifiers.MBI;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;

/**
 * Base class for all of the type-specific exporters.
 */
public abstract class RIFExporter {

  protected static final String BB2_PARTD_CONTRACTS = "BB2_PARTD_CONTRACTS";
  protected static final AtomicLong nextFiDocCntlNum = new AtomicLong(
          Config.getAsLong("exporter.bfd.fi_doc_cntl_num_start", -1));
  protected static final AtomicLong nextClaimId = new AtomicLong(
          Config.getAsLong("exporter.bfd.clm_id_start", -1));
  protected static final AtomicLong nextClaimGroupId = new AtomicLong(
          Config.getAsLong("exporter.bfd.clm_grp_id_start", -1));
  protected static final String BB2_BENE_ID = "BB2_BENE_ID";
  protected static final String BB2_HIC_ID = "BB2_HIC_ID";
  protected static final String BB2_MBI = "BB2_MBI";
  protected static final CLIA[] cliaLabNumbers = initCliaLabNumbers();
  protected static final long CLAIM_CUTOFF = parseSimpleDate(
          Config.get("exporter.bfd.cutoff_date", "20140529"));

  protected final BB2RIFExporter exporter;

  protected RIFExporter(BB2RIFExporter exporter) {
    this.exporter = exporter;
  }

  /**
   * Day-Month-Year date format. Note that SimpleDateFormat is not thread safe so we need one
   * per generator thread.
   */
  private static final ThreadLocal<SimpleDateFormat> BB2_DATE_FORMAT = new ThreadLocal<>();

  /**
   * Get a date string in the format DD-MMM-YY from the given time stamp.
   */
  static String bb2DateFromTimestamp(long time) {
    SimpleDateFormat dateFormat = BB2_DATE_FORMAT.get();
    if (dateFormat == null) {
      dateFormat = new SimpleDateFormat("dd-MMM-yyyy");
      BB2_DATE_FORMAT.set(dateFormat);
    }
    return dateFormat.format(new Date(time));
  }

  enum ClaimType {
    CARRIER,
    OUTPATIENT,
    INPATIENT,
    HHA,
    DME,
    SNF,
    PDE,
    HOSPICE
  }

  static boolean isVAorIHS(HealthRecord.Encounter encounter) {
    boolean isVA = Provider.ProviderType.VETERAN == encounter.provider.type;
    // IHS facilities have valid 6 digit id, IHS centers don't
    boolean isIHSCenter = (Provider.ProviderType.IHS == encounter.provider.type)
            && encounter.provider.id.length() != 6;
    return isVA || isIHSCenter;
  }

  static Set<ClaimType> getClaimTypes(HealthRecord.Encounter encounter) {
    Set<ClaimType> types = new HashSet<>();
    boolean isSNF = encounter.type.equals(HealthRecord.EncounterType.SNF.toString());
    boolean isHome = encounter.type.equals(HealthRecord.EncounterType.HOME.toString());
    boolean isHospice = encounter.type.equals(HealthRecord.EncounterType.HOSPICE.toString());
    boolean isInpatient = encounter.type.equals(HealthRecord.EncounterType.INPATIENT.toString());
    boolean isEmergency = encounter.type.equals(HealthRecord.EncounterType.EMERGENCY.toString());
    boolean isWellness = encounter.type.equals(HealthRecord.EncounterType.WELLNESS.toString());
    boolean isUrgent = encounter.type.equals(HealthRecord.EncounterType.URGENTCARE.toString());
    boolean isVirtual = encounter.type.equals(HealthRecord.EncounterType.VIRTUAL.toString());
    boolean isAmbulatory = encounter.type.equals(HealthRecord.EncounterType.AMBULATORY.toString());
    boolean isOutpatient = encounter.type.equals(HealthRecord.EncounterType.OUTPATIENT.toString());
    boolean isPrimary = Provider.ProviderType.PRIMARY == encounter.provider.type;
    boolean isVirtualOutpatient = isVirtual
            && (Provider.ProviderType.HOSPITAL == encounter.provider.type);
    if (!isVAorIHS(encounter)) {
      if (isSNF) {
        types.add(ClaimType.SNF);
      } else if (isHome) {
        types.add(ClaimType.HHA);
      } else if (isHospice) {
        types.add(ClaimType.HOSPICE);
      } else if (isInpatient || isEmergency) {
        types.add(ClaimType.INPATIENT);
      } else if (isPrimary || isWellness || isUrgent) {
        types.add(ClaimType.CARRIER);
      } else if (isAmbulatory || isOutpatient || isVirtualOutpatient) {
        types.add(ClaimType.OUTPATIENT);
      }
      if (types.isEmpty()) {
        System.out.printf("BFD RIF unhandled encounter type (" + encounter.type
                + ") and provider type (" + encounter.provider.type.toString() + ")");
      }
    }
    return types;
  }

  private static CLIA[] initCliaLabNumbers() {
    int numLabs = Config.getAsInteger("exporter.bfd.clia_labs_count", 1);
    CLIA[] labNumbers = new CLIA[numLabs];
    CLIA labNumber = CLIA.parse(Config.get("exporter.bfd.clia_labs_start", "00A0000000"));
    for (int i = 0; i < numLabs; i++) {
      labNumbers[i] = labNumber;
      labNumber = labNumber.next();
    }
    return labNumbers;
  }

  protected static long parseSimpleDate(String dateStr) {
    SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
    format.setTimeZone(TimeZone.getTimeZone("UTC"));
    try {
      return format.parse(dateStr).getTime();
    } catch (ParseException ex) {
      throw new RuntimeException(ex);
    }
  }

  private static final Comparator<HealthRecord.Entry> ENTRY_SORTER =
          new Comparator<HealthRecord.Entry>() {
    @Override
    public int compare(HealthRecord.Entry o1, HealthRecord.Entry o2) {
      return (int)(o2.start - o1.start);
    }
  };

  /**
   * This returns the list of active diagnoses, sorted by most recent first
   * and oldest last.
   * @param person patient with the diagnoses.
   * @return the list of active diagnoses, sorted by most recent first and oldest last.
   */
  protected List<String> getDiagnosesCodes(Person person, long time) {
    // Collect the active diagnoses at the given time,
    // keeping only those diagnoses that are mappable.
    List<HealthRecord.Entry> diagnoses = new ArrayList<HealthRecord.Entry>();
    for (HealthRecord.Encounter encounter : person.record.encounters) {
      if (encounter.start <= time) {
        for (HealthRecord.Entry dx : encounter.conditions) {
          if (dx.start <= time && (dx.stop == 0L || dx.stop > time)) {
            if (exporter.conditionCodeMapper.canMap(dx.codes.get(0).code)) {
              String mapped = exporter.conditionCodeMapper.map(dx.codes.get(0).code, person, true);
              // Temporarily add the mapped code... we'll remove it later.
              HealthRecord.Code mappedCode = new HealthRecord.Code("ICD10", mapped,
                      dx.codes.get(0).display);
              dx.codes.add(mappedCode);
              diagnoses.add(dx);
            }
          }
        }
      }
    }
    // Sort them by date and then return only the mapped codes (not the entire Entry).
    diagnoses.sort(ENTRY_SORTER);
    List<String> mappedDiagnosisCodes = new ArrayList<String>();
    for (HealthRecord.Entry dx : diagnoses) {
      mappedDiagnosisCodes.add(dx.codes.remove(dx.codes.size() - 1).code);
    }
    return mappedDiagnosisCodes;
  }

  /**
   * Remove dashes from a non-null SSN, turn null SSN into empty string.
   * @param ssn the SSN
   * @return formatted SSN or empty string
   */
  public static String bb2TaxId(String ssn) {
    if (ssn != null) {
      return ssn.replaceAll("-", "");
    } else {
      return "";
    }
  }

  /**
   * Get the Line Place of Service Code. This is a required field for
   * Carrier and DME claims.
   * @param encounter The encounter.
   * @return non-null place of service code.
   */
  static String getPlaceOfService(HealthRecord.Encounter encounter) {
    String placeOfServiceCode = "11"; // Default to "11" = Office
    if (encounter.type.equalsIgnoreCase(HealthRecord.EncounterType.VIRTUAL.toString())) {
      placeOfServiceCode = "02"; // telehealth
    } else if (encounter.provider.type == Provider.ProviderType.IHS) {
      if (encounter.provider.hasService(HealthRecord.EncounterType.WELLNESS)) {
        placeOfServiceCode = "05"; // no hospitalization
      } else {
        placeOfServiceCode = "06"; // hospitalization
      }
    } else if (encounter.provider.type == Provider.ProviderType.VETERAN) {
      placeOfServiceCode = "26"; // military
    } else if (encounter.provider.hasService(HealthRecord.EncounterType.SNF)) {
      placeOfServiceCode = "31"; // skilled nursing facility
    } else if (encounter.provider.hasService(HealthRecord.EncounterType.HOSPICE)) {
      placeOfServiceCode = "34"; // hospice
    } else if (encounter.provider.hasService(HealthRecord.EncounterType.HOME)) {
      placeOfServiceCode = "12"; // home
    } else if (encounter.provider.hasService(HealthRecord.EncounterType.URGENTCARE)) {
      placeOfServiceCode = "20"; // urgent care
    } else if (encounter.type.equalsIgnoreCase(HealthRecord.EncounterType.EMERGENCY.toString())) {
      placeOfServiceCode = "23"; // emergency room
    } else if (encounter.type.equalsIgnoreCase(HealthRecord.EncounterType.INPATIENT.toString())) {
      placeOfServiceCode = "21"; // inpatient
    } else if (encounter.type.equalsIgnoreCase(HealthRecord.EncounterType.OUTPATIENT.toString())) {
      placeOfServiceCode = "22"; // outpatient
    } else if (encounter.type.equalsIgnoreCase(HealthRecord.EncounterType.AMBULATORY.toString())) {
      placeOfServiceCode = "22";
    } else if (encounter.type.equalsIgnoreCase(HealthRecord.EncounterType.WELLNESS.toString())) {
      placeOfServiceCode = "11"; // office
    }
    return placeOfServiceCode;
  }
}
