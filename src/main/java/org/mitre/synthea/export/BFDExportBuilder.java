package org.mitre.synthea.export;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Function;


import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;

/**
 * Export configuration class for BFD
 */
public class BFDExportBuilder {

  enum ExportConfigType {
    BENEFICIARY,
    BENEFICIARY_HISTORY,
    CARRIER,
    // DME,
    INPATIENT,
    // HHA,
    // HOSPICE,
    // MEDICARE_BENEFICIARY_ID,
    OUTPATIENT,
    // PDE,
    PRESCRIPTION,
    // SNF,
  }
  

  private File configFile;
  private List<BFDExportConfigEntry> allConfigs = null;
  
  private List<BFDExportConfigEntry> beneficiaryConfigs = new ArrayList<BFDExportConfigEntry>();
  private List<BFDExportConfigEntry> beneficiaryHistoryConfigs = new ArrayList<BFDExportConfigEntry>();
  private List<BFDExportConfigEntry> carrierConfigs = new ArrayList<BFDExportConfigEntry>();
  // private List<BFDExportConfigEntry> dmeConfigs = new ArrayList<BFDExportConfigEntry>();
  private List<BFDExportConfigEntry> inpatientConfigs = new ArrayList<BFDExportConfigEntry>();
  // private List<BFDExportConfigEntry> hhaConfigs = new ArrayList<BFDExportConfigEntry>();
  // private List<BFDExportConfigEntry> hospiceConfigs = new ArrayList<BFDExportConfigEntry>();
  // private List<BFDExportConfigEntry> medicareBeneficiaryIdConfigs = new ArrayList<BFDExportConfigEntry>();
  private List<BFDExportConfigEntry> outpatientConfigs = new ArrayList<BFDExportConfigEntry>();
  // private List<BFDExportConfigEntry> pdeConfigs = new ArrayList<BFDExportConfigEntry>();
  private List<BFDExportConfigEntry> prescriptionConfigs = new ArrayList<BFDExportConfigEntry>();
  // private List<BFDExportConfigEntry> snfConfigs = new ArrayList<BFDExportConfigEntry>();
  
  /** constructor */
  public BFDExportBuilder() {
    this.configFile = new File( "src/main/resources/exporters/cms_field_values.tsv" );
    this.initConfigs();
  }

  /** initialize object from configuration file */
  private List<BFDExportConfigEntry> initConfigs() {
    try {
      System.out.println("reading from " + this.configFile.getAbsolutePath() );
      Reader reader = new BufferedReader(new FileReader(this.configFile));
      CsvToBean<BFDExportConfigEntry> csvReader = new CsvToBeanBuilder<BFDExportConfigEntry>(reader)
        .withType(BFDExportConfigEntry.class)
        .withSeparator('\t')
        .withIgnoreLeadingWhiteSpace(true)
        .withIgnoreEmptyLine(true)
        .build();
      this.allConfigs = csvReader.parse();

      // this.initConfigItems();
      for ( BFDExportConfigEntry prop: this.getAllConfigs() ) {
        if ( !prop.getBeneficiary().isEmpty() ) {
          this.beneficiaryConfigs.add(prop);
        }
        if ( !prop.getBeneficiary_history().isEmpty() ) {
          this.beneficiaryHistoryConfigs.add(prop);
        }
        if ( !prop.getCarrier().isEmpty() ) {
          this.carrierConfigs.add(prop);
        }
        if ( !prop.getInpatient().isEmpty() ) {
          this.inpatientConfigs.add(prop);
        }
        if ( !prop.getOutpatient().isEmpty() ) {
          this.outpatientConfigs.add(prop);
        }
        if ( !prop.getPrescription().isEmpty() ) {
          this.prescriptionConfigs.add(prop);
        }
      } 
      return this.allConfigs;
    }
    catch ( IOException ex ) {
      System.out.println( "Error reading " + this.configFile.getAbsolutePath() );
      return null;
    }
  }

  /** Sets the known field values based on exporter config TSV file
     * @param type output type (one of the ExportConfigType types)
     * @param fieldValues reference to a HashMap of field values in each of the exportXXXXX() functions
     * @param getCellValueFunc reference to Function that retrieves the string value relevant to the current output type from the config file
     * @param getFieldEnumFunc reference to Function that retrieves the enum relevant to the current output type
     * @return the updated field values
     */
  public HashMap setFromConfig( ExportConfigType type, HashMap fieldValues, Function<BFDExportConfigEntry, String> getCellValueFunc, Function<String, Enum> getFieldEnumFunc ) {
    fieldValues.clear();
    List<BFDExportConfigEntry> configs = this.getConfigItemsByType(type);
    try {
      // System.out.println("^^^^^"+type+"^^^^^"+configs.get(0));
      int propCount = 0;
      for ( BFDExportConfigEntry prop: configs ) {
        String cell = getCellValueFunc.apply( prop );
        // System.out.println("*****"+cell);
        if ( !cell.isEmpty() ) {
          propCount++;
          String value = cell;
          String comment = null;
          int commentStart = cell.indexOf("(");
          if ( commentStart >= 0 ) {
            value = cell.substring(0, commentStart - 1);
            comment = cell.substring(commentStart + 1, cell.length()-1);
          }
          Enum fieldEnum = getFieldEnumFunc.apply(prop.getField());
          // System.out.println("     field enum"+fieldEnum);
          // System.out.println("     value: " + value);
          // System.out.println("     comment: " + comment);
          fieldValues.put(fieldEnum, value);
        }
      }
      System.out.println("config props defined and processed for " + type + ":  " + propCount );
    }
    catch (Exception ex) {
      System.out.println("ExportDataBuilder.setFromConfig ERROR:  " + ex);
    }
    return fieldValues;
  }

  /** returns all the config items */
  public List<BFDExportConfigEntry> getAllConfigs() {
    return this.allConfigs;
  }


  /** returns all inpatient config items */
  public List<BFDExportConfigEntry> getConfigItemsByType(ExportConfigType type) {
    switch( type ) {
      case BENEFICIARY:
        return this.beneficiaryConfigs;
      case BENEFICIARY_HISTORY:
        return this.beneficiaryHistoryConfigs;
      case CARRIER:
        return this.carrierConfigs;
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

