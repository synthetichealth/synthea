package org.mitre.synthea.input;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.geography.Demographics;

public class FixedRecord {

  @SerializedName(value = "RECORD_ID")
  public String recordId;

  @SerializedName(value = "SEED_ID")
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

  @SerializedName(value = "ADDRESS_1")
  public String addressLineOne;

  @SerializedName(value = "ADDRESS_2")
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

  @SerializedName(value = "CONTACT_EMAIL")
  public String contactEmail;

  @SerializedName(value = "ADDRESS_ACTIVE_START")
  public int addressStartDate;

  @SerializedName(value = "HOUSEHOLD_ID")
  public String householdId;

  @SerializedName(value = "HOUSEHOLD_STATUS")
  public String householdRole;

  // Attributes map
  @Expose(serialize = false, deserialize = true) private transient Map<String, Object> attributes;

  // The end date that this record is valid. To be set based on other records in the record group.
  public int addressEndDate;

  /**
   * Constructor
   */
  public FixedRecord(){
    this.attributes = null;
  }

  /**
   * Returns the city of this fixedRecord if it is a valid city.
   */
  public String getCity() {
    try {
      // If the the current city/state combo is not in the Demographics file, return
      // null.
      if (Demographics.load(this.state).row(this.state).values().stream()
          .noneMatch(d -> d.city.equalsIgnoreCase(this.city))) {
        return null;
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
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
    if (this.attributes == null) {
      this.attributes = new HashMap<String, Object>();
      this.attributes.put(Person.IDENTIFIER_RECORD_ID, this.recordId);
      this.attributes.putAll(this.getNameAttributes());
      this.attributes.put(Person.TELECOM, this.phoneAreaCode + "-" + this.phoneNumber);
      String g = this.gender;
      if (g.equalsIgnoreCase("None") || StringUtils.isBlank(g)) {
        g = "UNK";
      }
      this.attributes.put(Person.GENDER, g);
      this.attributes.put(Person.BIRTHDATE, this.getBirthDate());
      this.attributes.put(Person.STATE, this.state);
      if (this.getCity() != null) {
        this.attributes.put(Person.CITY, this.getCity());
      } else {
        this.attributes.put(Person.CITY, this.city);
      }
      this.attributes.put(Person.ADDRESS, this.addressLineOne);
      this.attributes.put(Person.ZIP, this.zipcode);
      if (this.contactLastName != null) {
        this.attributes.put(Person.CONTACT_GIVEN_NAME, this.contactFirstName);
        this.attributes.put(Person.CONTACT_FAMILY_NAME, this.contactLastName);
      }
      this.attributes.put(Person.CONTACT_EMAIL, this.contactEmail);
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
    person.attributes.put(Person.STATE, this.state);
    if (this.getCity() != null) {
      person.attributes.put(Person.CITY, this.getCity());
    } else {
      person.attributes.put(Person.CITY, ((FixedRecordGroup)
          person.attributes.get(Person.RECORD_GROUP)).getSeedCity());
    }
    person.attributes.put(Person.ZIP, this.zipcode);
    // Fix the person's safe city in case it is invalid and update their location point.
    generator.location.assignPoint(person, (String) person.attributes.get(Person.CITY));
    // Return a boolean indicating whether the address was changed.
    return !oldCity.equals(person.attributes.get(Person.CITY))
        && !oldAddress.equals(person.attributes.get(Person.ADDRESS));
  }

  /**
   * Returns the name attributes of the current fixed record.
   */
  private Map<String, Object> getNameAttributes() {
    return Stream.of(new String[][] {
      {Person.FIRST_NAME, this.firstName},
      {Person.LAST_NAME, this.lastName},
      {Person.NAME, this.firstName + " " + this.lastName},
    }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
  }
}
