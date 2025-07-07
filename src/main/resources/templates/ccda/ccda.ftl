<#setting number_format="computer">
<?xml version="1.0" encoding="UTF-8"?>
<ClinicalDocument xmlns="urn:hl7-org:v3" xmlns:sdtc="urn:hl7-org:sdtc" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="urn:hl7-org:v3 http://xreg2.nist.gov:8080/hitspValidation/schema/cdar2c32/infrastructure/cda/C32_CDA.xsd">
  <realmCode code="US"/>
  <typeId root="2.16.840.1.113883.1.3" extension="POCD_HD000040"/>
  <templateId root="2.16.840.1.113883.10.20.22.1.1" extension="2015-08-01"/>
  <templateId root="2.16.840.1.113883.10.20.22.1.2" extension="2015-08-01"/>
  <id root="2.16.840.1.113883.19.5" extension="${id}" assigningAuthorityName="https://github.com/synthetichealth/synthea"/>
  <code code="34133-9" displayName="Summarization of episode note" codeSystem="2.16.840.1.113883.6.1" codeSystemName="LOINC"/>
  <title>C-CDA R2.1 Patient Record: ${name}</title>
  <effectiveTime value="${time?number_to_date?string["yyyyMMddHHmmss"]}"/>
  <confidentialityCode code="N"/>
  <languageCode code="en-US"/>
  <recordTarget>
    <patientRole>
      <id root="2.16.840.1.113883.19.5" extension="${id}" assigningAuthorityName="https://github.com/synthetichealth/synthea"/>
      <addr use="HP">
        <streetAddressLine>${address}</streetAddressLine>
        <city>${city}</city>
        <state>${state}</state>
        <#if zip?has_content>
        <postalCode>${zip}</postalCode>
        <#else>
        <postalCode nullFlavor="NI"/>
        </#if>
      </addr>
      <telecom nullFlavor="NI"/>
      <patient>
        <name>
          <given>${name?keep_before_last(" ")}</given>
          <family>${name?keep_after_last(" ")}</family>
        </name>
        <administrativeGenderCode code="${gender}" codeSystem="2.16.840.1.113883.5.1" codeSystemName="HL7 AdministrativeGender"/>
        <birthTime value="${birthdate?number_to_date?string["yyyyMMddHHmmss"]}"/>
        <#if race == "other">
        <raceCode code="2106-3" displayName="white" codeSystemName="CDC Race and Ethnicity" codeSystem="2.16.840.1.113883.6.238"/>
        <sdtc:raceCode code="${race_lookup[race]}" displayName="${race}" codeSystemName="CDC Race and Ethnicity" codeSystem="2.16.840.1.113883.6.238"/>
        <#else>
        <raceCode code="${race_lookup[race]}" displayName="${race}" codeSystemName="CDC Race and Ethnicity" codeSystem="2.16.840.1.113883.6.238"/>
        </#if>
        <ethnicGroupCode code="${ethnicity_lookup[race]}" displayName="${ethnicity_display_lookup[race]}" codeSystemName="CDC Race and Ethnicity" codeSystem="2.16.840.1.113883.6.238"/>
        <languageCommunication>
          <languageCode code="en-US"/>
        </languageCommunication>
      </patient>
    </patientRole>
  </recordTarget>
  <author>
    <time value="${time?number_to_date?string["yyyyMMddHHmmss"]}"/>
    <assignedAuthor>
      <id nullFlavor="NA"/>
      <addr nullFlavor="NA" />
      <telecom nullFlavor="NA" />
      <assignedAuthoringDevice>
        <manufacturerModelName>https://github.com/synthetichealth/synthea</manufacturerModelName>
        <softwareName>https://github.com/synthetichealth/synthea</softwareName>
      </assignedAuthoringDevice>
      <representedOrganization>
        <id nullFlavor="NA"/>
        <name>${preferredProviderwellness.name?replace("&", "&amp;")}</name>
        <telecom nullFlavor="NA"/>
        <addr>
          <streetAddressLine>${preferredProviderwellness.address?replace("&", "&amp;")}</streetAddressLine>
          <city>${preferredProviderwellness.city}</city>
          <state>${preferredProviderwellness.state}</state>
          <postalCode>${preferredProviderwellness.zip}</postalCode>
        </addr>
      </representedOrganization>
    </assignedAuthor>
  </author>
  <custodian>
    <assignedCustodian>
      <representedCustodianOrganization>
        <id nullFlavor="NA"/>
        <name>${preferredProviderwellness.name?replace("&", "&amp;")}</name>
        <telecom nullFlavor="NA"/>
        <addr>
          <streetAddressLine>${preferredProviderwellness.address?replace("&", "&amp;")}</streetAddressLine>
          <city>${preferredProviderwellness.city}</city>
          <state>${preferredProviderwellness.state}</state>
          <postalCode>${preferredProviderwellness.zip}</postalCode>
        </addr>
      </representedCustodianOrganization>
    </assignedCustodian>
  </custodian>
  <documentationOf>
    <serviceEvent classCode="PCPR">
      <effectiveTime>
        <low value="${birthdate?number_to_date?string["yyyyMMddHHmmss"]}"/>
        <high value="${time?number_to_date?string["yyyyMMddHHmmss"]}"/>
      </effectiveTime>
    </serviceEvent>
  </documentationOf>
  <component>
    <structuredBody>
      <#if ehr_allergies?has_content>
        <#include "allergies.ftl">
      <#else>
        <#include "allergies_no_current.ftl" parse=false>
      </#if>
      <#if ehr_medications?has_content>
        <#include "medications.ftl">
      <#else>
        <#include "medications_no_current.ftl" parse=false>
      </#if>
      <#if ehr_results?has_content>
        <#include "results.ftl">
      <#else>
        <#include "results_no_current.ftl" parse=false>
      </#if>
      <#if ehr_conditions?has_content>
        <#include "conditions.ftl">
      <#else>
        <#include "conditions_no_current.ftl" parse=false>
      </#if>
      <#if ehr_procedures?has_content>
        <#include "procedures.ftl">
      <#else>
        <#include "procedures_no_current.ftl" parse=false>
      </#if>
      <#if ehr_encounters?has_content>
        <#include "encounters.ftl">
      </#if>
      <#if ehr_vital_signs?has_content>
        <#include "vital_signs.ftl">
      <#else>
        <#include "vital_signs_no_current.ftl" parse=false>
      </#if>
      <#if ehr_immunizations?has_content>
        <#include "immunizations.ftl">
      </#if>
      <#if ehr_careplans?has_content>
        <#include "care_goals.ftl">
      </#if>
      <#if ehr_social_history?has_content || ehr_smoking_history?has_content>
        <#include "social_history.ftl">
      <#else>
        <#include "social_history_no_current.ftl">
      </#if>
      <#if ehr_medical_equipment?has_content>
        <#include "medical_equipment.ftl">
      </#if>
      <#if ehr_functional_statuses?has_content>
        <#include "functional_status.ftl">
      </#if>

      <!-- 🔧 Fix for Issue #570 -->
      <component>
        <section>
          <title>Explanation Of Benefit</title>
          <text>
            <resourceType>${ehr_explanationofbenefit.resourceType}</resourceType>
          </text>
        </section>
      </component>

    </structuredBody>
  </component>
</ClinicalDocument>
