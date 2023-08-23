package org.mitre.synthea.export.rif;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.mitre.synthea.export.ExportHelper;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.HealthRecord;

/**
 * Exporter for RIF Hospice file.
 */
public class HospiceExporter extends RIFExporter {

  /**
   * Construct an exporter for Hospice claims.
   * @param exporter the exporter instance that will be used to access code mappers
   */
  public HospiceExporter(BB2RIFExporter exporter) {
    super(exporter);
  }

  /**
   * Export Hospice visits for a single person.
   * @param person the person to export
   * @param startTime earliest claim date to export
   * @param stopTime end time of simulation
   * @return count of claims exported
   * @throws IOException if something goes wrong
   */
  long export(Person person, long startTime, long stopTime) throws IOException {
    long claimCount = 0;
    for (HealthRecord.Encounter encounter : person.record.encounters) {
      if (encounter.stop < startTime || encounter.stop < CLAIM_CUTOFF) {
        continue;
      }
      if (!hasPartABCoverage(person, encounter.stop)) {
        continue;
      }
      if (!RIFExporter.getClaimTypes(encounter).contains(ClaimType.HOSPICE)) {
        continue;
      }

      // Map active conditions at time of encounter
      List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, encounter.stop);
      if (mappedDiagnosisCodes.isEmpty()) {
        continue; // skip this encounter
      }

      int days = (int) ((encounter.stop - encounter.start) / (1000 * 60 * 60 * 24));
      if (days <= 0) {
        days = 1;
      }

      HashMap<BB2RIFStructure.HOSPICE, String> fieldValues = new HashMap<>();
      exporter.staticFieldConfig.setValues(fieldValues, BB2RIFStructure.HOSPICE.class, person);

      // Initial random revenue center from field code CSV
      String revCenter = fieldValues.get(BB2RIFStructure.HOSPICE.REV_CNTR);

      final String PHARMACY_REV_CENTER = "0250";
      final String MED_ADMIN_CODE = "A4216";
      final String HOSPICE_GENERAL_REV_CNTR = "0550"; // Skilled nursing - general classification
      ConsolidatedClaimLines consolidatedClaimLines = new ConsolidatedClaimLines();
      for (Claim.ClaimEntry lineItem : encounter.claim.items) {
        if (lineItem.entry instanceof HealthRecord.Procedure) {
          String hcpcsCode = null;
          for (HealthRecord.Code code : lineItem.entry.codes) {
            if (exporter.hcpcsCodeMapper.canMap(code)) {
              hcpcsCode = exporter.hcpcsCodeMapper.map(code, person, true);
              if (exporter.hospiceRevCntrMapper.canMap(hcpcsCode)) {
                revCenter = exporter.hospiceRevCntrMapper.map(hcpcsCode, person, true);
              }
              break; // take the first mappable code for each procedure
            }
          }
          if (hcpcsCode == null) {
            revCenter = HOSPICE_GENERAL_REV_CNTR;
          }
          consolidatedClaimLines.addClaimLine(hcpcsCode, revCenter, lineItem,
                  encounter);
        } else if (lineItem.entry instanceof HealthRecord.Medication) {
          HealthRecord.Medication med = (HealthRecord.Medication) lineItem.entry;
          if (med.administration) {
            consolidatedClaimLines.addClaimLine(MED_ADMIN_CODE, PHARMACY_REV_CENTER, lineItem,
                    encounter);
          }
        }
      }
      setClaimLevelValues(fieldValues, mappedDiagnosisCodes, days, person, encounter,
              consolidatedClaimLines);

      // Synchronize to ensure all claim lines are grouped togther in output file
      synchronized (exporter.rifWriters.getOrCreateWriter(BB2RIFStructure.HOSPICE.class)) {
        int claimLine = 1;
        for (ConsolidatedClaimLines.ConsolidatedClaimLine lineItem:
                consolidatedClaimLines.getLines()) {
          fieldValues.put(BB2RIFStructure.HOSPICE.REV_CNTR_DT,
                  RIFExporter.bb2DateFromTimestamp(lineItem.getStart()));
          fieldValues.put(BB2RIFStructure.HOSPICE.HCPCS_CD, lineItem.getCode());
          fieldValues.put(BB2RIFStructure.HOSPICE.REV_CNTR, lineItem.getRevCntr());
          int revCntrCount = lineItem.getCount();
          switch (lineItem.getCode()) {
            case MED_ADMIN_CODE:
              fieldValues.put(BB2RIFStructure.HOSPICE.REV_CNTR_NDC_QTY,
                      Integer.toString(revCntrCount));
              fieldValues.put(BB2RIFStructure.HOSPICE.REV_CNTR_NDC_QTY_QLFR_CD, "UN"); // Unit
              fieldValues.remove(BB2RIFStructure.HOSPICE.REV_CNTR_UNIT_CNT);
              break;
            default:
              fieldValues.put(BB2RIFStructure.HOSPICE.REV_CNTR_UNIT_CNT,
                      Integer.toString(revCntrCount));
              fieldValues.remove(BB2RIFStructure.HOSPICE.REV_CNTR_NDC_QTY);
              fieldValues.remove(BB2RIFStructure.HOSPICE.REV_CNTR_NDC_QTY_QLFR_CD);
              break;
          }
          setClaimLineCosts(fieldValues, lineItem, revCntrCount);
          fieldValues.put(BB2RIFStructure.HOSPICE.CLM_LINE_NUM, Integer.toString(claimLine++));
          exporter.rifWriters.writeValues(BB2RIFStructure.HOSPICE.class, fieldValues);
        }

        // Add a total charge entry
        setClaimLineCosts(fieldValues, consolidatedClaimLines, 1);
        fieldValues.put(BB2RIFStructure.HOSPICE.CLM_LINE_NUM, Integer.toString(claimLine++));
        fieldValues.remove(BB2RIFStructure.HOSPICE.HCPCS_CD);
        fieldValues.remove(BB2RIFStructure.HOSPICE.AT_PHYSN_NPI);
        fieldValues.remove(BB2RIFStructure.HOSPICE.RNDRNG_PHYSN_NPI);
        fieldValues.put(BB2RIFStructure.HOSPICE.REV_CNTR, "0001"); // Total charge
        fieldValues.remove(BB2RIFStructure.HOSPICE.REV_CNTR_NDC_QTY);
        fieldValues.remove(BB2RIFStructure.HOSPICE.REV_CNTR_UNIT_CNT);
        fieldValues.remove(BB2RIFStructure.HOSPICE.REV_CNTR_RATE_AMT);
        fieldValues.remove(BB2RIFStructure.HOSPICE.REV_CNTR_NDC_QTY_QLFR_CD);
        fieldValues.remove(BB2RIFStructure.HOSPICE.REV_CNTR_DDCTBL_COINSRNC_CD);
        exporter.rifWriters.writeValues(BB2RIFStructure.HOSPICE.class, fieldValues);
      }
      claimCount++;
    }
    return claimCount;
  }

  private static void setClaimLineCosts(Map<BB2RIFStructure.HOSPICE, String> fieldValues,
          Claim.ClaimCost lineItem, int count) {
    fieldValues.put(BB2RIFStructure.HOSPICE.REV_CNTR_RATE_AMT,
            String.format("%.2f", lineItem.cost
                    .divide(BigDecimal.valueOf(Integer.max(1, count)), RoundingMode.HALF_EVEN)
                    .setScale(2, RoundingMode.HALF_EVEN)));
    fieldValues.put(BB2RIFStructure.HOSPICE.REV_CNTR_PMT_AMT_AMT,
            String.format("%.2f", lineItem.getCoveredCost()));
    fieldValues.put(BB2RIFStructure.HOSPICE.REV_CNTR_PRVDR_PMT_AMT,
            String.format("%.2f", lineItem.getCoveredCost()));
    fieldValues.put(BB2RIFStructure.HOSPICE.REV_CNTR_TOT_CHRG_AMT,
            String.format("%.2f", lineItem.cost));
    fieldValues.put(BB2RIFStructure.HOSPICE.REV_CNTR_NCVRD_CHRG_AMT,
            String.format("%.2f", lineItem.copayPaidByPatient
                    .add(lineItem.deductiblePaidByPatient).add(lineItem.patientOutOfPocket)));
    if (lineItem.patientOutOfPocket.compareTo(Claim.ZERO_CENTS) == 0
            && lineItem.deductiblePaidByPatient.compareTo(Claim.ZERO_CENTS) == 0) {
      // Not subject to deductible or coinsurance
      fieldValues.put(BB2RIFStructure.HOSPICE.REV_CNTR_DDCTBL_COINSRNC_CD, "3");
    } else if (lineItem.patientOutOfPocket.compareTo(Claim.ZERO_CENTS) > 0
            && lineItem.deductiblePaidByPatient.compareTo(Claim.ZERO_CENTS) > 0) {
      // Subject to deductible and coinsurance
      fieldValues.put(BB2RIFStructure.HOSPICE.REV_CNTR_DDCTBL_COINSRNC_CD, "0");
    } else if (lineItem.patientOutOfPocket.compareTo(Claim.ZERO_CENTS) == 0) {
      // Not subject to deductible
      fieldValues.put(BB2RIFStructure.HOSPICE.REV_CNTR_DDCTBL_COINSRNC_CD, "1");
    } else {
      // Not subject to coinsurance
      fieldValues.put(BB2RIFStructure.HOSPICE.REV_CNTR_DDCTBL_COINSRNC_CD, "2");
    }
  }

  private void setClaimLevelValues(Map<BB2RIFStructure.HOSPICE, String> fieldValues,
          List<String> mappedDiagnosisCodes, int days, Person person,
          HealthRecord.Encounter encounter, Claim.ClaimCost cost) {
    fieldValues.put(BB2RIFStructure.HOSPICE.BENE_ID,
            (String)person.attributes.get(RIFExporter.BB2_BENE_ID));
    long claimId = RIFExporter.nextClaimId.getAndDecrement();
    fieldValues.put(BB2RIFStructure.HOSPICE.CLM_ID, "" + claimId);
    long claimGroupId = RIFExporter.nextClaimGroupId.getAndDecrement();
    fieldValues.put(BB2RIFStructure.HOSPICE.CLM_GRP_ID, "" + claimGroupId);
    long fiDocId = RIFExporter.nextFiDocCntlNum.getAndDecrement();
    fieldValues.put(BB2RIFStructure.HOSPICE.FI_DOC_CLM_CNTL_NUM, "" + fiDocId);
    fieldValues.put(BB2RIFStructure.HOSPICE.CLM_FROM_DT,
            RIFExporter.bb2DateFromTimestamp(encounter.start));
    fieldValues.put(BB2RIFStructure.HOSPICE.CLM_HOSPC_START_DT_ID,
            RIFExporter.bb2DateFromTimestamp(encounter.start));
    fieldValues.put(BB2RIFStructure.HOSPICE.CLM_THRU_DT,
            RIFExporter.bb2DateFromTimestamp(encounter.stop));
    fieldValues.put(BB2RIFStructure.HOSPICE.NCH_WKLY_PROC_DT,
            RIFExporter.bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop)));
    fieldValues.put(BB2RIFStructure.HOSPICE.PRVDR_NUM,
            StringUtils.truncate(encounter.provider.cmsProviderNum, 6));
    fieldValues.put(BB2RIFStructure.HOSPICE.AT_PHYSN_NPI, encounter.clinician.npi);
    fieldValues.put(BB2RIFStructure.HOSPICE.RNDRNG_PHYSN_NPI, encounter.clinician.npi);
    fieldValues.put(BB2RIFStructure.HOSPICE.ORG_NPI_NUM, encounter.provider.npi);
    fieldValues.put(BB2RIFStructure.HOSPICE.CLM_PMT_AMT,
            String.format("%.2f", cost.getCoveredCost()));
    if (encounter.claim.coveredByMedicare()) {
      fieldValues.put(BB2RIFStructure.HOSPICE.NCH_PRMRY_PYR_CLM_PD_AMT, "0");
    } else {
      fieldValues.put(BB2RIFStructure.HOSPICE.NCH_PRMRY_PYR_CLM_PD_AMT,
              String.format("%.2f", cost.getCoveredCost()));
    }
    fieldValues.put(BB2RIFStructure.HOSPICE.PRVDR_STATE_CD,
            exporter.locationMapper.getStateCode(encounter.provider.state));
    // PTNT_DSCHRG_STUS_CD: 1=home, 2=transfer, 3=SNF, 20=died, 30=still here
    // NCH_PTNT_STUS_IND_CD: A = Discharged, B = Died, C = Still a patient
    String dischargeStatus = null;
    String patientStatus = null;
    String dischargeDate = null;
    if (encounter.ended) {
      dischargeStatus = "1"; // TODO 2=transfer if the next encounter is also inpatient
      patientStatus = "A"; // discharged
      dischargeDate = RIFExporter.bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop));
    } else {
      dischargeStatus = "30"; // the patient is still here
      patientStatus = "C"; // still a patient
    }
    if (!person.alive(encounter.stop)) {
      dischargeStatus = "20"; // the patient died before the encounter ended
      patientStatus = "B"; // died
      dischargeDate = RIFExporter.bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop));
    }
    fieldValues.put(BB2RIFStructure.HOSPICE.PTNT_DSCHRG_STUS_CD, dischargeStatus);
    fieldValues.put(BB2RIFStructure.HOSPICE.NCH_PTNT_STATUS_IND_CD, patientStatus);
    if (dischargeDate != null) {
      fieldValues.put(BB2RIFStructure.HOSPICE.NCH_BENE_DSCHRG_DT, dischargeDate);
    }
    fieldValues.put(BB2RIFStructure.HOSPICE.CLM_TOT_CHRG_AMT,
            String.format("%.2f", cost.getTotalClaimCost()));

    if (encounter.reason != null) {
      // If the encounter has a recorded reason, enter the mapped
      // values into the principle diagnoses code.
      if (exporter.conditionCodeMapper.canMap(encounter.reason)) {
        String icdCode = exporter.conditionCodeMapper.map(encounter.reason, person, true);
        fieldValues.put(BB2RIFStructure.HOSPICE.PRNCPAL_DGNS_CD, icdCode);
      }
    }

    int smallest = Math.min(mappedDiagnosisCodes.size(),
            BB2RIFStructure.hospiceDxFields.length);
    for (int i = 0; i < smallest; i++) {
      BB2RIFStructure.HOSPICE[] dxField = BB2RIFStructure.hospiceDxFields[i];
      fieldValues.put(dxField[0], mappedDiagnosisCodes.get(i));
      fieldValues.put(dxField[1], "0"); // 0=ICD10
    }
    if (!fieldValues.containsKey(BB2RIFStructure.HOSPICE.PRNCPAL_DGNS_CD)) {
      fieldValues.put(BB2RIFStructure.HOSPICE.PRNCPAL_DGNS_CD, mappedDiagnosisCodes.get(0));
    }

    // Check for external code...
    exporter.setExternalCode(person, fieldValues,
        BB2RIFStructure.HOSPICE.PRNCPAL_DGNS_CD, BB2RIFStructure.HOSPICE.ICD_DGNS_E_CD1,
        BB2RIFStructure.HOSPICE.ICD_DGNS_E_VRSN_CD1);
    exporter.setExternalCode(person, fieldValues,
        BB2RIFStructure.HOSPICE.PRNCPAL_DGNS_CD, BB2RIFStructure.HOSPICE.FST_DGNS_E_CD,
        BB2RIFStructure.HOSPICE.FST_DGNS_E_VRSN_CD);

    fieldValues.put(BB2RIFStructure.HOSPICE.CLM_UTLZTN_DAY_CNT, "" + days);
  }
}
