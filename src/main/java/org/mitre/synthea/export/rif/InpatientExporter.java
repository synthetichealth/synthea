package org.mitre.synthea.export.rif;

import static org.mitre.synthea.export.rif.RIFExporter.bb2DateFromTimestamp;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.mitre.synthea.export.ExportHelper;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.HealthRecord;

/**
 * Exporter for RIF Inpatient File.
 */
public class InpatientExporter extends RIFExporter {

  /**
   * Construct an exporter for Inpatient claims.
   * @param exporter the exporter instance that will be used to access code mappers
   */
  public InpatientExporter(BB2RIFExporter exporter) {
    super(exporter);
  }

  /**
   * Export inpatient claims details for a single person.
   * @param person the person to export
   * @param startTime earliest claim date to export
   * @param stopTime end time of simulation
   * @return count of claims exported
   * @throws IOException if something goes wrong
   */
  long export(Person person, long startTime, long stopTime) throws IOException {
    long claimCount = 0;
    boolean previousEmergency = false;

    for (HealthRecord.Encounter encounter : person.record.encounters) {
      if (encounter.stop < startTime || encounter.stop < CLAIM_CUTOFF) {
        continue;
      }
      if (!hasPartABCoverage(person, encounter.stop)) {
        continue;
      }
      if (!RIFExporter.getClaimTypes(encounter).contains(ClaimType.INPATIENT)) {
        previousEmergency = false;
        continue;
      }

      // Get subset of billable items
      List<Claim.ClaimEntry> billableItems = getBillableProcedureAndMedAdminItems(encounter);
      Claim.ClaimEntry billableTotal = encounter.claim.new ClaimEntry(null);
      for (Claim.ClaimEntry lineItem: billableItems) {
        billableTotal.addCosts(lineItem);
      }
      if (billableTotal.getTotalClaimCost().compareTo(Claim.ZERO_CENTS) == 0) {
        continue;
      }

      long claimId = RIFExporter.nextClaimId.getAndDecrement();
      long claimGroupId = RIFExporter.nextClaimGroupId.getAndDecrement();
      long fiDocId = RIFExporter.nextFiDocCntlNum.getAndDecrement();

      HashMap<BB2RIFStructure.INPATIENT, String> fieldValues = new HashMap<>();
      exporter.staticFieldConfig.setValues(fieldValues, BB2RIFStructure.INPATIENT.class, person);

      // The REQUIRED fields
      fieldValues.put(BB2RIFStructure.INPATIENT.BENE_ID,
              (String)person.attributes.get(RIFExporter.BB2_BENE_ID));
      fieldValues.put(BB2RIFStructure.INPATIENT.CLM_ID, "" + claimId);
      fieldValues.put(BB2RIFStructure.INPATIENT.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(BB2RIFStructure.INPATIENT.FI_DOC_CLM_CNTL_NUM, "" + fiDocId);
      fieldValues.put(BB2RIFStructure.INPATIENT.CLM_FROM_DT,
              RIFExporter.bb2DateFromTimestamp(encounter.start));
      fieldValues.put(BB2RIFStructure.INPATIENT.CLM_ADMSN_DT,
              RIFExporter.bb2DateFromTimestamp(encounter.start));
      fieldValues.put(BB2RIFStructure.INPATIENT.CLM_THRU_DT,
              RIFExporter.bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(BB2RIFStructure.INPATIENT.NCH_BENE_DSCHRG_DT,
              RIFExporter.bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(BB2RIFStructure.INPATIENT.NCH_WKLY_PROC_DT,
              RIFExporter.bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop)));
      fieldValues.put(BB2RIFStructure.INPATIENT.PRVDR_NUM,
              StringUtils.truncate(encounter.provider.cmsProviderNum, 6));
      fieldValues.put(BB2RIFStructure.INPATIENT.AT_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(BB2RIFStructure.INPATIENT.ORG_NPI_NUM, encounter.provider.npi);
      fieldValues.put(BB2RIFStructure.INPATIENT.OP_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(BB2RIFStructure.INPATIENT.PRVDR_STATE_CD,
              exporter.locationMapper.getStateCode(encounter.provider.state));
      // PTNT_DSCHRG_STUS_CD: 1=home, 2=transfer, 3=SNF, 20=died, 30=still here
      String field = null;
      String patientStatus = null;
      if (encounter.ended) {
        field = "1"; // TODO 2=transfer if the next encounter is also inpatient
        patientStatus = "A"; // discharged
      } else {
        field = "30"; // the patient is still here
        patientStatus = "C"; // still a patient
      }
      if (!person.alive(encounter.stop)) {
        field = "20"; // the patient died before the encounter ended
        patientStatus = "B"; // died
      }
      fieldValues.put(BB2RIFStructure.INPATIENT.PTNT_DSCHRG_STUS_CD, field);
      fieldValues.put(BB2RIFStructure.INPATIENT.NCH_PTNT_STATUS_IND_CD, patientStatus);
      boolean isEmergency = encounter.type.equals(HealthRecord.EncounterType.EMERGENCY.toString());
      if (isEmergency) {
        field = "1"; // emergency
        fieldValues.put(BB2RIFStructure.INPATIENT.REV_CNTR, "0450"); // emergency
      } else if (previousEmergency) {
        field = "2"; // urgent
      } else {
        field = "3"; // elective
      }
      fieldValues.put(BB2RIFStructure.INPATIENT.CLM_IP_ADMSN_TYPE_CD, field);
      int days = (int) ((encounter.stop - encounter.start) / (1000 * 60 * 60 * 24));
      fieldValues.put(BB2RIFStructure.INPATIENT.CLM_UTLZTN_DAY_CNT, "" + days);
      if (days > 60) {
        field = "" + (days - 60);
      } else {
        field = "0";
      }
      fieldValues.put(BB2RIFStructure.INPATIENT.BENE_TOT_COINSRNC_DAYS_CNT, field);
      if (days > 60) {
        field = "1"; // days outlier
      } else if (encounter.claim.getTotalClaimCost().compareTo(BigDecimal.valueOf(100_000)) > 0) {
        field = "2"; // cost outlier
      } else {
        field = "0"; // no outlier
      }
      fieldValues.put(BB2RIFStructure.INPATIENT.CLM_DRG_OUTLIER_STAY_CD, field);

      // OPTIONAL FIELDS
      fieldValues.put(BB2RIFStructure.INPATIENT.RNDRNG_PHYSN_NPI, encounter.clinician.npi);

      String icdReasonCode = null;
      if (encounter.reason != null) {
        // If the encounter has a recorded reason, enter the mapped
        // values into the principle diagnoses code.
        if (exporter.conditionCodeMapper.canMap(encounter.reason)) {
          icdReasonCode = exporter.conditionCodeMapper.map(encounter.reason, person, true);
          fieldValues.put(BB2RIFStructure.INPATIENT.PRNCPAL_DGNS_CD, icdReasonCode);
          fieldValues.put(BB2RIFStructure.INPATIENT.ADMTG_DGNS_CD, icdReasonCode);
        }
      }
      int diagnosisCount = mapDiagnoses(fieldValues, person, encounter);
      int procedureCount = mapProcedures(fieldValues, person, encounter);
      if (icdReasonCode == null && (diagnosisCount + procedureCount == 0)) {
        continue; // skip this encounter
      }

      previousEmergency = isEmergency;

      setClaimCosts(fieldValues, billableTotal);
      if (encounter.claim.coveredByMedicare()) {
        fieldValues.put(BB2RIFStructure.INPATIENT.NCH_PRMRY_PYR_CLM_PD_AMT, "0");
      } else {
        fieldValues.put(BB2RIFStructure.INPATIENT.NCH_PRMRY_PYR_CLM_PD_AMT,
                String.format("%.2f", billableTotal.getCoveredCost()));
      }

      String originalRandomRevCenter = fieldValues.get(BB2RIFStructure.INPATIENT.REV_CNTR);
      synchronized (exporter.rifWriters.getOrCreateWriter(BB2RIFStructure.INPATIENT.class)) {
        int claimLine = 1;
        for (Claim.ClaimEntry lineItem: billableItems) {
          String hcpcsCode = null;
          if (lineItem.entry instanceof HealthRecord.Procedure) {
            hcpcsCode = getFirstMappedHCPCSCode(lineItem.entry.codes, person);
            String revCntr = originalRandomRevCenter;
            if (exporter.inpatientRevCntrMapper.canMap(hcpcsCode)) {
              revCntr = exporter.inpatientRevCntrMapper.map(hcpcsCode, person);
            }
            fieldValues.put(BB2RIFStructure.INPATIENT.REV_CNTR, revCntr);
            fieldValues.remove(BB2RIFStructure.INPATIENT.REV_CNTR_NDC_QTY);
            fieldValues.remove(BB2RIFStructure.INPATIENT.REV_CNTR_NDC_QTY_QLFR_CD);
          } else if (lineItem.entry instanceof HealthRecord.Medication) {
            HealthRecord.Medication med = (HealthRecord.Medication) lineItem.entry;
            if (med.administration) {
              hcpcsCode = "T1502";  // Administration of medication
              // Pharmacy-general classification
              fieldValues.put(BB2RIFStructure.INPATIENT.REV_CNTR, "0250");
              fieldValues.put(BB2RIFStructure.INPATIENT.REV_CNTR_NDC_QTY, "1"); // 1 Unit
              fieldValues.put(BB2RIFStructure.INPATIENT.REV_CNTR_NDC_QTY_QLFR_CD, "UN"); // Unit
            }
          }

          fieldValues.put(BB2RIFStructure.INPATIENT.CLM_LINE_NUM, Integer.toString(claimLine++));
          fieldValues.put(BB2RIFStructure.INPATIENT.HCPCS_CD, hcpcsCode);
          setLineItemCosts(fieldValues, lineItem, days);
          exporter.rifWriters.writeValues(BB2RIFStructure.INPATIENT.class, fieldValues);
        }

        // Add a total charge entry.
        fieldValues.remove(BB2RIFStructure.INPATIENT.REV_CNTR_NDC_QTY);
        fieldValues.remove(BB2RIFStructure.INPATIENT.REV_CNTR_NDC_QTY_QLFR_CD);
        fieldValues.put(BB2RIFStructure.INPATIENT.CLM_LINE_NUM, Integer.toString(claimLine));
        // HCPCS 99221: "Inpatient hospital visits: Initial and subsequent"
        fieldValues.put(BB2RIFStructure.INPATIENT.REV_CNTR, "0001");
        fieldValues.put(BB2RIFStructure.INPATIENT.HCPCS_CD, "99221");
        setLineItemCosts(fieldValues, billableTotal, 1);
        exporter.rifWriters.writeValues(BB2RIFStructure.INPATIENT.class, fieldValues);
      }
      claimCount++;
    }
    return claimCount;
  }

  private void setClaimCosts(HashMap<BB2RIFStructure.INPATIENT, String> fieldValues,
          Claim.ClaimEntry claim) {
    fieldValues.put(BB2RIFStructure.INPATIENT.CLM_PMT_AMT,
            String.format("%.2f", claim.getCoveredCost()));
    fieldValues.put(BB2RIFStructure.INPATIENT.CLM_TOT_CHRG_AMT,
            String.format("%.2f", claim.getTotalClaimCost()));
    fieldValues.put(BB2RIFStructure.INPATIENT.NCH_BENE_IP_DDCTBL_AMT,
            String.format("%.2f", claim.getDeductiblePaid()));
    fieldValues.put(BB2RIFStructure.INPATIENT.NCH_BENE_PTA_COINSRNC_LBLTY_AM,
            String.format("%.2f", claim.getCoinsurancePaid()));
    fieldValues.put(BB2RIFStructure.INPATIENT.NCH_IP_NCVRD_CHRG_AMT,
            String.format("%.2f", claim.getPatientCost()));
    fieldValues.put(BB2RIFStructure.INPATIENT.NCH_IP_TOT_DDCTN_AMT,
            String.format("%.2f", claim.getPatientCost()));
  }

  private void setLineItemCosts(HashMap<BB2RIFStructure.INPATIENT, String> fieldValues,
          Claim.ClaimEntry claim, int days) {
    fieldValues.put(BB2RIFStructure.INPATIENT.REV_CNTR_UNIT_CNT, "" + Integer.max(1, days));
    BigDecimal rate = claim.getTotalClaimCost().divide(
            BigDecimal.valueOf(Integer.max(1, days)), RoundingMode.HALF_EVEN)
            .setScale(2, RoundingMode.HALF_EVEN);
    fieldValues.put(BB2RIFStructure.INPATIENT.REV_CNTR_RATE_AMT,
        String.format("%.2f", rate));
    fieldValues.put(BB2RIFStructure.INPATIENT.REV_CNTR_TOT_CHRG_AMT,
            String.format("%.2f", claim.getTotalClaimCost()));
    fieldValues.put(BB2RIFStructure.INPATIENT.REV_CNTR_NCVRD_CHRG_AMT,
            String.format("%.2f", claim.getPatientCost()));
    if (claim.patientOutOfPocket.compareTo(Claim.ZERO_CENTS) == 0
            && claim.getDeductiblePaid().compareTo(Claim.ZERO_CENTS) == 0) {
      // Not subject to deductible or coinsurance
      fieldValues.put(BB2RIFStructure.INPATIENT.REV_CNTR_DDCTBL_COINSRNC_CD, "3");
    } else if (claim.patientOutOfPocket.compareTo(Claim.ZERO_CENTS) > 0
            && claim.getDeductiblePaid().compareTo(Claim.ZERO_CENTS) > 0) {
      // Subject to deductible and coinsurance
      fieldValues.put(BB2RIFStructure.INPATIENT.REV_CNTR_DDCTBL_COINSRNC_CD, "0");
    } else if (claim.patientOutOfPocket.compareTo(Claim.ZERO_CENTS) == 0) {
      // Not subject to deductible
      fieldValues.put(BB2RIFStructure.INPATIENT.REV_CNTR_DDCTBL_COINSRNC_CD, "1");
    } else {
      // Not subject to coinsurance
      fieldValues.put(BB2RIFStructure.INPATIENT.REV_CNTR_DDCTBL_COINSRNC_CD, "2");
    }
  }

  private int mapDiagnoses(Map<BB2RIFStructure.INPATIENT, String> fieldValues, Person person,
          HealthRecord.Encounter encounter) {
    // Use the active condition diagnoses to enter mapped values
    // into the diagnoses codes.
    List<String> presentOnAdmission = getDiagnosesCodes(person, encounter.start);
    List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, encounter.stop);
    int smallest = Math.min(mappedDiagnosisCodes.size(),
            BB2RIFStructure.inpatientDxFields.length);
    if (!mappedDiagnosisCodes.isEmpty()) {
      for (int i = 0; i < smallest; i++) {
        BB2RIFStructure.INPATIENT[] dxField = BB2RIFStructure.inpatientDxFields[i];
        String dxCode = mappedDiagnosisCodes.get(i);
        fieldValues.put(dxField[0], dxCode);
        fieldValues.put(dxField[1], "0"); // 0=ICD10
        String present = presentOnAdmission.contains(dxCode) ? "Y" : "N";
        fieldValues.put(dxField[2], present);
      }
      if (!fieldValues.containsKey(BB2RIFStructure.INPATIENT.PRNCPAL_DGNS_CD)) {
        fieldValues.put(BB2RIFStructure.INPATIENT.PRNCPAL_DGNS_CD, mappedDiagnosisCodes.get(0));
      }
    }

    if (fieldValues.containsKey(BB2RIFStructure.INPATIENT.PRNCPAL_DGNS_CD)) {
      String icdCode = fieldValues.get(BB2RIFStructure.INPATIENT.PRNCPAL_DGNS_CD);
      // Add a DRG code, if applicable
      if (exporter.drgCodeMapper.canMap(icdCode)) {
        fieldValues.put(BB2RIFStructure.INPATIENT.CLM_DRG_CD,
                exporter.drgCodeMapper.map(icdCode, person));
      }
      // Check for external code...
      exporter.setExternalCode(person, fieldValues,
          BB2RIFStructure.INPATIENT.PRNCPAL_DGNS_CD, BB2RIFStructure.INPATIENT.ICD_DGNS_E_CD1,
          BB2RIFStructure.INPATIENT.ICD_DGNS_E_VRSN_CD1,
          BB2RIFStructure.INPATIENT.CLM_E_POA_IND_SW1, presentOnAdmission);
      exporter.setExternalCode(person, fieldValues,
          BB2RIFStructure.INPATIENT.PRNCPAL_DGNS_CD, BB2RIFStructure.INPATIENT.FST_DGNS_E_CD,
          BB2RIFStructure.INPATIENT.FST_DGNS_E_VRSN_CD);
    }

    return smallest;
  }

  private int mapProcedures(Map<BB2RIFStructure.INPATIENT, String> fieldValues, Person person,
          HealthRecord.Encounter encounter) {
    // Use the procedures in this encounter to enter mapped values
    int procedureCount = 0;
    if (!encounter.procedures.isEmpty()) {
      List<HealthRecord.Procedure> mappableProcedures = new ArrayList<>();
      List<String> mappedProcedureCodes = new ArrayList<>();
      for (HealthRecord.Procedure procedure : encounter.procedures) {
        for (HealthRecord.Code code : procedure.codes) {
          if (exporter.conditionCodeMapper.canMap(code)) {
            mappableProcedures.add(procedure);
            mappedProcedureCodes.add(exporter.conditionCodeMapper.map(code, person, true));
            break; // take the first mappable code for each procedure
          }
        }
      }
      if (!mappableProcedures.isEmpty()) {
        procedureCount = Math.min(mappableProcedures.size(),
                BB2RIFStructure.inpatientPxFields.length);
        for (int i = 0; i < procedureCount; i++) {
          BB2RIFStructure.INPATIENT[] pxField = BB2RIFStructure.inpatientPxFields[i];
          fieldValues.put(pxField[0], mappedProcedureCodes.get(i));
          fieldValues.put(pxField[1], "0"); // 0=ICD10
          fieldValues.put(pxField[2], bb2DateFromTimestamp(mappableProcedures.get(i).start));
        }
      }
    }
    return procedureCount;
  }

}
