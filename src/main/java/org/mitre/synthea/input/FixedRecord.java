package org.mitre.synthea.input;

import com.google.gson.annotations.SerializedName;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;

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
    return "Colorado";
  }

  /**
   * Gets a safe city from the Fixed Record file in case it is not a valid city.
   */
  public String getSafeCity() {
    switch (this.city.toLowerCase()) {
      case "greenwood vlg":
      case "highlands ranch":
      case "hghlands ranch":
      case "highlands  ranch":
      case "hghlnds ranch":
      case "hghlnds  ranch":
      case "hghlnds   ranch":
      case "ranch":
      case "centemial":
      case "cente3nnial":
        return "Centennial";
      case "federal hgts":
        return "Thornton";
      case "henderson":
      case "bighton":
        return "Brighton";
      case "niwot":
        return "Longmont";
      case "franktown":
      case "sedalia":
      case "castle":
      case "castle-rock":
        return "Castle Rock";
      case "conifer":
      case "evergreen":
      case "pine":
      case "idledale":
      case "indian hills":
        return "Morrison";
      case "byers":
      case "strasburg":
      case "strasbourg":
      case "watkins":
        return "Bennett";
      case "westnminster":
      case "westminlster":
        return "Westminster";
      case "devner":
        return "Denver";
      case "allenspark":
        return "Jamestown";
      case "lakewoood":
        return "Lakewood";
      case "commerce  city":
      case "commerce              city":
        return "Commerce City";
      case "###boulder":
        return "Boulder";
      case "littlet0n":
        return "Littleton";
      default:
        return this.city;
    }
  }

  /**
   * Converts the birth year of the record into a birthdate.
   */
  public long getBirthDate() {
    String birthYear = this.birthYear;
    switch (birthYear.length()) {
      case 1:
        birthYear = "200" + birthYear;
        break;
      case 2:
        birthYear = "20" + birthYear;
        break;
      default:
        break;
    }

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
   * Completely overwrites the given person to have the demographic attributes in
   * this FixedRecord.
   * 
   * @param person The person who's attributes will be overwritten.
   */
  public void totalOverwrite(Person person) {
    String g = this.gender;
    if (g.equalsIgnoreCase("None") || StringUtils.isBlank(g)) {
      g = "UNK";
    }
    person.attributes.put(Person.GENDER, g);

    try {
      person.attributes.put(Person.BIRTHDATE, this.getBirthDate());
    } catch (java.time.DateTimeException | java.lang.NumberFormatException | java.lang.NullPointerException dontcare) {
      long bd = LocalDateTime.of(2010, 7, 2, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
      person.attributes.put(Person.BIRTHDATE, bd);
    }

    person.attributes.put(Person.CITY, this.city);
    person.attributes.put(Person.ZIP, this.zipcode);

    person.attributes.putAll(this.getFixedRecordAttributes());

  }

  /**
   * Overwrites the given person to have the relevant demographic attributes in
   * this FixedRecord.
   * 
   * @param person The person who's attributes will be overwritten.
   * @return
   */
  public Map<String, Object> getFixedRecordAttributes() {
    Map<String, Object> attributes = new HashMap<String, Object>();
    attributes.put(Person.FIRST_NAME, this.firstName);
    attributes.put(Person.LAST_NAME, this.lastName);
    attributes.put(Person.NAME, this.firstName + " " + this.lastName);
    attributes.put(Person.TELECOM, this.getTelecom());
    attributes.put(Person.IDENTIFIER_RECORD_ID, this.recordId);
    attributes.put(Person.IDENTIFIER_SITE, this.site);
    attributes.put(Person.CONTACT_GIVEN_NAME, this.parentFirstName);
    attributes.put(Person.CONTACT_FAMILY_NAME, this.parentLastName);
    attributes.put(Person.CONTACT_EMAIL, this.parentEmail);
    attributes.put(Person.ADDRESS, this.addressLineOne);
    return attributes;
  }
}
