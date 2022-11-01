package org.mitre.synthea.export.rif;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;

/**
 * Base class for all of the type-specific exporters.
 */
public class ExporterBase {

  static final String BB2_PARTD_CONTRACTS = "BB2_PARTD_CONTRACTS";
  static final AtomicLong nextCarrClmCntlNum = new AtomicLong(Config.getAsLong(
          "exporter.bfd.carr_clm_cntl_num_start", -1));
  static final AtomicLong nextPdeId = new AtomicLong(Config.getAsLong(
          "exporter.bfd.pde_id_start", -1));
  static final AtomicReference<HICN> nextHicn = new AtomicReference<>(
          HICN.parse(Config.get("exporter.bfd.hicn_start", "T00000000A")));
  static final AtomicLong nextFiDocCntlNum = new AtomicLong(
          Config.getAsLong("exporter.bfd.fi_doc_cntl_num_start", -1));
  static final AtomicReference<MBI> nextMbi = new AtomicReference<>(
          MBI.parse(Config.get("exporter.bfd.mbi_start", "1S00-A00-AA00")));
  static final AtomicLong nextBeneId = new AtomicLong(
          Config.getAsLong("exporter.bfd.bene_id_start", -1));
  static final AtomicLong nextClaimId = new AtomicLong(
          Config.getAsLong("exporter.bfd.clm_id_start", -1));
  static final AtomicLong nextClaimGroupId = new AtomicLong(
          Config.getAsLong("exporter.bfd.clm_grp_id_start", -1));
  static final String BB2_BENE_ID = "BB2_BENE_ID";
  static final String BB2_HIC_ID = "BB2_HIC_ID";
  static final String BB2_MBI = "BB2_MBI";
  static final CLIA[] cliaLabNumbers = initCliaLabNumbers();

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

  protected final long startTime;
  protected final long stopTime;
  protected final BB2RIFExporter exporter;

  protected ExporterBase(long startTime, long stopTime, BB2RIFExporter exporter) {
    this.startTime = startTime;
    this.stopTime = stopTime;
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
}
