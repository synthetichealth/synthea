package org.mitre.synthea.export;

import static org.mitre.synthea.export.ExportHelper.nextFriday;

import com.google.common.io.Resources;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import org.mitre.synthea.export.BB2RIFExporter.StateCodeMapper;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.helpers.SimpleCSV;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;


/** Export configuration class for BFD.
 *  See comments for BB2RIFExporter class for usage information.
 */
public class BFDExportBuilder {

  enum ExportConfigType {
    BENEFICIARY,
    BENEFICIARY_HISTORY,
    CARRIER,
    DME,
    INPATIENT,
    // HHA,
    // HOSPICE,
    // MEDICARE_BENEFICIARY_ID,
    OUTPATIENT,
    // PDE,
    PRESCRIPTION,
    // SNF,
  }

  private static final String BB2_BENE_ID = "BB2_BENE_ID";
  private static final String BB2_HIC_ID = "BB2_HIC_ID";
  
  /**
   * Day-Month-Year date format.
   */
  private static final SimpleDateFormat BB2_DATE_FORMAT = new SimpleDateFormat("dd-MMM-yyyy");

  /**
   * Get a date string in the format DD-MMM-YY from the given time stamp.
   */
  private static String bb2DateFromTimestamp(long time) {
    synchronized (BB2_DATE_FORMAT) {
      // http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6231579
      return BB2_DATE_FORMAT.format(new Date(time));
    }
  }
  
  // specifies if we are testing, 
  //  this causes some configs to be consistent from run to run (e.g., distributions)
  private boolean testing = false;  

  private URL configUrl;

  private StateCodeMapper locationMapper = null;

  private List<LinkedHashMap<String, String>> carrierLookup;

  private List<BFDExportConfigEntry> allConfigs = null;
  
  private List<BFDExportConfigEntry> beneficiaryConfigs = new ArrayList<BFDExportConfigEntry>();
  private List<BFDExportConfigEntry> beneficiaryHistoryConfigs 
                                                        = new ArrayList<BFDExportConfigEntry>();
  private List<BFDExportConfigEntry> carrierConfigs = new ArrayList<BFDExportConfigEntry>();
  private List<BFDExportConfigEntry> dmeConfigs = new ArrayList<BFDExportConfigEntry>();
  private List<BFDExportConfigEntry> inpatientConfigs = new ArrayList<BFDExportConfigEntry>();
  private List<BFDExportConfigEntry> outpatientConfigs = new ArrayList<BFDExportConfigEntry>();
  private List<BFDExportConfigEntry> prescriptionConfigs = new ArrayList<BFDExportConfigEntry>();
  
  /** Constructor. */
  public BFDExportBuilder(StateCodeMapper locationMapper) {
    String filepath = Config.get("exporter.bfd.default_config_file");
    this.locationMapper = locationMapper;
    this.configUrl = Resources.getResource(filepath);
    this.initConfigs();
  }


  /** lookup carrier information
   *  This used to be BB2RIFExporter.getCarrier() 
   *    and was used for Carrier only, for CARR_NUM and CARR_LINE_PRCNG_LCLTY_CD
   *    It has been generalized here for DME, and to keep code DRY
   */
  String lookupCarrier(String state, ExportConfigType type, String column) {
    String retval = "0";
    for (LinkedHashMap<String, String> row : carrierLookup) {
      if (row.get("STATE").equals(state) || row.get("STATE_CODE").equals(state)) {
        switch (type) {
          case CARRIER:
          case DME:
          default:
            retval = row.get(column);
        }
      }
    }
    return retval;
  }
  

  /** Determines if an expression in the TSV file should be added or ignored.
   *  @param expression the expression to evaluate
   *  @return true iff this expression is can be evaluated, expanded and is useful
   */
  private boolean shouldAdd(String expression, BFDExportConfigEntry entry, ExportConfigType type) {
    boolean retval = false;
    expression = expression.trim();
    if (!expression.isEmpty()) {
      // values we can work with
      if (expression.equalsIgnoreCase("NULL") || expression.equalsIgnoreCase("Coded")) {
        retval = false;
      } else if (expression.startsWith("(")) { // comment only 
        System.out.printf("  config spreadsheet needs further work (line %3d | %-19s : %s : %s\n",
              entry.getLineNum(), type, entry.getField(), expression);
        retval = false;
      } else if (expression.startsWith("[")) { // either a function or developer note
        retval = true;
      } else {
        retval = true;
      }
    } else {
      // System.out.println("rejecting because expression="+expression);
      retval = false;
    }
    return retval;
  }

