package org.mitre.synthea.export.rif;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.mitre.synthea.export.ExportHelper;
import org.mitre.synthea.helpers.ConsolidatedServicePeriods;
import org.mitre.synthea.helpers.ConsolidatedServicePeriods.ConsolidatedServicePeriod;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.HealthRecord;

/**
 * Exporter for RIF HHA (home health agency) file.
 */
public class HHAExporter extends RIFExporter {

  private static final long HHA_PPS_CASE_MIX_START = parseSimpleDate("20080101");
  private static final long HHA_PPS_PDGM_START = parseSimpleDate("20200101");

  /**
   * Construct an exporter for HHA claims.
   * @param exporter the exporter instance that will be used to access code mappers
   */
  public HHAExporter(BB2RIFExporter exporter) {
    super(exporter);
  }

  /**
   * Export HHA visits for a single person.
   * @param person the person to export
   * @param startTime earliest claim date to export
   * @param stopTime end time of simulation
   * @return count of claims exported
   * @throws IOException if something goes wrong
   */
  long export(Person person, long startTime, long stopTime) throws IOException {
    HashMap<BB2RIFStructure.HHA, String> fieldValues = new HashMap<>();
    long claimCount = 0;

    long maxGapForContinuousHHAService = Utilities.convertTime("days", 2);
    ConsolidatedServicePeriods servicePeriods = new ConsolidatedServicePeriods(
            maxGapForContinuousHHAService);
    for (HealthRecord.Encounter encounter : person.record.encounters) {
      if (encounter.stop < startTime || encounter.stop < CLAIM_CUTOFF) {
        continue;
      }
      if (!hasPartABCoverage(person, encounter.stop)) {
        continue;
      }
      if (!RIFExporter.getClaimTypes(encounter).contains(ClaimType.HHA)) {
        continue;
      }
      List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, encounter.stop);
      if (mappedDiagnosisCodes.isEmpty()) {
        continue; // skip this encounter
      }
      servicePeriods.addEncounter(encounter);
    }

