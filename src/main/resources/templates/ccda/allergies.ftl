<#import "narrative_block.ftl" as narrative>
<#import "code_with_reference.ftl" as codes>
<component>
  <!--Allergies/Reactions-->
  <section>
    <templateId root="2.16.840.1.113883.10.20.22.2.6.1" extension="2015-08-01"/>
    <!--Allergies/Reactions section template-->
    <code code="48765-2" codeSystem="2.16.840.1.113883.6.1" codeSystemName="LOINC" displayName="Allergies"/>
    <title>Allergies and Adverse Reactions</title>
    <@narrative.narrative entries=ehr_allergies section="allergies"/>
    <#list ehr_allergies as entry>
    <entry typeCode="DRIV">
      <act classCode="ACT" moodCode="EVN">
        <templateId root="2.16.840.1.113883.10.20.22.4.30" extension="2015-08-01"/>
        <!--Allergy act template -->
        <id root="${UUID?api.toString()}"/>
        <code nullFlavor="NA"/>
        <statusCode code="active"/>
        <effectiveTime>
          <low value="${entry.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
        </effectiveTime>
        <entryRelationship typeCode="SUBJ" inversionInd="false">
          <observation classCode="OBS" moodCode="EVN">
            <templateId root="2.16.840.1.113883.10.20.22.4.7" extension="2014-06-09"/>
            <id root="${UUID?api.toString()}"/>
            <code code="ASSERTION" codeSystem="2.16.840.1.113883.5.4" />
            <text>
              <reference value="#allergies-desc-${entry?counter}"/>
            </text>
            <statusCode code="completed"/>
            <effectiveTime>
              <low value="${entry.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
            </effectiveTime>
            <#switch entry.allergyType!>
              <#case "allergy">
                <#switch entry.category!>
                  <#case "food">
                    <value xsi:type="CD" code="414285001" displayName="Allergy to food" codeSystem="2.16.840.1.113883.6.96" codeSystemName="SNOMED CT" />
                    <#break>
                  <#case "medication">
                    <value xsi:type="CD" code="416098002" displayName="Allergy to drug" codeSystem="2.16.840.1.113883.6.96" codeSystemName="SNOMED CT" />
                    <#break>
                  <#-- There is no value in the valueset for allergy to environment. Everything gets put in here-->
                  <#default>
                    <value xsi:type="CD" code="419199007" displayName="Allergy to substance" codeSystem="2.16.840.1.113883.6.96" codeSystemName="SNOMED CT" />
                </#switch>
                <#break>
              <#case "intolerance">
                <#switch entry.category!>
                  <#case "food">
                    <value xsi:type="CD" code="235719002" displayName="Intolerance to food" codeSystem="2.16.840.1.113883.6.96" codeSystemName="SNOMED CT" />
                    <#break>
                  <#case "medication">
                    <value xsi:type="CD" code="59037007" displayName="Intolerance to drug" codeSystem="2.16.840.1.113883.6.96" codeSystemName="SNOMED CT" />
                    <#break>
                  <#-- There is no value in the valueset for intolerance to environment. Everything gets put in here-->
                  <#default>
                    <value xsi:type="CD" code="418038007" displayName="Propensity to adverse reactions to substance" codeSystem="2.16.840.1.113883.6.96" codeSystemName="SNOMED CT" />
                </#switch>
                <#break>
              <#default>
              <value xsi:type="CD" code="419199007" displayName="Allergy to substance" codeSystem="2.16.840.1.113883.6.96" codeSystemName="SNOMED CT" />
            </#switch>
            <participant typeCode="CSM">
              <participantRole classCode="MANU">
                <playingEntity classCode="MMAT">
                  <@codes.code_section codes=entry.codes section="allergies" counter=entry?counter />
                  <name>${entry.codes[0].display}</name>
                </playingEntity>
              </participantRole>
            </participant>
          </observation>
        </entryRelationship>
      </act>
    </entry>
    </#list>
  </section>
</component>
