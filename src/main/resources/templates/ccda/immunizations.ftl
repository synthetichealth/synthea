<#import "narrative_block.ftl" as narrative>
<#import "code_with_reference.ftl" as codes>
<component>
  <!--Immunizations-->
  <section>
    <templateId root="2.16.840.1.113883.10.20.22.2.2" extension="2015-08-01"/>
    <!--Immunizations section template-->
    <code code="11369-6" codeSystem="2.16.840.1.113883.6.1" codeSystemName="LOINC" displayName="History of immunizations"/>
    <title>Immunizations</title>
    <@narrative.narrative entries=ehr_immunizations section="immunizations"/>
    <#list ehr_immunizations as entry>
    <entry typeCode="DRIV">
      <substanceAdministration classCode="SBADM" moodCode="EVN" negationInd="false">
        <templateId root="2.16.840.1.113883.10.20.22.4.52"/>
        <!-- Medication activity template -->
        <id root="${UUID?api.toString()}"/>
        <code code='IMMUNIZ' codeSystem='2.16.840.1.113883.5.4' codeSystemName='ActCode'/>
        <text>
          <reference value="#immunizations-desc-${entry?counter}"/>
        </text>
        <statusCode code="completed"/>
        <effectiveTime value="${entry.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
        <doseQuantity nullFlavor="UNK"/>
        <consumable>
          <manufacturedProduct classCode="MANU">
            <templateId root="2.16.840.1.113883.10.20.22.4.54"/>
            <!-- Product template -->
            <manufacturedMaterial>
              <@codes.code_section codes=entry.codes section="immunizations" counter=entry?counter />
              <name>${entry.codes[0].display}</name>
            </manufacturedMaterial>
          </manufacturedProduct>
        </consumable>
      </substanceAdministration>
    </entry>
    </#list>
  </section>
</component>