    for (ConsolidatedServicePeriod servicePeriod: servicePeriods.getPeriods()) {
      long claimId = RIFExporter.nextClaimId.getAndDecrement();
      long claimGroupId = RIFExporter.nextClaimGroupId.getAndDecrement();
      long fiDocId = RIFExporter.nextFiDocCntlNum.getAndDecrement();

      fieldValues.clear();
      exporter.staticFieldConfig.setValues(fieldValues, BB2RIFStructure.HHA.class, person);

      // The REQUIRED fields
      fieldValues.put(BB2RIFStructure.HHA.BENE_ID,
              (String)person.attributes.get(RIFExporter.BB2_BENE_ID));
      fieldValues.put(BB2RIFStructure.HHA.CLM_ID, "" + claimId);
      fieldValues.put(BB2RIFStructure.HHA.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(BB2RIFStructure.HHA.FI_DOC_CLM_CNTL_NUM, "" + fiDocId);
      fieldValues.put(BB2RIFStructure.HHA.CLM_FROM_DT,
              RIFExporter.bb2DateFromTimestamp(servicePeriod.getStart()));
      fieldValues.put(BB2RIFStructure.HHA.CLM_ADMSN_DT,
              RIFExporter.bb2DateFromTimestamp(servicePeriod.getStart()));
      fieldValues.put(BB2RIFStructure.HHA.CLM_THRU_DT,
              RIFExporter.bb2DateFromTimestamp(servicePeriod.getStop()));
      fieldValues.put(BB2RIFStructure.HHA.NCH_WKLY_PROC_DT,
          RIFExporter.bb2DateFromTimestamp(ExportHelper.nextFriday(servicePeriod.getStop())));

      // random from fields TSV, may be overriden below
      String revCenter = fieldValues.get(BB2RIFStructure.HHA.REV_CNTR);

      final String HHA_TOTAL_CHARGE_REV_CNTR = "0001"; // Total charge
      final String HHA_GENERAL_REV_CNTR = "0270"; // General medical/surgical supplies
      final String HHA_PPS_REV_CNTR = "0023"; // Prospective payment system
      final String HHA_MEDICATION_CODE = "T1502"; // Administration of medication

      // Select a PPS code for this service period (if a PPS program was in place at the time).
      // Only one PPS code per service period since the code is based on patient characteristics
      // and care need.
      // TODO: rather than pick a weighted random PPS code, pick a code based on current patient
      // characteristics.
      String ppsCode = null;
      if (servicePeriod.getStart() > HHA_PPS_PDGM_START) {
        ppsCode = exporter.hhaPDGMCodes.next(person);
      } else if (servicePeriod.getStart() > HHA_PPS_CASE_MIX_START) {
        ppsCode = exporter.hhaCaseMixCodes.next(person);
      }

      ConsolidatedClaimLines consolidatedClaimLines = new ConsolidatedClaimLines();
      for (HealthRecord.Encounter encounter : servicePeriod.getEncounters()) {
        for (Claim.ClaimEntry lineItem : encounter.claim.items) {
          String hcpcsCode = null;
          if (lineItem.entry instanceof HealthRecord.Procedure) {
            // 10% of line items use a PPS code, use higher number here to account for
            // every claim having a total charge line
            if (ppsCode != null && person.rand() < 0.15) {
              hcpcsCode = ppsCode;
              revCenter = HHA_PPS_REV_CNTR;
            } else {
              for (HealthRecord.Code code : lineItem.entry.codes) {
                if (exporter.hcpcsCodeMapper.canMap(code)) {
                  hcpcsCode = exporter.hcpcsCodeMapper.map(code, person, true);
                  if (exporter.hhaRevCntrMapper.canMap(hcpcsCode)) {
                    revCenter = exporter.hhaRevCntrMapper.map(hcpcsCode, person);
                  }
                  break; // take the first mappable code for each procedure
                }
              }
              if (hcpcsCode == null) {
                revCenter = HHA_GENERAL_REV_CNTR;
              }
            }
            consolidatedClaimLines.addClaimLine(hcpcsCode, revCenter, lineItem, encounter);
          } else if (lineItem.entry instanceof HealthRecord.Medication) {
            HealthRecord.Medication med = (HealthRecord.Medication) lineItem.entry;
            if (med.administration) {
              hcpcsCode = HHA_MEDICATION_CODE;
              revCenter = HHA_GENERAL_REV_CNTR;
              consolidatedClaimLines.addClaimLine(hcpcsCode, revCenter, lineItem, encounter);
            }
          }
        }
      }

      fieldValues.put(BB2RIFStructure.HHA.CLM_PMT_AMT,
          String.format("%.2f", consolidatedClaimLines.getCoveredCost()));
      fieldValues.put(BB2RIFStructure.HHA.CLM_TOT_CHRG_AMT,
          String.format("%.2f", consolidatedClaimLines.getTotalClaimCost()));
      fieldValues.put(BB2RIFStructure.HHA.CLM_HHA_TOT_VISIT_CNT,
              "" + servicePeriod.getEncounters().size());
      fieldValues.put(BB2RIFStructure.HHA.PRVDR_NUM,
              StringUtils.truncate(servicePeriod.getProvider().cmsProviderNum, 6));
      fieldValues.put(BB2RIFStructure.HHA.ORG_NPI_NUM, servicePeriod.getProvider().npi);
      fieldValues.put(BB2RIFStructure.HHA.PRVDR_STATE_CD,
          exporter.locationMapper.getStateCode(servicePeriod.getProvider().state));

      // Use the final encounter in the service period to set all of the remaining field values that
      // are the same for all claim lines
      // TODO: update ConsolidatedServicePeriods to separate encounters based on provider, clinician
      HealthRecord.Encounter finalEncounterOfPeriod = servicePeriod.getEncounters().get(
              servicePeriod.getEncounters().size() - 1);
      if (finalEncounterOfPeriod.claim.coveredByMedicare()) {
        fieldValues.put(BB2RIFStructure.HHA.NCH_PRMRY_PYR_CLM_PD_AMT, "0");
      } else {
        fieldValues.put(BB2RIFStructure.HHA.NCH_PRMRY_PYR_CLM_PD_AMT,
            String.format("%.2f", servicePeriod.getTotalCost().getCoveredCost()));
      }
      if (finalEncounterOfPeriod.reason != null) {
        // If the encounter has a recorded reason, enter the mapped
        // values into the principle diagnoses code.
        if (exporter.conditionCodeMapper.canMap(finalEncounterOfPeriod.reason)) {
          String icdCode = exporter.conditionCodeMapper.map(finalEncounterOfPeriod.reason,
                  person, true);
          fieldValues.put(BB2RIFStructure.HHA.PRNCPAL_DGNS_CD, icdCode);
        }
      }
      // PTNT_DSCHRG_STUS_CD: 1=home, 2=transfer, 3=SNF, 20=died, 30=still here
      String dischargeStatus = "1"; //discharged
      if (!person.alive(servicePeriod.getStop())) {
        dischargeStatus = "20"; // the patient died before the service period ended
      } else if (servicePeriod.getStop() > stopTime) {
        // TBD: revisit if we break up service periods into multiple billing periods, can then set
        // this for all but the final billing period within a service period.
        dischargeStatus = "30"; // the patient is still having treatment
      }
      fieldValues.put(BB2RIFStructure.HHA.PTNT_DSCHRG_STUS_CD, dischargeStatus);
      // Use the active condition diagnoses to enter mapped values
      // into the diagnoses codes.
      List<String> mappedDiagnosisCodes = getDiagnosesCodes(person,
              finalEncounterOfPeriod.stop);
      int smallest = Math.min(mappedDiagnosisCodes.size(),
              BB2RIFStructure.homeDxFields.length);
      for (int i = 0; i < smallest; i++) {
        BB2RIFStructure.HHA[] dxField = BB2RIFStructure.homeDxFields[i];
        fieldValues.put(dxField[0], mappedDiagnosisCodes.get(i));
        fieldValues.put(dxField[1], "0"); // 0=ICD10
      }
      if (!fieldValues.containsKey(BB2RIFStructure.HHA.PRNCPAL_DGNS_CD)) {
        fieldValues.put(BB2RIFStructure.HHA.PRNCPAL_DGNS_CD, mappedDiagnosisCodes.get(0));
      }

      // Check for external code...
      exporter.setExternalCode(person, fieldValues,
          BB2RIFStructure.HHA.PRNCPAL_DGNS_CD, BB2RIFStructure.HHA.ICD_DGNS_E_CD1,
          BB2RIFStructure.HHA.ICD_DGNS_E_VRSN_CD1);
      exporter.setExternalCode(person, fieldValues,
          BB2RIFStructure.HHA.PRNCPAL_DGNS_CD, BB2RIFStructure.HHA.FST_DGNS_E_CD,
          BB2RIFStructure.HHA.FST_DGNS_E_VRSN_CD);

      // now loop over all of the consolidated claim lines and write a row for each
      synchronized (exporter.rifWriters.getOrCreateWriter(BB2RIFStructure.HHA.class)) {
        int claimLine = 1;
        for (ConsolidatedClaimLines.ConsolidatedClaimLine lineItem:
                consolidatedClaimLines.getLines()) {
          fieldValues.put(BB2RIFStructure.HHA.HCPCS_CD, lineItem.getCode());
          fieldValues.put(BB2RIFStructure.HHA.AT_PHYSN_NPI, lineItem.getClinician().npi);
          fieldValues.put(BB2RIFStructure.HHA.RNDRNG_PHYSN_NPI, lineItem.getClinician().npi);
          fieldValues.put(BB2RIFStructure.HHA.REV_CNTR_DT,
                  RIFExporter.bb2DateFromTimestamp(lineItem.getStart()));
          int revCntrCount = lineItem.getCount();
          switch (lineItem.getCode()) {
            case HHA_MEDICATION_CODE:
              fieldValues.put(BB2RIFStructure.HHA.REV_CNTR, lineItem.getRevCntr());
              fieldValues.put(BB2RIFStructure.HHA.REV_CNTR_NDC_QTY,
                      Integer.toString(revCntrCount));
              fieldValues.put(BB2RIFStructure.HHA.REV_CNTR_NDC_QTY_QLFR_CD, "UN"); // Unit
              fieldValues.remove(BB2RIFStructure.HHA.REV_CNTR_UNIT_CNT);
              break;
            default:
              fieldValues.put(BB2RIFStructure.HHA.REV_CNTR, lineItem.getRevCntr());
              fieldValues.put(BB2RIFStructure.HHA.REV_CNTR_UNIT_CNT,
                      Integer.toString(revCntrCount));
              fieldValues.remove(BB2RIFStructure.HHA.REV_CNTR_NDC_QTY);
              fieldValues.remove(BB2RIFStructure.HHA.REV_CNTR_NDC_QTY_QLFR_CD);
              break;
          }

          fieldValues.put(BB2RIFStructure.HHA.CLM_LINE_NUM, Integer.toString(claimLine++));
          setHHAClaimLineCosts(fieldValues, lineItem, Integer.max(1, revCntrCount));
          exporter.rifWriters.writeValues(BB2RIFStructure.HHA.class, fieldValues);
        }

        // Add a total charge entry.
        setHHAClaimLineCosts(fieldValues, consolidatedClaimLines, 1);
        fieldValues.put(BB2RIFStructure.HHA.CLM_LINE_NUM, Integer.toString(claimLine++));
        fieldValues.put(BB2RIFStructure.HHA.REV_CNTR, HHA_TOTAL_CHARGE_REV_CNTR);
        fieldValues.put(BB2RIFStructure.HHA.REV_CNTR_DT,
                RIFExporter.bb2DateFromTimestamp(servicePeriod.getStop()));
        fieldValues.remove(BB2RIFStructure.HHA.HCPCS_CD);
        fieldValues.remove(BB2RIFStructure.HHA.AT_PHYSN_NPI);
        fieldValues.remove(BB2RIFStructure.HHA.RNDRNG_PHYSN_NPI);
        fieldValues.remove(BB2RIFStructure.HHA.REV_CNTR_RATE_AMT);
        fieldValues.remove(BB2RIFStructure.HHA.REV_CNTR_UNIT_CNT);
        fieldValues.remove(BB2RIFStructure.HHA.REV_CNTR_DDCTBL_COINSRNC_CD);
        exporter.rifWriters.writeValues(BB2RIFStructure.HHA.class, fieldValues);
      }
      claimCount++;
    }
    return claimCount;
  }

