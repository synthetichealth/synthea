package org.mitre.synthea.input;

import com.google.gson.annotations.SerializedName;
import org.mitre.synthea.world.agents.Person;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class FixedRecord {
  @SerializedName(value = "LIST_ID")
  public String site;

  @SerializedName(value = "RECORD_ID")
  public String recordId;

  @SerializedName(value = "CHILD_SURNAME")
  public String lastName;

  @SerializedName(value = "CHILD_GIVEN_NAME")
  public String firstName;

  @SerializedName(value = "DOB_YEAR")
  public String birthYear;

  @SerializedName(value = "DOB_MONTH")
  public String birthMonth;

  @SerializedName(value = "DOB_DAY")
  public String birthDayOfMonth;

  @SerializedName(value = "GENDER")
  public String gender;

  @SerializedName(value = "PHONE_AREA_CODE")
  public String phoneAreaCode;

  @SerializedName(value = "PHONE_NUMBER")
  public String phoneNumber;

  @SerializedName(value = "ADDRESS_STREET1")
  public String addressLineOne;

  @SerializedName(value = "ADDRESS_STREET2")
  public String addressLineTwo;

  @SerializedName(value = "ADDRESS_CITY")
  public String city;

  @SerializedName(value = "ADDRESS_STATE")
  public String state;

  @SerializedName(value = "ADDRESS_COUNTRY")
  public String country;

  @SerializedName(value = "ADDRESS_ZIPCODE")
  public String zipcode;

  @SerializedName(value = "PARENT1_SURNAME")
  public String parentLastName;

  @SerializedName(value = "PARENT1_GIVEN_NAME")
  public String parentFirstName;

  @SerializedName(value = "PARENT1_EMAIL")
  public String parentEmail;

  public String getState() {
    if (this.state == null || this.state.equals("")) {
      return "Colorado";
    } else {
      return this.state;
    }
  }

  public String getSafeCity() {
    switch (this.city.toLowerCase()) {
      case "greenwood vlg":
      case "hghlnds ranch":
        return "Centennial";
      case "federal hgts":
        return "Thornton";
      case "henderson":
        return "Brighton";
      case "niwot":
        return "Longmont";
      case "franktown":
        return "Castle Rock";
      case "evergreen":
        return "Morrison";
      default:
        return this.city;
    }
  }

  public long getBirthDate() {
    return LocalDateTime.of(Integer.parseInt(this.birthYear), Integer.parseInt(this.birthMonth),
        Integer.parseInt(this.birthDayOfMonth), 0, 0).toInstant(ZoneOffset.UTC)
        .toEpochMilli();
  }

  public void overwriteDemoAttributes(Person person) {
    person.attributes.put(Person.GENDER, this.gender);
  }

  public String getTelecom() {
    return this.phoneAreaCode + "-" + this.phoneNumber;
  }

  public void totalOverwrite(Person person) {
    overwriteDemoAttributes(person);
    person.attributes.put(Person.FIRST_NAME, this.firstName);
    person.attributes.put(Person.LAST_NAME, this.lastName);
    person.attributes.put(Person.BIRTHDATE, this.getBirthDate());
    person.attributes.put(Person.TELECOM, this.getTelecom());
    person.attributes.put(Person.CITY, this.city);
    person.attributes.put(Person.IDENTIFIER_RECORD_ID, this.recordId);
    person.attributes.put(Person.IDENTIFIER_SITE, this.site);
    person.attributes.put(Person.CONTACT_GIVEN_NAME, this.parentFirstName);
    person.attributes.put(Person.CONTACT_FAMILY_NAME, this.parentLastName);
    person.attributes.put(Person.CONTACT_EMAIL, this.parentEmail);
  }
}
