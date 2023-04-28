package org.mitre.synthea.export.rif;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.StringUtils;

import org.mitre.synthea.export.ExportHelper;
import org.mitre.synthea.export.rif.identifiers.CLIA;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Clinician;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.Claim;
import org.mitre.synthea.world.concepts.ClinicianSpecialty;
import org.mitre.synthea.world.concepts.HealthRecord;

/**
 * Exporter for RIF Carrier File.
 */
public class CarrierExporter extends RIFExporter {

  private static final List<LinkedHashMap<String, String>> carrierLookup = getCarriers();
  public static final AtomicLong nextCarrClmCntlNum = new AtomicLong(Config.getAsLong(
          "exporter.bfd.carr_clm_cntl_num_start", -1));

  private static List<LinkedHashMap<String, String>> getCarriers() {
    String csv;
    try {
      csv = Utilities.readResourceAndStripBOM("payers/carriers.csv");
      return SimpleCSV.parse(csv);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  static String getCarrier(String state, BB2RIFStructure.CARRIER column) {
    for (LinkedHashMap<String, String> row : carrierLookup) {
      if (row.get("STATE").equals(state) || row.get("STATE_CODE").equals(state)) {
        return row.get(column.toString());
      }
    }
    return "0";
  }

  /**
   * Construct an exporter for Carrier claims.
   * @param exporter the exporter instance that will be used to access code mappers
   */
  public CarrierExporter(BB2RIFExporter exporter) {
    super(exporter);
  }

  /**
   * Export carrier claims details for a single person.
   * @param person the person to export
   * @param startTime earliest claim date to export
   * @param stopTime end time of simulation
   * @return number of claims exported
   * @throws IOException if something goes wrong
   */
  long export(Person person, long startTime, long stopTime) throws IOException {
    Boolean partBEnrollee = (Boolean)person.attributes.get(RIFExporter.BB2_PARTB_ENROLLEE);
    if (partBEnrollee.equals(false)) {
      // Skip carrier claims if beneficiary is not enrolled in part B
      return 0;
    }

    HashMap<BB2RIFStructure.CARRIER, String> fieldValues = new HashMap<>();

    long claimCount = 0;
    double latestHemoglobin = 0;

    for (HealthRecord.Encounter encounter : person.record.encounters) {
      if (encounter.stop < startTime || encounter.stop < CLAIM_CUTOFF) {
        continue;
      }
      if (!hasPartABCoverage(person, encounter.stop)) {
        continue;
      }
      if (!RIFExporter.getClaimTypes(encounter).contains(ClaimType.CARRIER)) {
        continue;
      }

      long claimId = RIFExporter.nextClaimId.getAndDecrement();
      long claimGroupId = RIFExporter.nextClaimGroupId.getAndDecrement();
      long carrClmId = nextCarrClmCntlNum.getAndDecrement();

      for (HealthRecord.Observation observation : encounter.observations) {
        if (observation.containsCode("718-7", "http://loinc.org")) {
          latestHemoglobin = (double) observation.value;
        }
      }

      exporter.staticFieldConfig.setValues(fieldValues, BB2RIFStructure.CARRIER.class, person);
      fieldValues.put(BB2RIFStructure.CARRIER.BENE_ID,
              (String) person.attributes.get(RIFExporter.BB2_BENE_ID));

      // The REQUIRED fields
      fieldValues.put(BB2RIFStructure.CARRIER.CLM_ID, "" + claimId);
      fieldValues.put(BB2RIFStructure.CARRIER.CLM_GRP_ID, "" + claimGroupId);
      fieldValues.put(BB2RIFStructure.CARRIER.CARR_CLM_CNTL_NUM, "" + carrClmId);
      fieldValues.put(BB2RIFStructure.CARRIER.CLM_FROM_DT,
              RIFExporter.bb2DateFromTimestamp(encounter.start));
      fieldValues.put(BB2RIFStructure.CARRIER.LINE_1ST_EXPNS_DT,
              RIFExporter.bb2DateFromTimestamp(encounter.start));
      fieldValues.put(BB2RIFStructure.CARRIER.CLM_THRU_DT,
              RIFExporter.bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(BB2RIFStructure.CARRIER.LINE_LAST_EXPNS_DT,
              RIFExporter.bb2DateFromTimestamp(encounter.stop));
      fieldValues.put(BB2RIFStructure.CARRIER.NCH_WKLY_PROC_DT,
              RIFExporter.bb2DateFromTimestamp(ExportHelper.nextFriday(encounter.stop)));
      fieldValues.put(BB2RIFStructure.CARRIER.CARR_NUM,
              getCarrier(encounter.provider.state, BB2RIFStructure.CARRIER.CARR_NUM));
      fieldValues.put(BB2RIFStructure.CARRIER.CLM_PMT_AMT,
              String.format("%.2f", encounter.claim.getTotalCoveredCost()));
      if (encounter.claim.coveredByMedicare()) {
        fieldValues.put(BB2RIFStructure.CARRIER.CARR_CLM_PRMRY_PYR_PD_AMT, "0");
      } else {
        fieldValues.put(BB2RIFStructure.CARRIER.CARR_CLM_PRMRY_PYR_PD_AMT,
                String.format("%.2f", encounter.claim.getTotalCoveredCost()));
      }
      // NCH_CLM_BENE_PMT_AMT, is always zero (set in field value spreadsheet)
      fieldValues.put(BB2RIFStructure.CARRIER.NCH_CLM_PRVDR_PMT_AMT,
              String.format("%.2f", encounter.claim.getTotalCoveredCost()));
      fieldValues.put(BB2RIFStructure.CARRIER.NCH_CARR_CLM_SBMTD_CHRG_AMT,
              String.format("%.2f", encounter.claim.getTotalClaimCost()));
      fieldValues.put(BB2RIFStructure.CARRIER.NCH_CARR_CLM_ALOWD_AMT,
              String.format("%.2f", encounter.claim.getTotalCoveredCost()));
      fieldValues.put(BB2RIFStructure.CARRIER.CARR_CLM_CASH_DDCTBL_APLD_AMT,
              String.format("%.2f", encounter.claim.getTotalDeductiblePaid()));
      fieldValues.put(BB2RIFStructure.CARRIER.CARR_CLM_RFRNG_PIN_NUM,
              StringUtils.truncate(encounter.provider.cmsPin, 14));
      fieldValues.put(BB2RIFStructure.CARRIER.CARR_PRFRNG_PIN_NUM,
              StringUtils.truncate(encounter.provider.cmsPin, 15));
      fieldValues.put(BB2RIFStructure.CARRIER.ORG_NPI_NUM, encounter.provider.npi);
      fieldValues.put(BB2RIFStructure.CARRIER.CARR_CLM_BLG_NPI_NUM, encounter.provider.npi);
      fieldValues.put(BB2RIFStructure.CARRIER.PRF_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(BB2RIFStructure.CARRIER.RFR_PHYSN_NPI, encounter.clinician.npi);
      fieldValues.put(BB2RIFStructure.CARRIER.PRVDR_SPCLTY,
          ClinicianSpecialty.getCMSProviderSpecialtyCode(
              (String) encounter.clinician.attributes.get(Clinician.SPECIALTY)));
      fieldValues.put(BB2RIFStructure.CARRIER.TAX_NUM,
              BeneficiaryExporter.bb2TaxId(
                      (String)encounter.clinician.attributes.get(Person.IDENTIFIER_SSN)));
      fieldValues.put(BB2RIFStructure.CARRIER.LINE_SRVC_CNT, "" + encounter.claim.items.size());
      fieldValues.put(BB2RIFStructure.CARRIER.CARR_LINE_PRCNG_LCLTY_CD,
              getCarrier(encounter.provider.state,
                      BB2RIFStructure.CARRIER.CARR_LINE_PRCNG_LCLTY_CD));
      // length of encounter in minutes
      fieldValues.put(BB2RIFStructure.CARRIER.CARR_LINE_MTUS_CNT,
              "" + ((encounter.stop - encounter.start) / (1000 * 60)));

      fieldValues.put(BB2RIFStructure.CARRIER.LINE_HCT_HGB_RSLT_NUM,
              "" + latestHemoglobin);
      fieldValues.put(BB2RIFStructure.CARRIER.LINE_PLACE_OF_SRVC_CD, getPlaceOfService(encounter));

      // OPTIONAL
      fieldValues.put(BB2RIFStructure.CARRIER.PRF_PHYSN_UPIN,
              StringUtils.truncate(encounter.provider.cmsUpin, 12));
      fieldValues.put(BB2RIFStructure.CARRIER.RFR_PHYSN_UPIN,
              StringUtils.truncate(encounter.provider.cmsUpin, 12));
      fieldValues.put(BB2RIFStructure.CARRIER.PRVDR_STATE_CD, encounter.provider.state);
      fieldValues.put(BB2RIFStructure.CARRIER.PRVDR_ZIP, encounter.provider.zip);

      String icdReasonCode = null;
      if (encounter.reason != null) {
        // If the encounter has a recorded reason, enter the mapped
        // values into the principle diagnoses code.
        if (exporter.conditionCodeMapper.canMap(encounter.reason)) {
          icdReasonCode = exporter.conditionCodeMapper.map(encounter.reason, person, true);
          fieldValues.put(BB2RIFStructure.CARRIER.PRNCPAL_DGNS_CD, icdReasonCode);
          fieldValues.put(BB2RIFStructure.CARRIER.LINE_ICD_DGNS_CD, icdReasonCode);
        }
      }

      // Use the active condition diagnoses to enter mapped values
      // into the diagnoses codes.
      List<String> mappedDiagnosisCodes = getDiagnosesCodes(person, encounter.stop);
      if (mappedDiagnosisCodes.isEmpty() && icdReasonCode == null) {
        continue; // skip this encounter
      }
      int smallest = Math.min(mappedDiagnosisCodes.size(),
              BB2RIFStructure.carrierDxFields.length);
      for (int i = 0; i < smallest; i++) {
        BB2RIFStructure.CARRIER[] dxField = BB2RIFStructure.carrierDxFields[i];
        fieldValues.put(dxField[0], mappedDiagnosisCodes.get(i));
        fieldValues.put(dxField[1], "0"); // 0=ICD10
      }
      if (!fieldValues.containsKey(BB2RIFStructure.CARRIER.PRNCPAL_DGNS_CD)) {
        fieldValues.put(BB2RIFStructure.CARRIER.PRNCPAL_DGNS_CD, mappedDiagnosisCodes.get(0));
      }

      synchronized (exporter.rifWriters.getOrCreateWriter(BB2RIFStructure.CARRIER.class)) {
        int lineNum = 1;
        CLIA cliaLab = RIFExporter.cliaLabNumbers[
                person.randInt(RIFExporter.cliaLabNumbers.length)];
        List<Claim.ClaimEntry> allItems = new ArrayList<>();
        allItems.add(encounter.claim.mainEntry);
        allItems.addAll(encounter.claim.items);
        for (Claim.ClaimEntry lineItem : allItems) {
          String hcpcsCode = "";
          String ndcCode = "";
          if (lineItem.entry instanceof HealthRecord.Procedure) {
            for (HealthRecord.Code code : lineItem.entry.codes) {
              if (exporter.hcpcsCodeMapper.canMap(code)) {
                hcpcsCode = exporter.hcpcsCodeMapper.map(code, person, true);
                break; // take the first mappable code for each procedure
              }
            }
          } else if (lineItem.entry instanceof HealthRecord.Medication) {
            HealthRecord.Medication med = (HealthRecord.Medication) lineItem.entry;
            if (med.administration) {
              hcpcsCode = "T1502";  // Administration of medication
              ndcCode = exporter.medicationCodeMapper.map(med.codes.get(0), person);
            }
          }
          if (icdReasonCode == null) {
            // If there is an icdReasonCode, then then LINE_ICD_DGNS_CD is already set.
            // If not, we might choose a value for each line item.
            double probability = person.rand();
            if (probability <= 0.06) {
              // Random code
              int index = person.randInt(mappedDiagnosisCodes.size());
              String code = mappedDiagnosisCodes.get(index);
              fieldValues.put(BB2RIFStructure.CARRIER.LINE_ICD_DGNS_CD, code);
            } else if (probability <= 0.48) {
              // The principal diagnosis code
              fieldValues.put(BB2RIFStructure.CARRIER.LINE_ICD_DGNS_CD,
                      fieldValues.get(BB2RIFStructure.CARRIER.PRNCPAL_DGNS_CD));
            } else {
              // No line item diagnosis code
              fieldValues.remove(BB2RIFStructure.CARRIER.LINE_ICD_DGNS_CD);
            }
          }
          // TBD: decide whether line item skip logic is needed here and in other files
          // TBD: affects ~80% of carrier claim lines, so left out for now
          // if (hcpcsCode == null) {
          //   continue; // skip this line item
          // }
          fieldValues.put(BB2RIFStructure.CARRIER.HCPCS_CD, hcpcsCode);
          if (exporter.betosCodeMapper.canMap(hcpcsCode)) {
            fieldValues.put(BB2RIFStructure.CARRIER.BETOS_CD,
                    exporter.betosCodeMapper.map(hcpcsCode, person));
          } else {
            fieldValues.put(BB2RIFStructure.CARRIER.BETOS_CD, "");
          }
          fieldValues.put(BB2RIFStructure.CARRIER.LINE_NDC_CD, ndcCode);
          fieldValues.put(BB2RIFStructure.CARRIER.LINE_BENE_PTB_DDCTBL_AMT,
                  String.format("%.2f", lineItem.deductiblePaidByPatient));
          fieldValues.put(BB2RIFStructure.CARRIER.LINE_COINSRNC_AMT,
                  String.format("%.2f", lineItem.coinsurancePaidByPayer));

          // Like NCH_CLM_BENE_PMT_AMT, LINE_BENE_PMT_AMT is always zero
          // (set in field value spreadsheet)
          BigDecimal providerAmount = lineItem.coinsurancePaidByPayer.add(lineItem.paidByPayer);
          fieldValues.put(BB2RIFStructure.CARRIER.LINE_PRVDR_PMT_AMT,
              String.format("%.2f", providerAmount));
          fieldValues.put(BB2RIFStructure.CARRIER.LINE_NCH_PMT_AMT,
              String.format("%.2f", providerAmount));
          fieldValues.put(BB2RIFStructure.CARRIER.LINE_SBMTD_CHRG_AMT,
              String.format("%.2f", lineItem.cost));
          fieldValues.put(BB2RIFStructure.CARRIER.LINE_ALOWD_CHRG_AMT,
              String.format("%.2f", lineItem.cost.subtract(lineItem.adjustment)));

          // If this item is a lab report, add the number of the clinical lab...
          if  (lineItem.entry instanceof HealthRecord.Report) {
            if (encounter.provider.cliaNumber != null) {
              fieldValues.put(BB2RIFStructure.CARRIER.CARR_LINE_CLIA_LAB_NUM,
                      encounter.provider.cliaNumber);
            } else {
              fieldValues.put(BB2RIFStructure.CARRIER.CARR_LINE_CLIA_LAB_NUM, cliaLab.toString());
            }
          }

          // set the line number and write out field values
          fieldValues.put(BB2RIFStructure.CARRIER.LINE_NUM, Integer.toString(lineNum++));
          exporter.rifWriters.writeValues(BB2RIFStructure.CARRIER.class, fieldValues);
        }

        if (lineNum == 1) {
          // If lineNum still equals 1, then no line items were successfully added.
          // Add a single top-level entry.
          fieldValues.put(BB2RIFStructure.CARRIER.LINE_NUM, Integer.toString(lineNum));
          fieldValues.put(BB2RIFStructure.CARRIER.LINE_BENE_PTB_DDCTBL_AMT,
                  String.format("%.2f", encounter.claim.getTotalDeductiblePaid()));
          fieldValues.put(BB2RIFStructure.CARRIER.LINE_COINSRNC_AMT,
                  String.format("%.2f", encounter.claim.getTotalCoinsurancePaid()));
          fieldValues.put(BB2RIFStructure.CARRIER.LINE_SBMTD_CHRG_AMT,
                  String.format("%.2f", encounter.claim.getTotalClaimCost()));
          fieldValues.put(BB2RIFStructure.CARRIER.LINE_ALOWD_CHRG_AMT,
                  String.format("%.2f", encounter.claim.getTotalCoveredCost()));
          // Like NCH_CLM_BENE_PMT_AMT, LINE_BENE_PMT_AMT is always zero
          // (set in field value spreadsheet)
          fieldValues.put(BB2RIFStructure.CARRIER.LINE_PRVDR_PMT_AMT,
                  String.format("%.2f", encounter.claim.getTotalCoveredCost()));
          fieldValues.put(BB2RIFStructure.CARRIER.LINE_NCH_PMT_AMT,
                  String.format("%.2f", encounter.claim.getTotalCoveredCost()));
          // 99241: "Office consultation for a new or established patient"
          fieldValues.put(BB2RIFStructure.CARRIER.HCPCS_CD, "99241");
          exporter.rifWriters.writeValues(BB2RIFStructure.CARRIER.class, fieldValues);
        }
      }
      claimCount++;
    }
    return claimCount;
  }
}
