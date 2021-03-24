package org.mitre.synthea.input;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.geography.Demographics;

public class FixedRecord {

  @SerializedName(value = "record_id")
  public String recordId;

  @SerializedName(value = "seed_id")
  public String seedID;

  @SerializedName(value = "GN")
  public String firstName;

  @SerializedName(value = "SN")
  public String lastName;

  @SerializedName(value = "DOB_DAY")
  public String birthDayOfMonth;

  @SerializedName(value = "DOB_MONTH")
  public String birthMonth;

  @SerializedName(value = "DOB_YEAR")
  public String birthYear;

  @SerializedName(value = "GENDER")
  public String gender;

  @SerializedName(value = "PHONE_CODE")
  public String phoneAreaCode;

  @SerializedName(value = "PHONE_NUMBER")
  public String phoneNumber;

  @SerializedName(value = "ADDRESS1")
  public String addressLineOne;

  @SerializedName(value = "ADDRESS2")
  public String addressLineTwo;

  @SerializedName(value = "ADDRESS_CITY")
  public String city;

  @SerializedName(value = "ADDRESS_STATE")
  public String state;

  @SerializedName(value = "ADDRESS_COUNTRY")
  public String country;

  @SerializedName(value = "ADDRESS_ZIP")
  public String zipcode;

  @SerializedName(value = "CONTACT_GN")
  public String contactFirstName;

  @SerializedName(value = "CONTACT_SN")
  public String contactLastName;

  @SerializedName(value = "EMAIL")
  public String contactEmail;

  @SerializedName(value = "ADDRESS_SEQUENCE")
  public int addressSequence;

  @SerializedName(value = "hh_id")
  public String householdId;

  @SerializedName(value = "hh_status")
  public String householdRole;

  // Attributes map
  @Expose(serialize = false, deserialize = true)
  private transient Map<String, Object> attributes;

  // The end date that this record is valid. To be set based on other records in
  // the record group.
  public int addressEndDate;

  /**
   * Constructor
   */
  public FixedRecord() {
    this.attributes = new HashMap<String, Object>();
  }

