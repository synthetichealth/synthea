package org.mitre.synthea.identity;

import java.time.LocalDate;
import java.util.List;

public interface IdentityRecord {
  public LocalDate getDateOfBirth();

  public String getGender();

  public String getGivenName();

  public String getFamilyName();

  public String getPhone();

  public List<String> getAddressLines();

  public String getCity();

  public String getState();

  public String getZipCode();
}
