package org.mitre.synthea.export.rif;

import java.io.IOException;
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
 * Exporter for RIF Outpatient File.
 */
public class OutpatientExporter extends RIFExporter {

  /**
   * Construct an exporter for Outpatient claims.
   * @param exporter the exporter instance that will be used to access code mappers
   */
  public OutpatientExporter(BB2RIFExporter exporter) {
    super(exporter);
  }

  /**
   * Export outpatient claims details for a single person.
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
      if (!RIFExporter.getClaimTypes(encounter).contains(ClaimType.OUTPATIENT)) {
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

      HashMap<BB2RIFStructure.OUTPATIENT, String> fieldValues = new HashMap<>();
      exporter.staticFieldConfig.setValues(fieldValues, BB2RIFStructure.OUTPATIENT.class, person);

      // The REQUIRED fields
      fieldValues.put(BB2RIFStructure.OUTPATIENT.BENE_ID,
              (String)person.attributes.get(RIFExporter.BB2_BENE_ID));
      fieldValues.put(BB2RIFStructure.OUTPATIENT.CLM_ID, "" + claimId);
      fieldValues.put(BB2RIFStructure.OUTPATIENT.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(BB2RIFStructure.OUTPATIENT.FI_DOC_CLM_CNTL_NUM, "" + fiDocId);
      fieldValues.put(BB2RIFStructure.OUTPATIENT.CLM_FROM_DT,
              RIFExporter.bb2DateFromTimestamp(encounter.start));
      fieldValues.put(BB2RIFStructure.OUTPATIENT.CLM_THRU_DT,
              RIFExporter.bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(BB2RIFStructure.OUTPATIENT.NCH_WKLY_PROC_DT,
              RIFExporter.bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop)));
      fieldValues.put(BB2RIFStructure.OUTPATIENT.PRVDR_NUM,
              StringUtils.truncate(encounter.provider.cmsProviderNum, 6));
      fieldValues.put(BB2RIFStructure.OUTPATIENT.AT_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(BB2RIFStructure.OUTPATIENT.RNDRNG_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(BB2RIFStructure.OUTPATIENT.ORG_NPI_NUM, encounter.provider.npi);
      fieldValues.put(BB2RIFStructure.OUTPATIENT.OP_PHYSN_NPI, encounter.clinician.npi);

      fieldValues.put(BB2RIFStructure.OUTPATIENT.PRVDR_STATE_CD,
              exporter.locationMapper.getStateCode(encounter.provider.state));
      // PTNT_DSCHRG_STUS_CD: 1=home, 2=transfer, 3=SNF, 20=died, 30=still here
      String field = null;
      if (encounter.ended) {
        field = "1";
      } else {
        field = "30"; // the patient is still here
      }
      if (!person.alive(encounter.stop)) {
        field = "20"; // the patient died before the encounter ended
      }
      fieldValues.put(BB2RIFStructure.OUTPATIENT.PTNT_DSCHRG_STUS_CD, field);

      String icdReasonCode = null;
      if (encounter.reason != null) {
        // If the encounter has a recorded reason, enter the mapped
        // values into the principle diagnoses code.
        if (exporter.conditionCodeMapper.canMap(encounter.reason)) {
          icdReasonCode = exporter.conditionCodeMapper.map(encounter.reason, person, true);
          fieldValues.put(BB2RIFStructure.OUTPATIENT.PRNCPAL_DGNS_CD, icdReasonCode);
        }
      }

      int diagnosisCount = mapDiagnoses(fieldValues, person, encounter);
      int procedureCount = mapProcedures(fieldValues, person, encounter);
      if (icdReasonCode == null && (diagnosisCount + procedureCount == 0)) {
        continue; // skip this encounter
      }

      setClaimCosts(fieldValues, billableTotal);
      if (encounter.claim.coveredByMedicare()) {
        fieldValues.put(BB2RIFStructure.OUTPATIENT.NCH_PRMRY_PYR_CLM_PD_AMT, "0");
      } else {
        fieldValues.put(BB2RIFStructure.OUTPATIENT.NCH_PRMRY_PYR_CLM_PD_AMT,
                String.format("%.2f", billableTotal.getCoveredCost()));
      }

      String originalRandomRevCenter = fieldValues.get(BB2RIFStructure.OUTPATIENT.REV_CNTR);

      synchronized (exporter.rifWriters.getOrCreateWriter(BB2RIFStructure.OUTPATIENT.class)) {
        int claimLine = 1;
        for (Claim.ClaimEntry lineItem : billableItems) {
          String hcpcsCode = null;
          if (lineItem.entry instanceof HealthRecord.Procedure) {
            hcpcsCode = getFirstMappedHCPCSCode(lineItem.entry.codes, person);
            String revCenter = originalRandomRevCenter;
            if (encounter.type.equals(HealthRecord.EncounterType.VIRTUAL.toString())) {
              revCenter = person.randBoolean() ? "0780" : "0789";
            } else if (exporter.outpatientRevCntrMapper.canMap(hcpcsCode)) {
              revCenter = exporter.outpatientRevCntrMapper.map(hcpcsCode, person);
            }
            fieldValues.put(BB2RIFStructure.OUTPATIENT.REV_CNTR, revCenter);
            fieldValues.remove(BB2RIFStructure.OUTPATIENT.REV_CNTR_IDE_NDC_UPC_NUM);
            fieldValues.remove(BB2RIFStructure.OUTPATIENT.REV_CNTR_NDC_QTY);
            fieldValues.remove(BB2RIFStructure.OUTPATIENT.REV_CNTR_NDC_QTY_QLFR_CD);
          } else if (lineItem.entry instanceof HealthRecord.Medication) {
            HealthRecord.Medication med = (HealthRecord.Medication) lineItem.entry;
            if (med.administration) {
              hcpcsCode = "T1502";  // Administration of medication
              // Drugs requiring specific id
              fieldValues.put(BB2RIFStructure.OUTPATIENT.REV_CNTR, "0636");
              String ndcCode = exporter.medicationCodeMapper.map(med.codes.get(0), person);
              fieldValues.put(BB2RIFStructure.OUTPATIENT.REV_CNTR_IDE_NDC_UPC_NUM, ndcCode);
              fieldValues.put(BB2RIFStructure.OUTPATIENT.REV_CNTR_NDC_QTY, "1"); // 1 Unit
              fieldValues.put(BB2RIFStructure.OUTPATIENT.REV_CNTR_NDC_QTY_QLFR_CD, "UN"); // Unit
            }
          }

          fieldValues.put(BB2RIFStructure.OUTPATIENT.CLM_LINE_NUM, Integer.toString(claimLine++));
          fieldValues.put(BB2RIFStructure.OUTPATIENT.REV_CNTR_DT,
                  RIFExporter.bb2DateFromTimestamp(lineItem.entry.start));
          fieldValues.put(BB2RIFStructure.OUTPATIENT.HCPCS_CD, hcpcsCode);
          setLineItemCosts(fieldValues, lineItem);
          exporter.rifWriters.writeValues(BB2RIFStructure.OUTPATIENT.class, fieldValues);
        }

        // Add a total charge entry.
        fieldValues.remove(BB2RIFStructure.OUTPATIENT.REV_CNTR_IDE_NDC_UPC_NUM);
        fieldValues.remove(BB2RIFStructure.OUTPATIENT.REV_CNTR_NDC_QTY);
        fieldValues.remove(BB2RIFStructure.OUTPATIENT.REV_CNTR_NDC_QTY_QLFR_CD);
        fieldValues.put(BB2RIFStructure.OUTPATIENT.REV_CNTR, "0001");
        // For outpatient total charge (0001) rev center, 99.95% of HCPCS codes are blank
        fieldValues.remove(BB2RIFStructure.OUTPATIENT.HCPCS_CD);
        fieldValues.put(BB2RIFStructure.OUTPATIENT.CLM_LINE_NUM, Integer.toString(claimLine));
        fieldValues.put(BB2RIFStructure.OUTPATIENT.REV_CNTR_DT,
                RIFExporter.bb2DateFromTimestamp(encounter.start));
        setLineItemCosts(fieldValues, billableTotal);
        exporter.rifWriters.writeValues(BB2RIFStructure.OUTPATIENT.class, fieldValues);
      }
      claimCount++;
    }
    return claimCount;
  }

  private void setClaimCosts(HashMap<BB2RIFStructure.OUTPATIENT, String> fieldValues,
          Claim.ClaimEntry claim) {
    fieldValues.put(BB2RIFStructure.OUTPATIENT.CLM_PMT_AMT, String.format("%.2f",
            claim.getCoveredCost()));
    fieldValues.put(BB2RIFStructure.OUTPATIENT.CLM_TOT_CHRG_AMT,
            String.format("%.2f", claim.getTotalClaimCost()));
    fieldValues.put(BB2RIFStructure.OUTPATIENT.CLM_OP_PRVDR_PMT_AMT,
            String.format("%.2f", claim.getTotalClaimCost()));
    fieldValues.put(BB2RIFStructure.OUTPATIENT.NCH_BENE_PTB_DDCTBL_AMT,
            String.format("%.2f", claim.getDeductiblePaid()));
  }

  private void setLineItemCosts(HashMap<BB2RIFStructure.OUTPATIENT, String> fieldValues,
          Claim.ClaimEntry claim) {
    fieldValues.put(BB2RIFStructure.OUTPATIENT.REV_CNTR_NCVRD_CHRG_AMT,
            String.format("%.2f", claim.getPatientCost()));
    fieldValues.put(BB2RIFStructure.OUTPATIENT.REV_CNTR_CASH_DDCTBL_AMT,
            String.format("%.2f", claim.getDeductiblePaid()));
    fieldValues.put(BB2RIFStructure.OUTPATIENT.REV_CNTR_COINSRNC_WGE_ADJSTD_C,
            String.format("%.2f", claim.getCoinsurancePaid()));
    fieldValues.put(BB2RIFStructure.OUTPATIENT.REV_CNTR_PRVDR_PMT_AMT,
            String.format("%.2f", claim.getTotalClaimCost()));
    fieldValues.put(BB2RIFStructure.OUTPATIENT.REV_CNTR_PTNT_RSPNSBLTY_PMT,
            String.format("%.2f", claim.getDeductiblePaid().add(claim.getCoinsurancePaid())));
    fieldValues.put(BB2RIFStructure.OUTPATIENT.REV_CNTR_RDCD_COINSRNC_AMT,
            String.format("%.2f", claim.getCoinsurancePaid()));
    fieldValues.put(BB2RIFStructure.OUTPATIENT.REV_CNTR_RATE_AMT,
        String.format("%.2f", claim.getTotalClaimCost()));
    fieldValues.put(BB2RIFStructure.OUTPATIENT.REV_CNTR_PMT_AMT_AMT,
        String.format("%.2f", claim.getCoveredCost()));
    fieldValues.put(BB2RIFStructure.OUTPATIENT.REV_CNTR_TOT_CHRG_AMT,
        String.format("%.2f", claim.getTotalClaimCost()));
    fieldValues.put(BB2RIFStructure.OUTPATIENT.REV_CNTR_NCVRD_CHRG_AMT,
        String.format("%.2f", claim.getPatientCost()));
  }

  private int mapDiagnoses(Map<BB2RIFStructure.OUTPATIENT, String> fieldValues, Person person,
        HealthRecord.Encounter encounter) {
    // Use the active condition diagnoses to enter mapped values
    // into the diagnoses codes.
    List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, encounter.stop);
    boolean noDiagnoses = mappedDiagnosisCodes.isEmpty();
    int smallest = Math.min(mappedDiagnosisCodes.size(),
            BB2RIFStructure.outpatientDxFields.length);
    if (!noDiagnoses) {
      for (int i = 0; i < smallest; i++) {
        BB2RIFStructure.OUTPATIENT[] dxField = BB2RIFStructure.outpatientDxFields[i];
        fieldValues.put(dxField[0], mappedDiagnosisCodes.get(i));
        fieldValues.put(dxField[1], "0"); // 0=ICD10
      }
      if (!fieldValues.containsKey(BB2RIFStructure.OUTPATIENT.PRNCPAL_DGNS_CD)) {
        fieldValues.put(BB2RIFStructure.OUTPATIENT.PRNCPAL_DGNS_CD, mappedDiagnosisCodes.get(0));
      }
    }

    // Check for external code...
    exporter.setExternalCode(person, fieldValues,
        BB2RIFStructure.OUTPATIENT.PRNCPAL_DGNS_CD,
        BB2RIFStructure.OUTPATIENT.ICD_DGNS_E_CD1,
        BB2RIFStructure.OUTPATIENT.ICD_DGNS_E_VRSN_CD1);
    exporter.setExternalCode(person, fieldValues,
        BB2RIFStructure.OUTPATIENT.PRNCPAL_DGNS_CD,
        BB2RIFStructure.OUTPATIENT.FST_DGNS_E_CD,
        BB2RIFStructure.OUTPATIENT.FST_DGNS_E_VRSN_CD);

    return smallest;
  }

  private int mapProcedures(Map<BB2RIFStructure.OUTPATIENT, String> fieldValues, Person person,
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
                BB2RIFStructure.outpatientPxFields.length);
        for (int i = 0; i < procedureCount; i++) {
          BB2RIFStructure.OUTPATIENT[] pxField = BB2RIFStructure.outpatientPxFields[i];
          fieldValues.put(pxField[0], mappedProcedureCodes.get(i));
          fieldValues.put(pxField[1], "0"); // 0=ICD10
          fieldValues.put(pxField[2],
                  RIFExporter.bb2DateFromTimestamp(mappableProcedures.get(i).start));
        }
      }
    }
    return procedureCount;
  }
}
