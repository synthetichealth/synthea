package org.mitre.synthea.identity;

import org.mitre.synthea.helpers.RandomNumberGenerator;

import java.time.LocalDate;
import java.util.List;

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
    return phone;
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

  public void setEntity(Entity entity) {
    this.entity = entity;
  }
}
