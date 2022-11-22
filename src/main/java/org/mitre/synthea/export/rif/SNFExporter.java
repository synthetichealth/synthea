package org.mitre.synthea.export.rif;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mitre.synthea.export.ExportHelper;
import org.mitre.synthea.world.agents.PayerManager;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.HealthRecord;

/**
 * Exporter for RIF SNF File.
 */
public class SNFExporter extends RIFExporter {

  private static final long SNF_PDPM_CUTOVER = parseSimpleDate("20191001");

  /**
   * Construct an exporter for Skilled Nursing Facility claims.
   * @param exporter the exporter instance that will be used to access code mappers
   */
  public SNFExporter(BB2RIFExporter exporter) {
    super(exporter);
  }

  /**
   * Export Skilled Nursing Facility claims for the supplied single person.
   * @param person the person to export
   * @param startTime earliest claim date to export
   * @param stopTime end time of simulation
   * @return count of claims exported
   * @throws IOException if something goes wrong
   */
  public long export(Person person, long startTime, long stopTime)
          throws IOException {
    long claimCount = 0;
    HashMap<BB2RIFStructure.SNF, String> fieldValues = new HashMap<>();
    boolean previousEmergency;
    boolean previousUrgent;

    for (HealthRecord.Encounter encounter : person.record.encounters) {
      if (encounter.stop < startTime || encounter.stop < CLAIM_CUTOFF) {
        continue;
      }
      if (!RIFExporter.getClaimTypes(encounter).contains(ClaimType.SNF)) {
        continue;
      }

      previousEmergency = encounter.type.equals(HealthRecord.EncounterType.EMERGENCY.toString());
      previousUrgent = encounter.type.equals(HealthRecord.EncounterType.URGENTCARE.toString());

      long claimId = RIFExporter.nextClaimId.getAndDecrement();
      long claimGroupId = RIFExporter.nextClaimGroupId.getAndDecrement();
      long fiDocId = RIFExporter.nextFiDocCntlNum.getAndDecrement();

      fieldValues.clear();
      exporter.staticFieldConfig.setValues(fieldValues, BB2RIFStructure.SNF.class, person);

      // The REQUIRED Fields
      fieldValues.put(BB2RIFStructure.SNF.BENE_ID,
              (String)person.attributes.get(RIFExporter.BB2_BENE_ID));
      fieldValues.put(BB2RIFStructure.SNF.CLM_ID, "" + claimId);
      fieldValues.put(BB2RIFStructure.SNF.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(BB2RIFStructure.SNF.FI_DOC_CLM_CNTL_NUM, "" + fiDocId);
      fieldValues.put(BB2RIFStructure.SNF.CLM_FROM_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(BB2RIFStructure.SNF.CLM_ADMSN_DT, bb2DateFromTimestamp(encounter.start));
      fieldValues.put(BB2RIFStructure.SNF.CLM_THRU_DT, bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(BB2RIFStructure.SNF.NCH_WKLY_PROC_DT,
          bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop)));
      fieldValues.put(BB2RIFStructure.SNF.PRVDR_NUM, encounter.provider.cmsProviderNum);
      fieldValues.put(BB2RIFStructure.SNF.ORG_NPI_NUM, encounter.provider.npi);

      fieldValues.put(BB2RIFStructure.SNF.CLM_PMT_AMT,
          String.format("%.2f", encounter.claim.getTotalCoveredCost()));
      if (encounter.claim.plan == PayerManager.getGovernmentPayer(PayerManager.MEDICARE)
          .getGovernmentPayerPlan()) {
        fieldValues.put(BB2RIFStructure.SNF.NCH_PRMRY_PYR_CLM_PD_AMT, "0");
      } else {
        fieldValues.put(BB2RIFStructure.SNF.NCH_PRMRY_PYR_CLM_PD_AMT,
            String.format("%.2f", encounter.claim.getTotalCoveredCost()));
      }
      fieldValues.put(BB2RIFStructure.SNF.PRVDR_STATE_CD,
          exporter.locationMapper.getStateCode(encounter.provider.state));
      fieldValues.put(BB2RIFStructure.SNF.CLM_TOT_CHRG_AMT,
          String.format("%.2f", encounter.claim.getTotalClaimCost()));
      if (previousEmergency) {
        fieldValues.put(BB2RIFStructure.SNF.CLM_IP_ADMSN_TYPE_CD, "1");
      } else if (previousUrgent) {
        fieldValues.put(BB2RIFStructure.SNF.CLM_IP_ADMSN_TYPE_CD, "2");
      } else {
        fieldValues.put(BB2RIFStructure.SNF.CLM_IP_ADMSN_TYPE_CD, "3");
      }
      fieldValues.put(BB2RIFStructure.SNF.NCH_BENE_IP_DDCTBL_AMT,
          String.format("%.2f", encounter.claim.getTotalDeductiblePaid()));
      fieldValues.put(BB2RIFStructure.SNF.NCH_BENE_PTA_COINSRNC_LBLTY_AM,
          String.format("%.2f", encounter.claim.getTotalCoinsurancePaid()));
      fieldValues.put(BB2RIFStructure.SNF.NCH_IP_NCVRD_CHRG_AMT,
          String.format("%.2f", encounter.claim.getTotalPatientCost()));
      fieldValues.put(BB2RIFStructure.SNF.NCH_IP_TOT_DDCTN_AMT,
          String.format("%.2f", encounter.claim.getTotalDeductiblePaid().add(
                  encounter.claim.getTotalCoinsurancePaid())));
      int days = (int) ((encounter.stop - encounter.start) / (1000 * 60 * 60 * 24));
      if (days <= 0) {
        days = 1;
      }
      fieldValues.put(BB2RIFStructure.SNF.CLM_UTLZTN_DAY_CNT, "" + days);
      int coinDays = days -  21; // first 21 days no coinsurance
      if (coinDays < 0) {
        coinDays = 0;
      }
      fieldValues.put(BB2RIFStructure.SNF.BENE_TOT_COINSRNC_DAYS_CNT, "" + coinDays);
      fieldValues.put(BB2RIFStructure.SNF.REV_CNTR_TOT_CHRG_AMT,
          String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(BB2RIFStructure.SNF.REV_CNTR_NCVRD_CHRG_AMT,
          String.format("%.2f", encounter.claim.getTotalPatientCost()));

      // OPTIONAL CODES
      if (encounter.reason != null) {
        // If the encounter has a recorded reason, enter the mapped
        // values into the principle diagnoses code.
        if (exporter.conditionCodeMapper.canMap(encounter.reason.code)) {
          String icdCode = exporter.conditionCodeMapper.map(encounter.reason.code, person, true);
          fieldValues.put(BB2RIFStructure.SNF.PRNCPAL_DGNS_CD, icdCode);
          fieldValues.put(BB2RIFStructure.SNF.ADMTG_DGNS_CD, icdCode);
          if (exporter.drgCodeMapper.canMap(icdCode)) {
            fieldValues.put(BB2RIFStructure.SNF.CLM_DRG_CD,
                    exporter.drgCodeMapper.map(icdCode, person));
          }
        }
      }

      // PTNT_DSCHRG_STUS_CD: 1=home, 2=transfer, 3=SNF, 20=died, 30=still here
      // NCH_PTNT_STUS_IND_CD: A = Discharged, B = Died, C = Still a patient
      String dischargeStatus;
      String patientStatus;
      String dischargeDate = null;
      if (encounter.ended) {
        dischargeStatus = "1"; // TODO 2=transfer if the next encounter is also inpatient
        patientStatus = "A"; // discharged
        dischargeDate = bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop));
      } else {
        dischargeStatus = "30"; // the patient is still here
        patientStatus = "C"; // still a patient
      }
      if (!person.alive(encounter.stop)) {
        dischargeStatus = "20"; // the patient died before the encounter ended
        patientStatus = "B"; // died
        dischargeDate = bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop));
      }
      fieldValues.put(BB2RIFStructure.SNF.PTNT_DSCHRG_STUS_CD, dischargeStatus);
      fieldValues.put(BB2RIFStructure.SNF.NCH_PTNT_STATUS_IND_CD, patientStatus);
      if (dischargeDate != null) {
        fieldValues.put(BB2RIFStructure.SNF.NCH_BENE_DSCHRG_DT, dischargeDate);
      }

