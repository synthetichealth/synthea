<#import "narrative_block.ftl" as narrative>
<#import "code_with_reference.ftl" as codes>
<component>
  <!--Surgeries-->
  <section>
    <templateId root="2.16.840.1.113883.10.20.22.2.7.1" extension="2014-06-09"/>
    <!--Surgeries section template-->
    <code code="47519-4" codeSystem="2.16.840.1.113883.6.1" codeSystemName="LOINC" displayName="List of surgeries"/>
    <title>Surgeries</title>
    <@narrative.narrative entries=ehr_procedures section="procedures"/>
    <#list ehr_procedures as entry>
    <entry typeCode="DRIV">
      <procedure classCode="PROC" moodCode="EVN">
        <templateId root="2.16.840.1.113883.10.20.22.4.14"/>
        <!-- Procedure activity template -->
        <id root="${UUID?api.toString()}"/>
        <@codes.code_section codes=entry.codes section="procedures" counter=entry?counter />
        <text>
          <reference value="#procedures-desc-#{entry?counter}"/>
        </text>
        <statusCode code="completed"/>
        <effectiveTime value="${entry.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
      </procedure>
    </entry>
    </#list>
  </section>
</component>
