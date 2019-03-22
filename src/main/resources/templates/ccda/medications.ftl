<#import "narrative_block.ftl" as narrative>
<#import "code_with_reference.ftl" as codes>
<component>
  <!--Medications-->
  <section>
	<templateId root="2.16.840.1.113883.10.20.22.2.1.1" extension="2014-06-09"/>
    <code code="10160-0" displayName="History of medication use" codeSystem="2.16.840.1.113883.6.1" codeSystemName="LOINC"/>
    <title>Medications</title>
    <@narrative.narrative entries=ehr_medications section="medications"/>
    <#list ehr_medications as entry>
    <entry>
      <!--CCD Medication activity - Required-->
      <substanceAdministration classCode="SBADM" moodCode="EVN">
        <templateId root="2.16.840.1.113883.10.20.22.4.16"/>
        <templateId root="2.16.840.1.113883.10.20.22.4.16" extension="2014-06-09"/>
        <id root="${UUID?api.toString()}"/>
        <statusCode code="completed"/>
        <effectiveTime xsi:type="IVL_TS">
          <low value="${entry.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
          <#if entry.stop != 0>
          <high value="${entry.stop?number_to_date?string["yyyyMMddHHmmss"]}"/>
          <#else>
          <high nullFlavor="UNK"/>
          </#if>
        </effectiveTime>
        <doseQuantity value="1"/>
        <consumable>
          <!--CCD Product - Required-->
          <manufacturedProduct classCode="MANU">
            <templateId root="2.16.840.1.113883.10.20.22.4.23"/>
            <templateId root="2.16.840.1.113883.10.20.22.4.23" extension="2014-06-09"/>
            <manufacturedMaterial>
              <@codes.code_section codes=entry.codes section="medications" counter=entry?counter />
              <name>${entry.codes[0].display}</name>
            </manufacturedMaterial>
          </manufacturedProduct>
        </consumable>
      </substanceAdministration>
    </entry>
    </#list>
  </section>
</component>