  private static void setHHAClaimLineCosts(HashMap<BB2RIFStructure.HHA, String> fieldValues,
          Claim.ClaimCost lineItem, int count) {
    fieldValues.put(BB2RIFStructure.HHA.REV_CNTR_RATE_AMT,
            String.format("%.2f",
                    lineItem.cost.divide(BigDecimal.valueOf(count),
                            RoundingMode.HALF_EVEN).setScale(2, RoundingMode.HALF_EVEN)));
    fieldValues.put(BB2RIFStructure.HHA.REV_CNTR_PMT_AMT_AMT,
            String.format("%.2f", lineItem.coinsurancePaidByPayer.add(lineItem.paidByPayer)));
    fieldValues.put(BB2RIFStructure.HHA.REV_CNTR_TOT_CHRG_AMT,
            String.format("%.2f", lineItem.cost));
    fieldValues.put(BB2RIFStructure.HHA.REV_CNTR_NCVRD_CHRG_AMT,
            String.format("%.2f", lineItem.copayPaidByPatient.add(lineItem.deductiblePaidByPatient)
                    .add(lineItem.patientOutOfPocket)));
    if (lineItem.patientOutOfPocket.compareTo(Claim.ZERO_CENTS) == 0
            && lineItem.deductiblePaidByPatient.compareTo(Claim.ZERO_CENTS) == 0) {
      // Not subject to deductible or coinsurance
      fieldValues.put(BB2RIFStructure.HHA.REV_CNTR_DDCTBL_COINSRNC_CD, "3");
    } else if (lineItem.patientOutOfPocket.compareTo(Claim.ZERO_CENTS) > 0
            && lineItem.deductiblePaidByPatient.compareTo(Claim.ZERO_CENTS) > 0) {
      // Subject to deductible and coinsurance
      fieldValues.put(BB2RIFStructure.HHA.REV_CNTR_DDCTBL_COINSRNC_CD, "0");
    } else if (lineItem.patientOutOfPocket.compareTo(Claim.ZERO_CENTS) == 0) {
      // Not subject to deductible
      fieldValues.put(BB2RIFStructure.HHA.REV_CNTR_DDCTBL_COINSRNC_CD, "1");
    } else {
      // Not subject to coinsurance
      fieldValues.put(BB2RIFStructure.HHA.REV_CNTR_DDCTBL_COINSRNC_CD, "2");
    }
  }

}
