package org.mitre.synthea.export;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.List;
import java.util.ArrayList;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;


/**
 * Export configuration class
 */
public class ExportConfig {

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
  private List<ExportConfigEntry> allConfigs = null;
  
  private List<ExportConfigEntry> beneficiaryConfigs = new ArrayList<ExportConfigEntry>();
  private List<ExportConfigEntry> beneficiaryHistoryConfigs = new ArrayList<ExportConfigEntry>();
  private List<ExportConfigEntry> carrierConfigs = new ArrayList<ExportConfigEntry>();
  // private List<ExportConfigEntry> dmeConfigs = new ArrayList<ExportConfigEntry>();
  private List<ExportConfigEntry> inpatientConfigs = new ArrayList<ExportConfigEntry>();
  // private List<ExportConfigEntry> hhaConfigs = new ArrayList<ExportConfigEntry>();
  // private List<ExportConfigEntry> hospiceConfigs = new ArrayList<ExportConfigEntry>();
  // private List<ExportConfigEntry> medicareBeneficiaryIdConfigs = new ArrayList<ExportConfigEntry>();
  private List<ExportConfigEntry> outpatientConfigs = new ArrayList<ExportConfigEntry>();
  // private List<ExportConfigEntry> pdeConfigs = new ArrayList<ExportConfigEntry>();
  private List<ExportConfigEntry> prescriptionConfigs = new ArrayList<ExportConfigEntry>();
  // private List<ExportConfigEntry> snfConfigs = new ArrayList<ExportConfigEntry>();
  
  /** constructor
   *  @param configFilePath path to the configuration TSV file
   */
  public ExportConfig( String configFilePath ) {
    this.configFile = new File( configFilePath );
    this.initConfigs();
  }

  /** initialize object from configuration file
   *  @return the List of ExportConfigEntry
   */
  private List<ExportConfigEntry> initConfigs() {
    try {
      System.out.println("reading from " + this.configFile.getAbsolutePath() );
      Reader reader = new BufferedReader(new FileReader(this.configFile));
      CsvToBean<ExportConfigEntry> csvReader = new CsvToBeanBuilder<ExportConfigEntry>(reader)
        .withType(ExportConfigEntry.class)
        .withSeparator('\t')
        .withIgnoreLeadingWhiteSpace(true)
        .withIgnoreEmptyLine(true)
        .build();
      this.allConfigs = csvReader.parse();

      // this.initConfigItems();
      for ( ExportConfigEntry prop: this.getAllConfigs() ) {
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

  /** returns all the config items */
  public List<ExportConfigEntry> getAllConfigs() {
    return this.allConfigs;
  }


  /** returns all inpatient config items */
  public List<ExportConfigEntry> getConfigItemsByType(ExportConfigType type) {
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

