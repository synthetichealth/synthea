package org.mitre.synthea.identity;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

/**
 * A representation of demographic information for a person. The expectation is that it will be a
 * deviation from its associated Seed, reproducing the variability usually observed in demographic
 * data, such as nicknames, typos, or even placeholder values. Variant information is only used for
 * supplying information to write into the exported record, so it can contain an incorrect date of
 * birth or non-existent city.
 * <p>
 *   Variants may be sparsely populated. If a value for a field is null, it will pull the value from
 *   the associated seed.
 * </p>
 */
public class Variant implements IdentityRecord {
  private String variantId;
  private String givenName;
  private String familyName;
  private String phone;
  private List<String> addressLines;
  private String city;
  private String state;
  private String zipCode;
  private LocalDate dateOfBirth;
  private String gender;
  private String socialSecurityNumber;
  private transient Seed seed;

  public String getVariantId() {
    return variantId;
  }

  public void setVariantId(String variantId) {
    this.variantId = variantId;
  }

  public Period getPeriod() {
    return seed.getPeriod();
  }

  /**
   * Gets the given name for the variant. If null, will return the given name of the associated
   * seed
   * @return the given name
   */
  public String getGivenName() {
    if (givenName == null) {
      return seed.getGivenName();
    }
    return givenName;
  }

  public void setGivenName(String givenName) {
    this.givenName = givenName;
  }

  /**
   * Gets the family name for the variant. If null, will return the family name of the associated
   * seed
   * @return the family name
   */
  public String getFamilyName() {
    if (familyName == null) {
      return seed.getFamilyName();
    }
    return familyName;
  }

  public void setFamilyName(String familyName) {
    this.familyName = familyName;
  }

  /**
   * Gets the phone number for the variant. If null, will return the phone number of the associated
   * seed
   * @return the phone number
   */
  public String getPhone() {
    if (phone == null) {
      return seed.getPhone();
    }
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  /**
   * Gets the address lines for the variant. If null, will return the address lines of the
   * associated seed
   * @return the address lines
   */
  public List<String> getAddressLines() {
    if (addressLines == null || addressLines.isEmpty()) {
      return seed.getAddressLines();
    }
    return addressLines;
  }

  public void setAddressLines(List<String> addressLines) {
    this.addressLines = addressLines;
  }

  /**
   * Gets the city for the variant. If null, will return the city of the associated
   * seed
   * @return the city
   */
  public String getCity() {
    if (city == null) {
      return seed.getCity();
    }
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  /**
   * Gets the state for the variant. If null, will return the state of the associated
   * seed
   * @return the state
   */
  public String getState() {
    if (state == null) {
      return seed.getState();
    }
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  /**
   * Gets the zip code for the variant. If null, will return the zip code of the associated
   * seed
   * @return the zip code
   */
  public String getZipCode() {
    if (zipCode == null) {
      return seed.getZipCode();
    }
    return zipCode;
  }

  public void setZipCode(String zipCode) {
    this.zipCode = zipCode;
  }

  /**
   * Gets the date of birth for the variant. If null, will return the date of birth of the
   * associated seed
   * @return the date of birth
   */
  public LocalDate getDateOfBirth() {
    if (dateOfBirth == null) {
      return seed.getDateOfBirth();
    }
    return dateOfBirth;
  }

  public void setDateOfBirth(LocalDate dateOfBirth) {
    this.dateOfBirth = dateOfBirth;
  }

  /**
   * Gets the gender for the variant. If null, will return the gender of the associated
   * seed
   * @return the gender
   */
  public String getGender() {
    if (gender == null) {
      return seed.getGender();
    }
    return gender;
  }

  public void setGender(String gender) {
    this.gender = gender;
  }

  public Seed getSeed() {
    return seed;
  }

  public void setSeed(Seed seed) {
    this.seed = seed;
  }

  @Override
  public long birthdateTimestamp() {
    return this.getDateOfBirth().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
  }

  @Override
  public String getSocialSecurityNumber() {
    if (socialSecurityNumber == null) {
      return this.getSeed().getSocialSecurityNumber();
    }

    return socialSecurityNumber;
  }

  public void setSocialSecurityNumber(String socialSecurityNumber) {
    this.socialSecurityNumber = socialSecurityNumber;
  }

  /**
   * Returns the attributes the Synthea Generator usually fills in for a person. These can be used
   * to overwrite those attributes with information from the fixed record file
   * @return a map of person attributes
   */
  public Map<String, Object> demographicAttributesForPerson() {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put(Person.IDENTIFIER_SEED_ID, this.getSeed().getSeedId());
    attributes.put(Person.FIRST_NAME, this.getGivenName());
    attributes.put(Person.LAST_NAME, this.getFamilyName());
    attributes.put(Person.NAME, this.getGivenName() + " " + this.getFamilyName());
    attributes.put(Person.TELECOM, this.getPhone());
    attributes.put(Person.GENDER, this.getGender());
    attributes.put(Person.STATE, this.getState());
    attributes.put(Person.CITY, this.getCity());
    attributes.put(Person.ADDRESS, this.getAddressLines().stream()
        .collect(Collectors.joining("\n")));
    attributes.put(Person.ZIP, this.getZipCode());
    attributes.put(Person.IDENTIFIER_VARIANT_ID, this.getVariantId());
    if (this.getSocialSecurityNumber() != null) {
      attributes.put(Person.IDENTIFIER_SSN, this.getSocialSecurityNumber());
    }
    return Utilities.cleanMap(attributes);
  }
}