  /**
   * Returns the city of this fixedRecord if it is a valid city.
   */
  public String getValidCity(FixedRecordGroup frg) {
    if(this.city == null){
      return frg.getSeedCity();
    }
    String tempCity = WordUtils.capitalize(this.city.toLowerCase());
    try {
      // If the the current city/state combo is not in the Demographics file, return
      // the safe seed city of the frg.
      if (Demographics.load(this.state).row(this.state).values().stream()
          .noneMatch(d -> d.city.equalsIgnoreCase(tempCity))) {
        return frg.getSeedCity();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    // Otherwise it's a safe city that can be returned.
    return this.city;
  }

  /**
   * Converts the birth year of the record into a birthdate.
   */
  public long getBirthDate() {
    String birthYear = this.birthYear;

    long bd = LocalDateTime.of(Integer.parseInt(birthYear), Integer.parseInt(this.birthMonth),
        Integer.parseInt(this.birthDayOfMonth), 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli();

    return bd;
  }

  /**
   * Returns the attributes associated with this FixedRecord.
   * 
   * @return the attribute Map associated with this FixedRecord.
   */
  public Map<String, Object> getFixedRecordAttributes() {
    if (this.attributes.isEmpty()) {
      this.attributes.put(Person.IDENTIFIER_RECORD_ID, this.recordId);
      if (this.seedID == null) {
        this.attributes.put(Person.IDENTIFIER_SEED_ID, "ERROR:_THIS_IS_THE_SEED_RECORD");
      } else {
        this.attributes.put(Person.IDENTIFIER_SEED_ID, this.seedID);
      }
      this.attributes.putAll(this.getNameAttributes());
      if(this.phoneAreaCode == null){
        this.phoneAreaCode = "";
      }
      if(this.phoneNumber == null){
        this.phoneNumber = "";
      }
      this.attributes.put(Person.TELECOM, this.phoneAreaCode + "-" + this.phoneNumber);
      String g = this.gender;
      if(this.gender == null && this.seedID == null){
        throw new RuntimeException("Input gender is null for seed record " + this.recordId + ".");
      }
      if (g == null ||g.equalsIgnoreCase("None") || StringUtils.isBlank(g)) {
        g = "UNK";
      }
      this.attributes.put(Person.GENDER, g);
      this.attributes.put(Person.BIRTHDATE, this.getBirthDate());
      if(this.state == null){
        this.state = "";
      }
      this.attributes.put(Person.STATE, this.state);
      if (this.city == null) {
        this.city = "none";
      }
      this.attributes.put(Person.CITY, this.city);
      if (this.addressLineOne == null) {
        this.addressLineOne = "";
      }
      this.attributes.put(Person.ADDRESS, this.addressLineOne);
      if (this.zipcode == null) {
        this.zipcode = "";
      }
      this.attributes.put(Person.ZIP, this.zipcode);
      if (this.contactLastName != null) {
        this.attributes.put(Person.CONTACT_GIVEN_NAME, this.contactFirstName);
        this.attributes.put(Person.CONTACT_FAMILY_NAME, this.contactLastName);
      }
      this.attributes.put(Person.CONTACT_EMAIL, this.contactEmail);
      if (this.contactEmail == null) {
        this.attributes.put(Person.CONTACT_EMAIL, "");
      }
      if (this.householdRole == null) {
        throw new RuntimeException("Household roles cannot be null.");
      }
      this.attributes.put(Person.HOUSEHOLD_ROLE, this.householdRole);
    }
    return this.attributes;
  }

  /**
   * Overwrites the address attribute information for the given person with this
   * FixedRecord.
   * 
   * @return Whether the address changed since the last FixedRecord.
   */
  public boolean overwriteAddress(Person person, Generator generator) {
    String oldCity = (String) person.attributes.get(Person.CITY);
    String oldAddress = (String) person.attributes.get(Person.ADDRESS);
    person.attributes.put(Person.ADDRESS, this.addressLineOne);
    if(this.state == null && this.seedID != null){
      // If this is a seed record and there's a null state, we got a problem.
      throw new RuntimeException("Cannot have a null state for seed record. Seed Record ID: " + this.seedID);
    } else if(state == null){
      person.attributes.put(Person.STATE, "");

    } else {
      person.attributes.put(Person.STATE, this.state);
    }
    person.attributes.put(Person.CITY, this.getValidCity(Generator.fixedRecordGroupManager.getRecordGroupFor(person)));
    person.attributes.put(Person.ZIP, this.zipcode);
    // Fix the person's safe city in case it is invalid and update their location
    // point.
    generator.location.assignPoint(person, Generator.fixedRecordGroupManager.getRecordGroupFor(person).getSeedCity());
    // Return a boolean indicating whether the address was changed.
    return !oldCity.equals(person.attributes.get(Person.CITY))
        && !oldAddress.equals(person.attributes.get(Person.ADDRESS));
  }

  /**
   * Returns the name attributes of the current fixed record.
   */
  private Map<String, Object> getNameAttributes() {
    if(this.firstName == null){
      this.firstName = "";
    } if(this.lastName== null){
      this.lastName = "";
    }
    return Stream
        .of(new String[][] { { Person.FIRST_NAME, this.firstName }, { Person.LAST_NAME, this.lastName },
            { Person.NAME, this.firstName + " " + this.lastName }, })
        .collect(Collectors.toMap(data -> data[0], data -> data[1]));
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof FixedRecord)) {
      return false;
    }
    FixedRecord that = (FixedRecord) o;
    return this.recordId.equals(that.recordId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(this.seedID);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (this.seedID == null) {
      // This is a seed record.
      sb.append("Seed Record: [").append(this.recordId).append("]");
    } else {
      // This is a fixed record.
      sb.append("Variant Record: [").append("Record ID: ").append(this.recordId).append(", Seed ID:")
          .append(this.seedID).append("]");
    }
    return sb.toString();
  }
}