      // Use the active condition diagnoses to enter mapped values
      // into the diagnoses codes.
      List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, encounter.stop);
      boolean noDiagnoses = mappedDiagnosisCodes.isEmpty();
      if (!noDiagnoses) {
        int smallest = Math.min(mappedDiagnosisCodes.size(),
                BB2RIFStructure.snfDxFields.length);
        for (int i = 0; i < smallest; i++) {
          BB2RIFStructure.SNF[] dxField = BB2RIFStructure.snfDxFields[i];
          fieldValues.put(dxField[0], mappedDiagnosisCodes.get(i));
          fieldValues.put(dxField[1], "0"); // 0=ICD10
        }
        if (!fieldValues.containsKey(BB2RIFStructure.SNF.PRNCPAL_DGNS_CD)) {
          fieldValues.put(BB2RIFStructure.SNF.PRNCPAL_DGNS_CD, mappedDiagnosisCodes.get(0));
          fieldValues.put(BB2RIFStructure.SNF.ADMTG_DGNS_CD, mappedDiagnosisCodes.get(0));
        }
      }

      // Check for external code...
      exporter.setExternalCode(person, fieldValues,
          BB2RIFStructure.SNF.PRNCPAL_DGNS_CD, BB2RIFStructure.SNF.ICD_DGNS_E_CD1,
          BB2RIFStructure.SNF.ICD_DGNS_E_VRSN_CD1);
      exporter.setExternalCode(person, fieldValues,
          BB2RIFStructure.SNF.PRNCPAL_DGNS_CD, BB2RIFStructure.SNF.FST_DGNS_E_CD,
          BB2RIFStructure.SNF.FST_DGNS_E_VRSN_CD);

      // Use the procedures in this encounter to enter mapped values
      boolean noProcedures = false;
      if (!encounter.procedures.isEmpty()) {
        List<HealthRecord.Procedure> mappableProcedures = new ArrayList<>();
        List<String> mappedProcedureCodes = new ArrayList<>();
        for (HealthRecord.Procedure procedure : encounter.procedures) {
          for (HealthRecord.Code code : procedure.codes) {
            if (exporter.conditionCodeMapper.canMap(code.code)) {
              mappableProcedures.add(procedure);
              mappedProcedureCodes.add(exporter.conditionCodeMapper.map(code.code, person, true));
              break; // take the first mappable code for each procedure
            }
          }
        }
        if (!mappableProcedures.isEmpty()) {
          int smallest = Math.min(mappableProcedures.size(),
                  BB2RIFStructure.snfPxFields.length);
          for (int i = 0; i < smallest; i++) {
            BB2RIFStructure.SNF[] pxField = BB2RIFStructure.snfPxFields[i];
            fieldValues.put(pxField[0], mappedProcedureCodes.get(i));
            fieldValues.put(pxField[1], "0"); // 0=ICD10
            fieldValues.put(pxField[2], bb2DateFromTimestamp(mappableProcedures.get(i).start));
          }
        } else {
          noProcedures = true;
        }
      }

      if (noDiagnoses && noProcedures) {
        continue; // skip this encounter
      }

      /**
       * PPS and PDPM codes are documented in the "Long-Term Care Facility Resident Assessment
       * Instrument 3.0 User’s Manual", see
       * https://www.cms.gov/Medicare/Quality-Initiatives-Patient-Assessment-Instruments/NursingHomeQualityInits/MDS30RAIManual
       * For PPS and PDPM, the HCPCS code is used to describe patient characteristics that drive
       * the level of care required, the revenue center captures the type of care provided.
       **/
      final String PPS_MED_ADMIN_CODE = "AAA00";
      final String PDPM_MED_ADMIN_CODE = "KAGD1";
      final String PHARMACY_REV_CNTR = "0250";
      final boolean isPDPM = encounter.start > SNF_PDPM_CUTOVER;
      final String SNF_MED_ADMIN_CODE = isPDPM ? PDPM_MED_ADMIN_CODE : PPS_MED_ADMIN_CODE;
      final CodeMapper codeMapper = isPDPM ? exporter.snfPDPMMapper : exporter.snfPPSMapper;
      ConsolidatedClaimLines consolidatedClaimLines = new ConsolidatedClaimLines();
      for (Claim.ClaimEntry lineItem : encounter.claim.items) {
        if (lineItem.entry instanceof HealthRecord.Procedure) {
          String snfCode = null;
          String revCntr = null;
          for (HealthRecord.Code code : lineItem.entry.codes) {
            if (exporter.snfRevCntrMapper.canMap(code.code)) {
              revCntr = exporter.snfRevCntrMapper.map(code.code, person, true);
            }
            if (codeMapper.canMap(code.code)) {
              if (person.rand() < 0.15) { // Only 15% of SNF claim have a HCPCS code
                snfCode = codeMapper.map(code.code, person, true);
                consolidatedClaimLines.addClaimLine(snfCode, revCntr, lineItem, encounter);
              }
              break; // take the first mappable code for each procedure
            }
          }
          if (snfCode == null) {
            // Add an entry for an empty code (either unmappable or 85% blank)
            consolidatedClaimLines.addClaimLine(snfCode, revCntr, lineItem, encounter);
          }
        } else if (lineItem.entry instanceof HealthRecord.Medication) {
          HealthRecord.Medication med = (HealthRecord.Medication) lineItem.entry;
          if (med.administration) {
            // Administration of medication
            consolidatedClaimLines.addClaimLine(SNF_MED_ADMIN_CODE, PHARMACY_REV_CNTR, lineItem,
                    encounter);
          }
        }
      }

      synchronized (exporter.rifWriters.getOrCreateWriter(BB2RIFStructure.SNF.class)) {
        int claimLine = 1;
        for (ConsolidatedClaimLines.ConsolidatedClaimLine lineItem:
                consolidatedClaimLines.getLines()) {
          fieldValues.put(BB2RIFStructure.SNF.HCPCS_CD, lineItem.getCode());
          fieldValues.put(BB2RIFStructure.SNF.AT_PHYSN_NPI, lineItem.getClinician().npi);
          fieldValues.put(BB2RIFStructure.SNF.OP_PHYSN_NPI, lineItem.getClinician().npi);
          fieldValues.put(BB2RIFStructure.SNF.RNDRNG_PHYSN_NPI, lineItem.getClinician().npi);
          int revCntrCount = lineItem.getCount();
          switch (lineItem.getCode()) {
            case "":
              fieldValues.put(BB2RIFStructure.SNF.REV_CNTR, lineItem.getRevCntr());
              fieldValues.put(BB2RIFStructure.SNF.REV_CNTR_UNIT_CNT,
                      Integer.toString(revCntrCount));
              fieldValues.remove(BB2RIFStructure.SNF.REV_CNTR_NDC_QTY);
              fieldValues.remove(BB2RIFStructure.SNF.REV_CNTR_NDC_QTY_QLFR_CD);
              break;
            case PPS_MED_ADMIN_CODE:
              fieldValues.put(BB2RIFStructure.SNF.REV_CNTR, lineItem.getRevCntr());
              fieldValues.put(BB2RIFStructure.SNF.REV_CNTR_NDC_QTY,
                      Integer.toString(revCntrCount));
              fieldValues.put(BB2RIFStructure.SNF.REV_CNTR_NDC_QTY_QLFR_CD, "UN"); // Unit
              fieldValues.remove(BB2RIFStructure.SNF.REV_CNTR_UNIT_CNT);
              break;
            case PDPM_MED_ADMIN_CODE:
              // TBD Java 14+ would allow this block to be merged with the prior one
              fieldValues.put(BB2RIFStructure.SNF.REV_CNTR, lineItem.getRevCntr());
              fieldValues.put(BB2RIFStructure.SNF.REV_CNTR_NDC_QTY,
                      Integer.toString(revCntrCount));
              fieldValues.put(BB2RIFStructure.SNF.REV_CNTR_NDC_QTY_QLFR_CD, "UN"); // Unit
              fieldValues.remove(BB2RIFStructure.SNF.REV_CNTR_UNIT_CNT);
              break;
            default:
              // Override mapped REV_CNTR when a PPS code is present and not a medication
              // SNF claim paid under PPS submitted as type of bill (TOB) 21X
              fieldValues.put(BB2RIFStructure.SNF.REV_CNTR, "0022");
              fieldValues.put(BB2RIFStructure.SNF.REV_CNTR_UNIT_CNT,
                      Integer.toString(revCntrCount));
              fieldValues.remove(BB2RIFStructure.SNF.REV_CNTR_NDC_QTY);
              fieldValues.remove(BB2RIFStructure.SNF.REV_CNTR_NDC_QTY_QLFR_CD);
              break;
          }

          fieldValues.put(BB2RIFStructure.SNF.CLM_LINE_NUM, Integer.toString(claimLine++));
          setClaimLineCosts(fieldValues, lineItem, Integer.max(1, revCntrCount));
          exporter.rifWriters.writeValues(BB2RIFStructure.SNF.class, fieldValues);
        }

        // Add a total charge entry
        setClaimLineCosts(fieldValues, consolidatedClaimLines, 1);
        fieldValues.put(BB2RIFStructure.SNF.CLM_LINE_NUM, Integer.toString(claimLine++));
        fieldValues.remove(BB2RIFStructure.SNF.HCPCS_CD);
        fieldValues.remove(BB2RIFStructure.SNF.AT_PHYSN_NPI);
        fieldValues.remove(BB2RIFStructure.SNF.RNDRNG_PHYSN_NPI);
        fieldValues.remove(BB2RIFStructure.SNF.OP_PHYSN_NPI);
        fieldValues.put(BB2RIFStructure.SNF.REV_CNTR, "0001"); // Total charge
        fieldValues.remove(BB2RIFStructure.SNF.REV_CNTR_NDC_QTY);
        fieldValues.remove(BB2RIFStructure.SNF.REV_CNTR_UNIT_CNT);
        fieldValues.remove(BB2RIFStructure.SNF.REV_CNTR_RATE_AMT);
        fieldValues.remove(BB2RIFStructure.SNF.REV_CNTR_NDC_QTY_QLFR_CD);
        fieldValues.remove(BB2RIFStructure.SNF.REV_CNTR_DDCTBL_COINSRNC_CD);
        exporter.rifWriters.writeValues(BB2RIFStructure.SNF.class, fieldValues);
      }
      claimCount++;
    }
    return claimCount;
  }

  private static void setClaimLineCosts(Map<BB2RIFStructure.SNF, String> fieldValues,
          Claim.ClaimCost lineItem, int count) {
    fieldValues.put(BB2RIFStructure.SNF.REV_CNTR_RATE_AMT,
            String.format("%.2f", lineItem.cost
                    .divide(BigDecimal.valueOf(count), RoundingMode.HALF_EVEN)
                    .setScale(2, RoundingMode.HALF_EVEN)));
    fieldValues.put(BB2RIFStructure.SNF.REV_CNTR_TOT_CHRG_AMT,
            String.format("%.2f", lineItem.cost));
    fieldValues.put(BB2RIFStructure.SNF.REV_CNTR_NCVRD_CHRG_AMT,
            String.format("%.2f", lineItem.copayPaidByPatient
                    .add(lineItem.deductiblePaidByPatient).add(lineItem.patientOutOfPocket)));
    if (lineItem.patientOutOfPocket.compareTo(Claim.ZERO_CENTS) == 0
            && lineItem.deductiblePaidByPatient.compareTo(Claim.ZERO_CENTS) == 0) {
      // Not subject to deductible or coinsurance
      fieldValues.put(BB2RIFStructure.SNF.REV_CNTR_DDCTBL_COINSRNC_CD, "3");
    } else if (lineItem.patientOutOfPocket.compareTo(Claim.ZERO_CENTS) > 0
            && lineItem.deductiblePaidByPatient.compareTo(Claim.ZERO_CENTS) > 0) {
      // Subject to deductible and coinsurance
      fieldValues.put(BB2RIFStructure.SNF.REV_CNTR_DDCTBL_COINSRNC_CD, "0");
    } else if (lineItem.patientOutOfPocket.compareTo(Claim.ZERO_CENTS) == 0) {
      // Not subject to deductible
      fieldValues.put(BB2RIFStructure.SNF.REV_CNTR_DDCTBL_COINSRNC_CD, "1");
    } else {
      // Not subject to coinsurance
      fieldValues.put(BB2RIFStructure.SNF.REV_CNTR_DDCTBL_COINSRNC_CD, "2");
    }
  }
}
