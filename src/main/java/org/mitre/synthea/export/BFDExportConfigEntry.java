package org.mitre.synthea.export;

/**
 * This class represents a single line in the export configuration TSV file. It
 * contains one property for all export specifications that has a specification
 * (i.e., not NULL or empty). Implementation Note: the names of the properties
 * MUST be exactly the same as the heading in the TSV file.
 */

public class BFDExportConfigEntry {
  private int lineNum; // the line that the config appears in in the TSV file
  private String field;
  private String beneficiary;
  private String beneficiaryHistory;
  private String dme;
  private String inpatient;
  private String outpatient;
  private String carrier;
  private String prescription;

  static int sNextLineNum = 1; // starts from offset due headers, etc.

  /**
   * Trims any newlines, tabs, spaces from value.
   * 
   * @param value the string to evaluate for newlines, tabs, etc.
   */
  public String trimAllWhitespace(String value) {
    return value.trim();
  }

  public int getLineNum() {
    return this.lineNum;
  }

  public void setlineNum(int lineNum) {
    this.lineNum = lineNum;
  }

  public String getField() {
    return this.field;
  }

  public void setField(String field) {
    this.field = trimAllWhitespace(field);
  }

  public String getBeneficiary() {
    return this.beneficiary;
  }

  public void setBeneficiary(String beneficiary) {
    this.beneficiary = trimAllWhitespace(beneficiary);
  }

  public String getbeneficiaryHistory() {
    return this.beneficiaryHistory;
  }

  public void setbeneficiaryHistory(String beneficiaryHistory) {
    this.beneficiaryHistory = trimAllWhitespace(beneficiaryHistory);
  }

  public String getDme() {
    return dme;
  }

  public void setDme(String dme) {
    this.dme = dme;
  }

  public String getInpatient() {
    return this.inpatient;
  }

  public void setInpatient(String inpatient) {
    this.inpatient = trimAllWhitespace(inpatient);
  }

  public String getOutpatient() {
    return this.outpatient;
  }

  public void setOutpatient(String outpatient) {
    this.outpatient = trimAllWhitespace(outpatient);
  }

  public String getCarrier() {
    return this.carrier;
  }

  public void setCarrier(String carrier) {
    this.carrier = trimAllWhitespace(carrier);
  }

  public String getPrescription() {
    return this.prescription;
  }

  public void setPrescription(String prescription) {
    this.prescription = trimAllWhitespace(prescription);
  }


  @Override
  public String toString() {
    return "{"
      + " lineNum=" + getLineNum()
      + " field='" + getField() + "'"
      + ", beneficiary='" + getBeneficiary() + "'"
      + ", beneficiaryHistory='" + getbeneficiaryHistory() + "'"
      + ", inpatient='" + getInpatient() + "'"
      + ", outpatient='" + getOutpatient() + "'"
      + ", carrier='" + getCarrier() + "'"
      + ", prescription='" + getPrescription() + "'"
      + "}";
  }
}