  private String evalConfig(
        String expression, 
        BFDExportConfigEntry entry, 
        ExportConfigType type, 
        HealthRecord.Encounter encounter,
        HealthRecord.Device device,
        Person person) {
    String retval = expression;
    // look for comments (matching anything in parenthesis)
    int commentStart = expression.indexOf("(");
    if (commentStart >= 0) {
      retval = expression.substring(0, commentStart - 1); // peel off comment
      // currently we just ignore comments 
      //  since they are for the analyst doing the config spreadsheet
      // comment = expression.substring(commentStart + 1, expression.length()-1);
    }
    boolean printError = false;

    // evaluate for functions
    if (expression.startsWith("[")) {
      switch (expression) {
        case "[Blank]":
        case "[blank]":
          retval = "";
          break;
        case "[bene_id]":
          retval = (String) person.attributes.get(BB2_BENE_ID);
          break;
        case "[bene_race]":
          retval = BB2RIFExporter.bb2RaceCode(
            (String)person.attributes.get(Person.ETHNICITY),
            (String)person.attributes.get(Person.RACE));
          break;
        case "[bene_stateCode]":
          if (locationMapper != null) {
            retval = locationMapper.getStateCode((String)person.attributes.get(Person.STATE));
          } else {
            retval = "?";
          }
          break;
        case "[bene_terminationCode]":
          retval = (person.attributes.get(Person.DEATHDATE) == null) ? "0" : "1";
          break;
        case "[carr_num_from_state]":
          retval = lookupCarrier(encounter.provider.state, type, entry.getField());
          break;
        case "[device_startTimestamp]":
          retval = bb2DateFromTimestamp(device.start);
          break;
        // case "[device_stopTimestamp]":
        //   retval = bb2DateFromTimestamp(device.stop);
        //   break;
        case "[encounter_claimCoinsurancePaid]":
          retval = String.format("%.2f", encounter.claim.getCoinsurancePaid());
          break;
        case "[encounter_claimDeductiblePaid]":
          retval = String.format("%.2f", encounter.claim.getDeductiblePaid());
          break;
        case "[encounter_claimDeductible+Coinsurance]":
          retval = String.format("%.2f", 
                encounter.claim.getDeductiblePaid() + encounter.claim.getCoinsurancePaid());
          break;
        case "[encounter_claimTotalCost]":
          retval = String.format("%.2f", encounter.claim.getTotalClaimCost());
          break;
        case "[encounter_clinician_ssn]":
          retval = "" + encounter.clinician.attributes.get(Person.IDENTIFIER_SSN);
          break;
        case "[encounter_providerID]":
          retval = encounter.provider.id;
          break;
        case "[encounter_providerStateCD]":
          if (locationMapper != null) {
            retval = locationMapper.getStateCode(encounter.provider.state);
          } else {
            retval = "?";
          }
          break;
        case "[encounter_startTimestamp]":
          retval = bb2DateFromTimestamp(encounter.start);
          break;
        case "[encounter_stopTimestamp]":
          retval = bb2DateFromTimestamp(encounter.stop);
          break;
        case "[encounter_stopTimestamp_nextFriday]":
          retval = bb2DateFromTimestamp(nextFriday(encounter.stop));
          break;
        default:  // does not know specified function, 
          // might be a comment or it needs to be implemented
          printError = true;
          retval = "?";
          break;
      }
    } else if (expression.startsWith("fieldValues.put")) {  // same as [:...]
      printError = true;
      retval = "?";
    }

    if (printError) {
      System.out.printf(
            "  output config error: unknown function in TSV line %3d for %s:  %s : %s\n",
            entry.getLineNum(), type, entry.getField(), expression);
    }
    return retval;
  }

  /** Choose a value in a distribution string.
   *  Note that this is safe to always use since it will check 
   *    to see if the the expression is a distribution string first
   *  @param expression the string that potentially represents a distribution string
   *  @param useFirst boolean to always use the first value in the distribution string;
   *                  useful for testing
  */
  private String evalConfigDistribution(String expression, boolean useFirst,
          RandomNumberGenerator rand) {
    // must be done after expression has removed things like comments and functions
    String retval = expression;
    if (expression.contains(",")) {
      List<String> values = Arrays.asList(retval.split(","));
      if (useFirst) {
        retval = values.get(0);
      } else {
        // System.out.println("flat distribution:"+expression);
        int index = rand.randInt(values.size());
        retval = values.get(index);
      }
    }
    return retval;
  }


