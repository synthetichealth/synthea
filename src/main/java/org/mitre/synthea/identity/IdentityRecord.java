package org.mitre.synthea.identity;

import java.time.LocalDate;
import java.util.List;

/**
 * Interface representing an individual's identity record.
 */
public interface IdentityRecord {

  /**
   * Get the individual's date of birth.
   * @return the date of birth.
   */
  public LocalDate getDateOfBirth();

  /**
   * Get the individual's gender.
   * @return the gender.
   */
  public String getGender();

  /**
   * Get the individual's given name.
   * @return the given name.
   */
  public String getGivenName();

  /**
   * Get the individual's family name.
   * @return the family name.
   */
  public String getFamilyName();

  /**
   * Get the individual's phone number.
   * @return the phone number.
   */
  public String getPhone();

  /**
   * Get the individual's address lines.
   * @return the address lines.
   */
  public List<String> getAddressLines();

  /**
   * Get the individual's city of residence.
   * @return the city.
   */
  public String getCity();

  /**
   * Get the individual's state of residence.
   * @return the state.
   */
  public String getState();

  /**
   * Get the individual's ZIP code.
   * @return the ZIP code.
   */
  public String getZipCode();

  /**
   * Get the individual's birthdate as a timestamp.
   * @return the birthdate timestamp.
   */
  public long birthdateTimestamp();

  /**
   * Get the individual's Social Security Number.
   * @return the Social Security Number.
   */
  public String getSocialSecurityNumber();
}