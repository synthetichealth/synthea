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
 * Export configuration class for BFD
 */
public class BFDExportConfig {

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
  
  /** constructor
   *  @param configFilePath path to the configuration TSV file
   */
  public BFDExportConfig( String configFilePath ) {
    this.configFile = new File( configFilePath );
    this.initConfigs();
  }

  /** initialize object from configuration file
   *  @return the List of BFDExportConfigEntry
   */
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

