package org.mitre.synthea.input;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.geography.Demographics;

public class FixedRecord {
  @SerializedName(value = "LIST_ID")
  public String site;

  @SerializedName(value = "seed_id")
  public String seedID;

  @SerializedName(value = "record_id")
  public String recordId;

  @SerializedName(value = "SN")
  public String lastName;

  @SerializedName(value = "GN")
  public String firstName;

  @SerializedName(value = "DOB_YEAR")
  public String birthYear;

  @SerializedName(value = "DOB_MONTH")
  public String birthMonth;

  @SerializedName(value = "DOB_DAY")
  public String birthDayOfMonth;

  @SerializedName(value = "GENDER")
  public String gender;

  @SerializedName(value = "PHONE_CODE")
  public String phoneAreaCode;

  @SerializedName(value = "PHONE_NUMBER")
  public String phoneNumber;

  @SerializedName(value = "ADDRESS1")
  public String addressLineOne;

  @SerializedName(value = "ADDRESS_STREET2")
  public String addressLineTwo;

  @SerializedName(value = "ADDRESS_CITY")
  public String city;

  @SerializedName(value = "ADDRESS_STATE")
  public String state;

  @SerializedName(value = "ADDRESS_COUNTRY")
  public String country;

  @SerializedName(value = "ADDRESS_ZIP")
  public String zipcode;

  @SerializedName(value = "PARENT1_SURNAME")
  public String parentLastName;

  @SerializedName(value = "PARENT1_GIVEN_NAME")
  public String parentFirstName;

  @SerializedName(value = "PARENT1_EMAIL")
  public String parentEmail;

  @SerializedName(value = "RECORD_DATES")
  public String recordDates;

  @SerializedName(value = "hh_id")
  public String householdId;

  @SerializedName(value = "hh_status")
  public String householdRole;

  // Attributes map
  @Expose(serialize = false, deserialize = true) private transient Map<String, Object> attributes;


  /**
   * Returns the city of this fixedRecord if it is a valid city.
   */
  public String getSafeCity() {
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
   * Return the telephone number associated with this FixedRecord.
   * 
   * @return The phone number in this FixedRecord.
   */
  public String getTelecom() {
    return this.phoneAreaCode + "-" + this.phoneNumber;
  }

  /**
   * Returns the attributes associated with this FixedRecord.
   * 
   * @return the attributes associated with this FixedRecord.
   */
  public Map<String, Object> getFixedRecordAttributes() {
    if (this.attributes == null) {
      this.attributes = new HashMap<String, Object>();
      this.attributes.put(Person.FIRST_NAME, this.firstName);
      this.attributes.put(Person.LAST_NAME, this.lastName);
      this.attributes.put(Person.NAME, this.firstName + " " + this.lastName);
      this.attributes.put(Person.TELECOM, this.getTelecom());
      // this.attributes.put(Person.IDENTIFIER_RECORD_ID, this.recordId);
      // this.attributes.put(Person.IDENTIFIER_SITE, this.site);
      // this.attributes.put(Person.CONTACT_GIVEN_NAME, this.parentFirstName);
      // this.attributes.put(Person.CONTACT_FAMILY_NAME, this.parentLastName);
      // this.attributes.put(Person.CONTACT_EMAIL, this.parentEmail);
      // this.attributes.put(Person.ADDRESS, this.addressLineOne);
      String g = this.gender;
      if (g.equalsIgnoreCase("None") || StringUtils.isBlank(g)) {
        g = "UNK";
      }
      this.attributes.put(Person.GENDER, g);
      this.attributes.put(Person.BIRTHDATE, this.getBirthDate());
      // this.attributes.put(Person.STATE, this.state);
      // this.attributes.put(Person.CITY, this.getSafeCity());
      // this.attributes.put(Person.ZIP, this.zipcode);
    }
    System.out.println(this.recordDates);
    return this.attributes;
  }

  /**
   * Checks the record dates of the current FixedRecord in relation to the given
   * year.
   * 
   * @return Whether the given year is within the FixedRecord date range.
   */
  public boolean checkRecordDates(int currentYear) {
    // Pull out the 2 years from the current fixed record.
    String years[] = this.recordDates.split("-");
    // Check if the current year is between the years in the current fixed record.
    return currentYear >= Integer.parseInt(years[0]) && currentYear <= Integer.parseInt(years[1]);
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
    person.attributes.put(Person.CITY, this.getSafeCity());
    person.attributes.put(Person.ZIP, this.zipcode);
    // Fix the person's safe city in case it is invalid and update their location
    // point.
    generator.location.assignPoint(person, (String) person.attributes.get(Person.CITY));
    return !oldCity.equals(person.attributes.get(Person.CITY)) && !oldAddress.equals(person.attributes.get(Person.ADDRESS));
  }
}
