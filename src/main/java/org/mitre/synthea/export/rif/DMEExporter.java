package org.mitre.synthea.export.rif;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.mitre.synthea.export.ExportHelper;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.ClinicianSpecialty;
import org.mitre.synthea.world.concepts.HealthRecord;

/**
 * Exporter for RIF DME (durable medical equipment) file.
 */
public class DMEExporter extends RIFExporter {

  /**
   * Construct an exporter for DME claims.
   * @param exporter the exporter instance that will be used to access code mappers
   */
  public DMEExporter(BB2RIFExporter exporter) {
    super(exporter);
  }

  /**
   * Export DME details for a single person.
   * @param person the person to export
   * @param startTime earliest claim date to export
   * @param stopTime end time of simulation
   * @return count of claims exported
   * @throws IOException if something goes wrong
   */
  long export(Person person, long startTime, long stopTime) throws IOException {
    long claimCount = 0;
    HashMap<BB2RIFStructure.DME, String> fieldValues = new HashMap<>();

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

      long claimId = RIFExporter.nextClaimId.getAndDecrement();
      long claimGroupId = RIFExporter.nextClaimGroupId.getAndDecrement();
      long carrClmId = CarrierExporter.nextCarrClmCntlNum.getAndDecrement();

      double latestHemoglobin = 0;
      for (HealthRecord.Observation observation : encounter.observations) {
        if (observation.containsCode("718-7", "http://loinc.org")) {
          latestHemoglobin = (double) observation.value;
        }
      }

      fieldValues.clear();
      exporter.staticFieldConfig.setValues(fieldValues, BB2RIFStructure.DME.class, person);

      // complex fields that could not easily be set using cms_field_values.tsv
      fieldValues.put(BB2RIFStructure.DME.CLM_ID, "" + claimId);
      fieldValues.put(BB2RIFStructure.DME.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(BB2RIFStructure.DME.CARR_CLM_CNTL_NUM, "" + carrClmId);
      fieldValues.put(BB2RIFStructure.DME.BENE_ID,
              (String)person.attributes.get(RIFExporter.BB2_BENE_ID));
      fieldValues.put(BB2RIFStructure.DME.LINE_HCT_HGB_RSLT_NUM, "" + latestHemoglobin);
      fieldValues.put(BB2RIFStructure.DME.CARR_NUM,
              CarrierExporter.getCarrier(encounter.provider.state,
                      BB2RIFStructure.CARRIER.CARR_NUM));
      fieldValues.put(BB2RIFStructure.DME.NCH_WKLY_PROC_DT,
              RIFExporter.bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop)));
      fieldValues.put(BB2RIFStructure.DME.PRVDR_NUM,
              StringUtils.truncate(encounter.provider.cmsProviderNum, 10));
      fieldValues.put(BB2RIFStructure.DME.PRVDR_NPI, encounter.provider.npi);
      fieldValues.put(BB2RIFStructure.DME.RFR_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(BB2RIFStructure.DME.RFR_PHYSN_UPIN,
              StringUtils.truncate(encounter.provider.cmsUpin, 12));
      fieldValues.put(BB2RIFStructure.DME.PRVDR_SPCLTY,
          ClinicianSpecialty.getCMSProviderSpecialtyCode(
              (String) encounter.clinician.attributes.get(Clinician.SPECIALTY)));
      fieldValues.put(BB2RIFStructure.DME.PRVDR_STATE_CD,
              exporter.locationMapper.getStateCode(encounter.provider.state));
      fieldValues.put(BB2RIFStructure.DME.TAX_NUM,
              BeneficiaryExporter.bb2TaxId(
                      (String)encounter.clinician.attributes.get(Person.IDENTIFIER_SSN)));
      fieldValues.put(BB2RIFStructure.DME.DMERC_LINE_PRCNG_STATE_CD,
              exporter.locationMapper.getStateCode((String)person.attributes.get(Person.STATE)));
      fieldValues.put(BB2RIFStructure.DME.LINE_1ST_EXPNS_DT,
              RIFExporter.bb2DateFromTimestamp(encounter.start));
      fieldValues.put(BB2RIFStructure.DME.LINE_LAST_EXPNS_DT,
              RIFExporter.bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(BB2RIFStructure.DME.LINE_SRVC_CNT, "" + encounter.claim.items.size());
      fieldValues.put(BB2RIFStructure.DME.LINE_PLACE_OF_SRVC_CD, getPlaceOfService(encounter));

      // OPTIONAL
      if (encounter.reason != null) {
        // If the encounter has a recorded reason, enter the mapped
        // values into the principle diagnoses code.
        if (exporter.conditionCodeMapper.canMap(encounter.reason)) {
          String icdCode = exporter.conditionCodeMapper.map(encounter.reason, person, true);
          fieldValues.put(BB2RIFStructure.DME.PRNCPAL_DGNS_CD, icdCode);
          fieldValues.put(BB2RIFStructure.DME.LINE_ICD_DGNS_CD, icdCode);
        }
      }

      // Use the active condition diagnoses to enter mapped values
      // into the diagnoses codes.
      List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, encounter.stop);
      if (mappedDiagnosisCodes.isEmpty()) {
        continue; // skip this encounter
      }
      int smallest = Math.min(mappedDiagnosisCodes.size(), BB2RIFStructure.dmeDxFields.length);
      for (int i = 0; i < smallest; i++) {
        BB2RIFStructure.DME[] dxField = BB2RIFStructure.dmeDxFields[i];
        fieldValues.put(dxField[0], mappedDiagnosisCodes.get(i));
        fieldValues.put(dxField[1], "0"); // 0=ICD10
      }
      if (!fieldValues.containsKey(BB2RIFStructure.DME.PRNCPAL_DGNS_CD)) {
        fieldValues.put(BB2RIFStructure.DME.PRNCPAL_DGNS_CD, mappedDiagnosisCodes.get(0));
        fieldValues.put(BB2RIFStructure.DME.LINE_ICD_DGNS_CD, mappedDiagnosisCodes.get(0));
      }

      // preprocess some subtotals...
      Claim.ClaimEntry subTotals = (encounter.claim).new ClaimEntry(null);
      for (Claim.ClaimEntry lineItem : encounter.claim.items) {
        if (lineItem.entry instanceof HealthRecord.Device
                || lineItem.entry instanceof HealthRecord.Supply) {
          subTotals.addCosts(lineItem);
        }
      }
      fieldValues.put(BB2RIFStructure.DME.CARR_CLM_CASH_DDCTBL_APLD_AMT,
          String.format("%.2f", subTotals.getDeductiblePaid()));
      fieldValues.put(BB2RIFStructure.DME.NCH_CARR_CLM_SBMTD_CHRG_AMT,
          String.format("%.2f", subTotals.getTotalClaimCost()));
      BigDecimal paidAmount = subTotals.getCoveredCost();
      fieldValues.put(BB2RIFStructure.DME.CARR_CLM_PRMRY_PYR_PD_AMT,
          String.format("%.2f", paidAmount));
      fieldValues.put(BB2RIFStructure.DME.NCH_CARR_CLM_ALOWD_AMT,
          String.format("%.2f", paidAmount));
      fieldValues.put(BB2RIFStructure.DME.NCH_CLM_PRVDR_PMT_AMT,
          String.format("%.2f", paidAmount));
      fieldValues.put(BB2RIFStructure.DME.CLM_PMT_AMT,
          String.format("%.2f", paidAmount));

      synchronized (exporter.rifWriters.getOrCreateWriter(BB2RIFStructure.DME.class)) {
        int lineNum = 1;
        boolean wroteAtLeastOneLine = false;
        // Now generate the line items...
        for (Claim.ClaimEntry lineItem : encounter.claim.items) {
          if (!(lineItem.entry instanceof HealthRecord.Device
                  || lineItem.entry instanceof HealthRecord.Supply)) {
            continue;
          }
          if (lineItem.entry instanceof HealthRecord.Supply) {
            HealthRecord.Supply supply = (HealthRecord.Supply) lineItem.entry;
            fieldValues.put(BB2RIFStructure.DME.DMERC_LINE_MTUS_CNT, "" + supply.quantity);
          } else {
            fieldValues.put(BB2RIFStructure.DME.DMERC_LINE_MTUS_CNT, "");
          }
          if (!exporter.dmeCodeMapper.canMap(lineItem.entry.codes.get(0))) {
            continue;
          }
          fieldValues.put(BB2RIFStructure.DME.CLM_FROM_DT,
                  RIFExporter.bb2DateFromTimestamp(lineItem.entry.start));
          fieldValues.put(BB2RIFStructure.DME.CLM_THRU_DT,
                  RIFExporter.bb2DateFromTimestamp(lineItem.entry.start));
          String hcpcsCode = exporter.dmeCodeMapper.map(lineItem.entry.codes.get(0), person);
          fieldValues.put(BB2RIFStructure.DME.HCPCS_CD, hcpcsCode);
          if (exporter.betosCodeMapper.canMap(hcpcsCode)) {
            fieldValues.put(BB2RIFStructure.DME.BETOS_CD,
                    exporter.betosCodeMapper.map(hcpcsCode, person));
          } else {
            fieldValues.put(BB2RIFStructure.DME.BETOS_CD, "");
          }
          fieldValues.put(BB2RIFStructure.DME.LINE_CMS_TYPE_SRVC_CD,
                  exporter.dmeCodeMapper.map(lineItem.entry.codes.get(0),
                          BB2RIFStructure.DME.LINE_CMS_TYPE_SRVC_CD.toString().toLowerCase(),
                          person));
          fieldValues.put(BB2RIFStructure.DME.LINE_BENE_PTB_DDCTBL_AMT,
                  String.format("%.2f", lineItem.deductiblePaidByPatient));
          fieldValues.put(BB2RIFStructure.DME.LINE_COINSRNC_AMT,
                  String.format("%.2f", lineItem.getCoinsurancePaid()));
          // LINE_BENE_PMT_AMT and NCH_CLM_BENE_PMT_AMT are always 0, set in field value spreadsheet
          BigDecimal providerAmount = lineItem.getCoveredCost();
          fieldValues.put(BB2RIFStructure.DME.LINE_PRVDR_PMT_AMT,
              String.format("%.2f", providerAmount));
          fieldValues.put(BB2RIFStructure.DME.LINE_NCH_PMT_AMT,
              String.format("%.2f", providerAmount));
          fieldValues.put(BB2RIFStructure.DME.LINE_SBMTD_CHRG_AMT,
              String.format("%.2f", lineItem.getTotalClaimCost()));
          BigDecimal allowedAmount = lineItem.getTotalClaimCost().subtract(lineItem.adjustment);
          fieldValues.put(BB2RIFStructure.DME.LINE_ALOWD_CHRG_AMT,
              String.format("%.2f", allowedAmount));
          fieldValues.put(BB2RIFStructure.DME.LINE_PRMRY_ALOWD_CHRG_AMT,
              String.format("%.2f", allowedAmount));

          // set the line number and write out field values
          fieldValues.put(BB2RIFStructure.DME.LINE_NUM, Integer.toString(lineNum++));
          exporter.rifWriters.writeValues(BB2RIFStructure.DME.class, fieldValues);
          wroteAtLeastOneLine = true;
        }
        if (wroteAtLeastOneLine) {
          claimCount++;
        }
      }
    }
    return claimCount;
  }
}
