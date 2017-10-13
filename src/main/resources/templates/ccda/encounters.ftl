<#import "narrative_block.ftl" as narrative>
<#import "code_with_reference.ftl" as codes>
<component>
  <!--Encounters-->
  <section>
    <templateId root="2.16.840.1.113883.10.20.22.2.22" extension="2015-08-01"/> <!-- CCDA Template id -->
    <!--Encounters section template-->
    <code code="46240-8" codeSystem="2.16.840.1.113883.6.1" codeSystemName="LOINC" displayName="History of encounters"/>
    <title>Encounters</title>
    <@narrative.narrative entries=ehr_encounters section="encounters"/>
    <#list ehr_encounters as entry>
    <entry typeCode="DRIV">
      <encounter classCode="ENC" moodCode="EVN">
		    <templateId root="2.16.840.1.113883.10.20.22.4.49"/>
        <!-- Encounter activity template -->
        <id root="${UUID?api.toString()}"/>
        <@codes.code_section codes=entry.codes section="encounters" counter=entry?counter />
        <text>
          <reference value="#encounters-desc-${entry?counter}"/>
        </text>
        <effectiveTime>
          <low value="${entry.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
          <#if entry.stop != 0><high value="${entry.stop?number_to_date?string["yyyyMMddHHmmss"]}"/></#if>
        </effectiveTime>
      </encounter>
    </entry>
    </#list>
  </section>
</component>
