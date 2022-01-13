package org.mitre.synthea.identity;

import org.mitre.synthea.world.agents.Person;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
  private transient Seed seed;

  public Variant() {

  }

  public String getVariantId() {
    return variantId;
  }

  public void setVariantId(String variantId) {
    this.variantId = variantId;
  }

  public Period getPeriod() {
    return seed.getPeriod();
  }

  public String getGivenName() {
    if (givenName == null) {
      return seed.getGivenName();
    }
    return givenName;
  }

  public void setGivenName(String givenName) {
    this.givenName = givenName;
  }

  public String getFamilyName() {
    if (familyName == null) {
      return familyName;
    }
    return familyName;
  }

  public void setFamilyName(String familyName) {
    this.familyName = familyName;
  }

  public String getPhone() {
    if (phone == null) {
      return phone;
    }
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public List<String> getAddressLines() {
    if (addressLines == null || addressLines.isEmpty()) {
      return seed.getAddressLines();
    }
    return addressLines;
  }

  public void setAddressLines(List<String> addressLines) {
    this.addressLines = addressLines;
  }

  public String getCity() {
    if (city == null) {
      return seed.getCity();
    }
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getState() {
    if (state == null) {
      return seed.getState();
    }
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getZipCode() {
    if (zipCode == null) {
      return seed.getZipCode();
    }
    return zipCode;
  }

  public void setZipCode(String zipCode) {
    this.zipCode = zipCode;
  }

  public LocalDate getDateOfBirth() {
    if (dateOfBirth == null) {
      return seed.getDateOfBirth();
    }
    return dateOfBirth;
  }

  public void setDateOfBirth(LocalDate dateOfBirth) {
    this.dateOfBirth = dateOfBirth;
  }

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
    return attributes;
  }
}
