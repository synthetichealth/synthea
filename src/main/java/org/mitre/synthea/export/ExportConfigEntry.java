package org.mitre.synthea.export;

import java.math.BigDecimal;
import java.io.Reader;

/** A single line in the export configuration TSV file, containing the full properties for a specification 
 *  Implementation Note: the names of the properties MUST be exactly the same as the heading in the TSV file
*/
public class ExportConfigEntry {
  private String field;
  private String beneficiary;
  private String beneficiary_history;  
  private String inpatient;
  private String outpatient;
  private String carrier;
  private String prescription;

  /** trims any newlines, tabs, spaces from value 
   *  @param value the string to evaluate for newlines, tabs, etc.
  */
  public String trimAllWhitespace( String value ) {
    return value.stripTrailing().stripLeading();
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

  public String getBeneficiary_history() {
    return this.beneficiary_history;
  }

  public void setBeneficiary_history(String beneficiary_history) {
    this.beneficiary_history = trimAllWhitespace(beneficiary_history);
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
    return "{" +
      " field='" + getField() + "'" +
      ", beneficiary='" + getBeneficiary() + "'" +
      ", beneficiary_history='" + getBeneficiary_history() + "'" +
      ", inpatient='" + getInpatient() + "'" +
      ", outpatient='" + getOutpatient() + "'" +
      ", carrier='" + getCarrier() + "'" +
      ", prescription='" + getPrescription() + "'" +
      "}";
  }


  public String toMinString( boolean printAll ) {

    // String optStr = getIsOptional() ? "opt" : "req";
    // String fixedValue = getFixedValue().isEmpty() ? "" : " fixedValue: " + getFixedValue();
    // String sampledValue = getSampledValues().isEmpty() ? "" : " sampledValue: " + getSampledValues();
    // String expression = getExpression().isEmpty() ? "" : " expression: " + getExpression();
    // String retval;
    // if ( getFixedValue().isEmpty() && getSampledValues().isEmpty() && getExpression().isEmpty() ) {
    //   retval = String.format( "  %30s:%5s", getColumnName(), optStr );
    // }
    // else {
    //   retval =  String.format( "  %30s:%5s => %5s %20s %s", getColumnName(), optStr, fixedValue, sampledValue, expression );
    // }

    // return retval;
    return "...";
  }
}
