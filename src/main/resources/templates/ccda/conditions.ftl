<#import "narrative_block.ftl" as narrative>
<#import "code_with_reference.ftl" as codes>
<component>
  <!--Problems-->
  <section>
    <templateId root="2.16.840.1.113883.10.20.22.2.5.1" extension="2015-08-01"/>
    <!--Problems section template-->
    <code code="11450-4" codeSystem="2.16.840.1.113883.6.1" codeSystemName="LOINC" displayName="Problem list"/>
    <title>Problems</title>
    <@narrative.narrative entries=ehr_conditions section="conditions"/>
    <#list ehr_conditions as entry>
    <entry typeCode="DRIV">
      <act classCode="ACT" moodCode="EVN">
        <templateId root="2.16.840.1.113883.10.20.22.4.3" extension="2015-08-01"/>
        <!-- Problem act template -->
        <id root="${UUID?api.toString()}"/>
        <code nullFlavor="NA"/>
        <#if entry.stop != 0>
        <statusCode code="completed"/>
        <#else>
        <statusCode code="active"/>
        </#if>
        <effectiveTime>
          <low value="${entry.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
          <#if entry.stop != 0><high value="${entry.stop?number_to_date?string["yyyyMMddHHmmss"]}"/></#if>
        </effectiveTime>
        <entryRelationship typeCode="SUBJ" inversionInd="false">
          <observation classCode="OBS" moodCode="EVN">
            <templateId root="2.16.840.1.113883.10.20.22.4.4" extension="2015-08-01"/>
            <!--Problem observation template - NOT episode template-->
            <id root="${UUID?api.toString()}"/>
            <code code="64572001" displayName="Condition" codeSystem="2.16.840.1.113883.6.96" codeSystemName="SNOMED-CT">
              <translation code="75323-6" displayName="Condition" codeSystem="2.16.840.1.113883.6.1" codeSystemName="LOINC"/>
            </code>
            <text>
              <reference value="#conditions-desc-${entry?counter}"/>
            </text>
            <statusCode code="completed"/>
            <effectiveTime>
              <low value="${entry.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
              <#if entry.stop != 0><high value="${entry.stop?number_to_date?string["yyyyMMddHHmmss"]}"/></#if>
            </effectiveTime>
            <priorityCode code="8319008" codeSystem="2.16.840.1.113883.6.96" displayName="Principal diagnosis" />
            <@codes.code_section codes=entry.codes section="conditions" counter=entry?counter tag="value" extra="xsi:type=\"CD\""/>
          </observation>
        </entryRelationship>
      </act>
    </entry>
    </#list>
  </section>
</component>
