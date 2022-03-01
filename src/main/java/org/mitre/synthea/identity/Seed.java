package org.mitre.synthea.identity;

import static org.mitre.synthea.helpers.Utilities.localDateToTimestamp;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.mitre.synthea.helpers.RandomNumberGenerator;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

public class Seed implements IdentityRecord {
  private String seedId;
  private Period period;
  private String givenName;
  private String familyName;
  private String phone;
  private List<String> addressLines;
  private String city;
  private String state;
  private String zipCode;
  private String socialSecurityNumber;
  private transient Entity entity;
  private List<Variant> variants;

  public String getSeedId() {
    return seedId;
  }

  public void setSeedId(String seedId) {
    this.seedId = seedId;
  }

  public Period getPeriod() {
    return period;
  }

  public void setPeriod(Period period) {
    this.period = period;
  }

  public void setGivenName(String givenName) {
    this.givenName = givenName;
  }

  public void setFamilyName(String familyName) {
    this.familyName = familyName;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public void setAddressLines(List<String> addressLines) {
    this.addressLines = addressLines;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public void setState(String state) {
    this.state = state;
  }

  public void setZipCode(String zipCode) {
    this.zipCode = zipCode;
  }

  public List<Variant> getVariants() {
    return variants;
  }

  public void setVariants(List<Variant> variants) {
    this.variants = variants;
  }

  public Variant selectVariant(RandomNumberGenerator rng) {
    return variants.get(rng.randInt(variants.size()));
  }

  @Override
  public LocalDate getDateOfBirth() {
    return entity.getDateOfBirth();
  }

  @Override
  public String getGender() {
    return entity.getGender();
  }

  @Override
  public String getGivenName() {
    return givenName;
  }

  @Override
  public String getFamilyName() {
    return familyName;
  }

  @Override
  public String getPhone() {
    return this.phone;
  }

  @Override
  public List<String> getAddressLines() {
    return addressLines;
  }

  @Override
  public String getCity() {
    return city;
  }

  @Override
  public String getState() {
    return state;
  }

  @Override
  public String getZipCode() {
    return zipCode;
  }

  @Override
  public String getSocialSecurityNumber() {
    return socialSecurityNumber;
  }

  public void setSocialSecurityNumber(String socialSecurityNumber) {
    this.socialSecurityNumber = socialSecurityNumber;
  }

  @Override
  public long birthdateTimestamp() {
    return localDateToTimestamp(this.getDateOfBirth());
  }

  public void setEntity(Entity entity) {
    this.entity = entity;
  }

  /**
   * Returns the attributes the Synthea Generator usually fills in for a person. These can be used
   * to overwrite those attributes with information from the fixed record file
   * @return a map of person attributes
   */
  public Map<String, Object> demographicAttributesForPerson() {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put(Person.IDENTIFIER_SEED_ID, this.seedId);
    attributes.put(Person.FIRST_NAME, this.givenName);
    attributes.put(Person.LAST_NAME, this.familyName);
    attributes.put(Person.NAME, this.givenName + " " + this.familyName);
    attributes.put(Person.TELECOM, this.getPhone());
    attributes.put(Person.GENDER, this.getGender());
    attributes.put(Person.STATE, this.state);
    attributes.put(Person.CITY, this.city);
    attributes.put(Person.ADDRESS, this.addressLines.stream()
        .collect(Collectors.joining("\n")));
    attributes.put(Person.ZIP, this.zipCode);
    if (this.getSocialSecurityNumber() != null) {
      attributes.put(Person.IDENTIFIER_SSN, this.getSocialSecurityNumber());
    }
    return Utilities.cleanMap(attributes);
  }
}