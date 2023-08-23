package org.mitre.synthea.export.rif;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.mitre.synthea.export.rif.enrollment.PartDContractHistory;
import org.mitre.synthea.export.rif.identifiers.PartDContractID;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.HealthRecord;

/**
 * Exporter for RIF PDE (prescription) file.
 */
public class PDEExporter extends RIFExporter {

  private static final Map<Integer, Double> pdeOutOfPocketThresholds = getThresholds();
  public static final AtomicLong nextPdeId = new AtomicLong(Config.getAsLong(
          "exporter.bfd.pde_id_start", -1));

  private static Map<Integer, Double> getThresholds() {
    Map<Integer, Double> pdeOutOfPocketThresholds = new HashMap<>();
    try {
      String csv = Utilities.readResourceAndStripBOM("costs/pde_oop_thresholds.csv");
      for (LinkedHashMap<String, String> row : SimpleCSV.parse(csv)) {
        int year = Integer.parseInt(row.get("YEAR"));
        double threshold = Double.parseDouble(row.get("THRESHOLD"));
        pdeOutOfPocketThresholds.put(year, threshold);
      }
      return pdeOutOfPocketThresholds;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Construct an exporter for PDE claims.
   * @param exporter the exporter instance that will be used to access code mappers
   */
  public PDEExporter(BB2RIFExporter exporter) {
    super(exporter);
  }

  /**
   * Export PDE claims details for a single person.
   * @param person the person to export
   * @param startTime earliest claim date to export
   * @param stopTime end time of simulation
   * @return count of claims exported
   * @throws IOException if something goes wrong
   */
  long export(Person person, long startTime, long stopTime) throws IOException {
    long claimCount = 0;
    PartDContractHistory partDContracts =
            (PartDContractHistory) person.attributes.get(RIFExporter.BB2_PARTD_CONTRACTS);
    // Build a chronologically ordered list of prescription fills (including refills where
    // specified).
    List<PrescriptionFill> prescriptionFills = new LinkedList<>();
    for (HealthRecord.Encounter encounter : person.record.encounters) {
      if (encounter.stop < startTime || encounter.stop < CLAIM_CUTOFF) {
        continue;
      }
      if (!hasPartABCoverage(person, encounter.stop)) {
        continue;
      }
      if (RIFExporter.isVAorIHS(encounter)) {
        continue;
      }

      for (HealthRecord.Medication medication : encounter.medications) {
        if (!exporter.medicationCodeMapper.canMap(medication.codes.get(0))) {
          continue; // skip codes that can't be mapped to NDC
        }
        long supplyDaysMax = 90; // TBD - 30, 60, 90 day refil schedules?
        long supplyInterval = supplyDaysMax * 24 * 60 * 60 * 1000;
        long finishTime = medication.stop == 0L ? stopTime : Long.min(medication.stop, stopTime);
        String medicationCode = exporter.medicationCodeMapper.map(medication.codes.get(0),
                person);
        long time = medication.start;
        int fillNo = 1;
        while (time < finishTime) {
          PartDContractID partDContractID = partDContracts.getContractID(time);
          PrescriptionFill fill = new PrescriptionFill(time, encounter, medication,
                    medicationCode, fillNo, partDContractID, supplyInterval, finishTime);
          if (partDContractID != null) {
            prescriptionFills.add(fill);
          }
          if (!fill.refillsRemaining()) {
            break;
          }
          time += Long.min((long)fill.days * 24 * 60 * 60 * 1000, supplyInterval);
          fillNo++;
        }
      }
    }
    Collections.sort(prescriptionFills);

    // Export each prescription fill to RIF format
    HashMap<BB2RIFStructure.PDE, String> fieldValues = new HashMap<>();
    BigDecimal costs = Claim.ZERO_CENTS;
    int costYear = 0;
    String catastrophicCode = "";
    for (PrescriptionFill fill: prescriptionFills) {

      long pdeId = nextPdeId.getAndDecrement();
      long claimGroupId = RIFExporter.nextClaimGroupId.getAndDecrement();

      fieldValues.clear();
      exporter.staticFieldConfig.setValues(fieldValues, BB2RIFStructure.PDE.class, person);

      // The REQUIRED fields
      fieldValues.put(BB2RIFStructure.PDE.PDE_ID, "" + pdeId);
      fieldValues.put(BB2RIFStructure.PDE.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(BB2RIFStructure.PDE.BENE_ID,
              (String)person.attributes.get(RIFExporter.BB2_BENE_ID));
      fieldValues.put(BB2RIFStructure.PDE.SRVC_DT, RIFExporter.bb2DateFromTimestamp(fill.time));
      fieldValues.put(BB2RIFStructure.PDE.SRVC_PRVDR_ID, fill.encounter.provider.cmsProviderNum);
      fieldValues.put(BB2RIFStructure.PDE.PRSCRBR_ID,
          "" + (9_999_999_999L - fill.encounter.clinician.identifier));
      fieldValues.put(BB2RIFStructure.PDE.RX_SRVC_RFRNC_NUM, "" + pdeId);
      fieldValues.put(BB2RIFStructure.PDE.PROD_SRVC_ID, fill.medicationCode);
      // The following field was replaced by the PartD contract ID, leaving this here for now
      // until this is validated
      // H=hmo, R=ppo, S=stand-alone, E=employer direct, X=limited income
      // fieldValues.put(PrescriptionFields.PLAN_CNTRCT_REC_ID,
      //     ("R" + Math.abs(
      //         UUID.fromString(medication.claim.payer.uuid)
      //         .getMostSignificantBits())).substring(0, 5));
      fieldValues.put(BB2RIFStructure.PDE.PLAN_CNTRCT_REC_ID, fill.partDContractID.toString());
      fieldValues.put(BB2RIFStructure.PDE.DAW_PROD_SLCTN_CD, "" + (int) person.rand(0, 9));
      fieldValues.put(BB2RIFStructure.PDE.QTY_DSPNSD_NUM, "" + fill.quantity);
      fieldValues.put(BB2RIFStructure.PDE.DAYS_SUPLY_NUM, "" + fill.days);
      fieldValues.put(BB2RIFStructure.PDE.FILL_NUM, "" + fill.fillNo);
      int year = Utilities.getYear(fill.time);
      if (year != costYear) {
        costYear = year;
        costs = Claim.ZERO_CENTS;
        catastrophicCode = ""; // Blank = Attachment point not met
      }
      BigDecimal threshold = getDrugOutOfPocketThreshold(year);
      costs = costs.add(fill.medication.claim.getTotalPatientCost());
      if (costs.compareTo(threshold) < 0) {
        fieldValues.put(BB2RIFStructure.PDE.GDC_BLW_OOPT_AMT, String.format("%.2f", costs));
        fieldValues.put(BB2RIFStructure.PDE.GDC_ABV_OOPT_AMT, "0");
        fieldValues.put(BB2RIFStructure.PDE.CTSTRPHC_CVRG_CD, catastrophicCode);
      } else {
        if (catastrophicCode.equals("")) {
          catastrophicCode = "A"; // A = Attachment point met on this event
        } else if (catastrophicCode.equals("A")) {
          catastrophicCode  = "C"; // C = Above attachment point
        }
        fieldValues.put(BB2RIFStructure.PDE.GDC_BLW_OOPT_AMT, String.format("%.2f", threshold));
        fieldValues.put(BB2RIFStructure.PDE.GDC_ABV_OOPT_AMT,
                String.format("%.2f", costs.subtract(threshold)));
        fieldValues.put(BB2RIFStructure.PDE.CTSTRPHC_CVRG_CD, catastrophicCode);
      }
      fieldValues.put(BB2RIFStructure.PDE.TOT_RX_CST_AMT,
          String.format("%.2f", fill.medication.claim.getTotalClaimCost()));
      // Under normal circumstances, the following fields summed together,
      // should equal TOT_RX_CST_AMT:
      // - PTNT_PAY_AMT       : what the patient paid
      // - OTHR_TROOP_AMT     : what 3rd party paid out of pocket
      // - LICS_AMT           : low income subsidized payment
      // - PLRO_AMT           : what other 3rd party insurances paid
      // - CVRD_D_PLAN_PD_AMT : what Part D paid
      // - NCVRD_PLAN_PD_AMT  : part of total not covered by Part D whatsoever
      // OTHR_TROOP_AMT and LICS_AMT are always 0, set in field value spreadsheet
      // TODO: make claim copay match the designated cost sharing code, see
      // PartDContractHistory.getPartDCostSharingCode
      fieldValues.put(BB2RIFStructure.PDE.PTNT_PAY_AMT,
          String.format("%.2f", fill.medication.claim.getTotalPatientCost()));
      fieldValues.put(BB2RIFStructure.PDE.PLRO_AMT,
          String.format("%.2f", fill.medication.claim.getTotalPaidBySecondaryPayer()));
      fieldValues.put(BB2RIFStructure.PDE.CVRD_D_PLAN_PD_AMT,
          String.format("%.2f", fill.medication.claim.getTotalCoveredCost()));
      fieldValues.put(BB2RIFStructure.PDE.NCVRD_PLAN_PD_AMT,
          String.format("%.2f", fill.medication.claim.getTotalAdjustment()));

      fieldValues.put(BB2RIFStructure.PDE.PHRMCY_SRVC_TYPE_CD, "0" + (int) person.rand(1, 8));
      fieldValues.put(BB2RIFStructure.PDE.PD_DT, RIFExporter.bb2DateFromTimestamp(fill.time));
      String residenceCode = getResidenceCode(person, fill.encounter);
      fieldValues.put(BB2RIFStructure.PDE.PTNT_RSDNC_CD, residenceCode);

      exporter.rifWriters.writeValues(BB2RIFStructure.PDE.class, fieldValues);
      claimCount++;
    }
    return claimCount;
  }

  private static BigDecimal getDrugOutOfPocketThreshold(int year) {
    double threshold = pdeOutOfPocketThresholds.getOrDefault(year, 4550.0);
    return BigDecimal.valueOf(threshold);
  }

  private static String getResidenceCode(Person person, HealthRecord.Encounter encounter) {
    Set<ClaimType> claimTypes = RIFExporter.getClaimTypes(encounter);
    String residenceCode = "00"; // 00=not specified
    double roll = person.rand();
    if (claimTypes.contains(ClaimType.SNF)) {
      residenceCode = "03"; // 03=long-term
    } else if (claimTypes.contains(ClaimType.HHA)) {
      if (roll <= 0.95) {
        residenceCode = "01"; // 01=home
      } else {
        residenceCode = "04"; // 04=assisted living
      }
    } else if (claimTypes.contains(ClaimType.HOSPICE)) {
      residenceCode = "11"; // 11=hospice
    } else if (claimTypes.contains(ClaimType.INPATIENT)) {
      if (roll <= 0.95) {
        residenceCode = "03"; // 03=nursing
      } else if (roll <= 0.99) {
        residenceCode = "04"; // 04=assisted living
      } else {
        residenceCode = "13"; // 13=inpatient rehab
      }
    } else if (claimTypes.contains(ClaimType.CARRIER)
        || claimTypes.contains(ClaimType.OUTPATIENT)) {
      if (roll <= 0.87) {
        residenceCode = "01"; // 01=home
      } else if (roll <= 0.95) {
        residenceCode = "00"; // 00=not specified
      } else if (roll <= 0.99) {
        residenceCode = "04"; // 04=assisted living
      } else if (person.attributes.containsKey("homeless")
          && ((Boolean) person.attributes.get("homeless") == true)) {
        residenceCode = "14"; // 14=homeless, rare in actual data
      }
    } else {
      // Other
    }
    return residenceCode;
  }
}