  /** Initialize object from configuration file. */
  private List<BFDExportConfigEntry> initConfigs() {
    // read in carrier config
    try {
      String csv = Utilities.readResource("payers/carriers.csv");
      if (csv.startsWith("\uFEFF")) {
        csv = csv.substring(1); // Removes BOM.
      }
      carrierLookup = SimpleCSV.parse(csv);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    // read in BFD config
    try {
      System.out.printf("Reading using spec in synthea properties: %s\n",
            this.configUrl.toString());
      Reader reader = new BufferedReader(new InputStreamReader(this.configUrl.openStream()));
      CsvToBean<BFDExportConfigEntry> csvReader = new CsvToBeanBuilder<BFDExportConfigEntry>(reader)
            .withType(BFDExportConfigEntry.class)
            .withSeparator('\t')
            .withIgnoreLeadingWhiteSpace(true)
            .withIgnoreEmptyLine(true)
            .build();
      this.allConfigs = csvReader.parse();

      for (BFDExportConfigEntry prop: this.getAllConfigs()) {
        if (shouldAdd(prop.getBeneficiary(), prop, ExportConfigType.BENEFICIARY)) {
          this.beneficiaryConfigs.add(prop);
        }
        if (shouldAdd(prop.getbeneficiaryHistory(), prop, ExportConfigType.BENEFICIARY_HISTORY)) {
          this.beneficiaryHistoryConfigs.add(prop);
        }
        if (shouldAdd(prop.getCarrier(), prop, ExportConfigType.CARRIER)) {
          this.carrierConfigs.add(prop);
        }
        if (shouldAdd(prop.getDme(), prop, ExportConfigType.DME)) {
          this.dmeConfigs.add(prop);
        }
        if (shouldAdd(prop.getInpatient(), prop, ExportConfigType.INPATIENT)) {
          this.inpatientConfigs.add(prop);
        }
        if (shouldAdd(prop.getOutpatient(), prop, ExportConfigType.OUTPATIENT)) {
          this.outpatientConfigs.add(prop);
        }
        if (shouldAdd(prop.getPrescription(), prop, ExportConfigType.PRESCRIPTION)) {
          this.prescriptionConfigs.add(prop);
        }
      } 
      return this.allConfigs;
    } catch (IOException ex) {
      System.out.printf("Error reading %s:\n%s\n\n", this.configUrl.toString(), ex);
      return null;
    }
  }


  /** Returns the expression in prop corresponding to the type. */
  private String  getExpressionByType(BFDExportConfigEntry prop, ExportConfigType type) 
        throws Exception {
    String cell = "";
    switch (type) {
      case BENEFICIARY:
        cell = prop.getBeneficiary();
        break;
      case BENEFICIARY_HISTORY:
        cell = prop.getbeneficiaryHistory();
        break;
      case CARRIER:
        cell = prop.getCarrier();
        break;
      case DME:
        cell = prop.getDme();
        break;
      case INPATIENT:
        cell = prop.getInpatient();
        break;
      // case HHA:
      //   break;
      // case HOSPICE:
      //   break;
      // case MEDICARE_BENEFICIARY_ID:
      //   break;
      case OUTPATIENT:
        cell = prop.getOutpatient();
        break;
      // case PDE:
      //   break;
      case PRESCRIPTION:
        cell = prop.getPrescription();
        break;
      // case SNF:
      //   break;
      default:
        throw new Exception("No expression found for " + type + " in row " + prop.getField());
    }
    return cell;
  }

  @SuppressWarnings("rawtypes")
  private Enum getEnumValueByType(BFDExportConfigEntry prop, ExportConfigType type) 
        throws Exception {
    Enum fieldEnum;
    switch (type) {
      case BENEFICIARY:
        fieldEnum = BB2RIFExporter.BeneficiaryFields.valueOf(prop.getField());
        break;
      case BENEFICIARY_HISTORY:
        fieldEnum = BB2RIFExporter.BeneficiaryHistoryFields.valueOf(prop.getField());
        break;
      case CARRIER:
        fieldEnum = BB2RIFExporter.CarrierFields.valueOf(prop.getField());
        break;
      case DME:
        fieldEnum = BB2RIFExporter.DMEFields.valueOf(prop.getField());
        break;
      case INPATIENT:
        fieldEnum = BB2RIFExporter.InpatientFields.valueOf(prop.getField());
        break;
      // case HHA:
      //   break;
      // case HOSPICE:
      //   break;
      // case MEDICARE_BENEFICIARY_ID:
      //   break;
      case OUTPATIENT:
        fieldEnum = BB2RIFExporter.OutpatientFields.valueOf(prop.getField());
        break;
      // case PDE:
      //   break;
      case PRESCRIPTION:
        fieldEnum = BB2RIFExporter.PrescriptionFields.valueOf(prop.getField());
        break;
      // case SNF:
      //   break;
      default:
        throw new Exception("Unknown field '" + prop.getField() + "' specfied for " + type);
    }
    return fieldEnum;
  }


  /** Sets the known field values based on exporter config TSV file.
   * @param type output type (one of the ExportConfigType types)
   * @param fieldValues reference to a HashMap of field values;
   *                    this is instantiated in each of the exportXXXXX() functions
   * @param person the patient
   * @return the updated field values
   */
  @SuppressWarnings("rawtypes")
  public HashMap setFromConfig(ExportConfigType type, HashMap fieldValues, Person person) {
    return setFromConfig(type, fieldValues, null, null, person);
  }

  /** Sets the known field values based on exporter config TSV file.
   * @param type output type (one of the ExportConfigType types)
   * @param fieldValues reference to a HashMap of field values;
   *                    this is instantiated in each of the exportXXXXX() functions
   * @param encounter the specific encounter, if any
   * @param person the patient
   * @return the updated field values
   */
  @SuppressWarnings("rawtypes")
  public HashMap setFromConfig(ExportConfigType type, 
                                HashMap fieldValues, 
                                HealthRecord.Encounter encounter,
                                Person person) {
    return setFromConfig(type, fieldValues, encounter, null, person);
  }
  

  /** Sets the known field values based on exporter config TSV file.
   * @param type output type (one of the ExportConfigType types)
   * @param fieldValues reference to a HashMap of field values;
   *                    this is instantiated in each of the exportXXXXX() functions
   * @param encounter the specific encounter, if any
   * @param person the patient
   * @return the updated field values
   */
  @SuppressWarnings("rawtypes")
  public HashMap setFromConfig(ExportConfigType type, 
                                HashMap fieldValues, 
                                HealthRecord.Encounter encounter,
                                HealthRecord.Device device,
                                Person person) {
    fieldValues.clear();
    List<BFDExportConfigEntry> configs = this.getConfigItemsByType(type);
    try {
      int processedCount = 0;
      for (BFDExportConfigEntry prop: configs) {
        String cell = getExpressionByType(prop, type);
        // System.out.println("*****"+cell);
        if (!cell.isEmpty()) {
          String value = evalConfig(cell, prop, type, encounter, device, person);
          value = evalConfigDistribution(value, this.testing, person);
          if (!value.startsWith("?")) {
            processedCount++;
          }
          Enum fieldEnum = getEnumValueByType(prop, type);
          fieldValues.put(fieldEnum, value);
        }
      }
      System.out.printf("  config processed/total for %s:  %d/%d\n", 
            type, processedCount, configs.size());
    } catch (Exception ex) {
      System.out.println("ExportDataBuilder.setFromConfig ERROR while processing :  " + ex);
    }
    return fieldValues;
  }

  /** Validates to make sure that everything in the config is set in the exporter.
   *  This includes those with `Coded`, to verify that it indeed is set */
  // public boolean validateUsingConfig(ExportConfigType type, HashMap fieldValues) {
  //   boolean retval = true;
  //   return retval;
  // }

  /** returns all the config items. */
  public List<BFDExportConfigEntry> getAllConfigs() {
    return this.allConfigs;
  }


  /** Returns all inpatient config items. */
  public List<BFDExportConfigEntry> getConfigItemsByType(ExportConfigType type) {
    switch (type) {
      case BENEFICIARY:
        return this.beneficiaryConfigs;
      case BENEFICIARY_HISTORY:
        return this.beneficiaryHistoryConfigs;
      case CARRIER:
        return this.carrierConfigs;
      case DME: 
        return this.dmeConfigs;
      case INPATIENT: 
        return this.inpatientConfigs;
      case OUTPATIENT: 
        return this.outpatientConfigs;
      case PRESCRIPTION: 
        return this.prescriptionConfigs;
      default: return null;
    }
  }
}

