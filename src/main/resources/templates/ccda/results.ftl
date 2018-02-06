<#import "narrative_block.ftl" as narrative>
<#import "code_with_reference.ftl" as codes>
<component>
  <!--Diagnostic Results-->
  <section>
    <templateId root="2.16.840.1.113883.10.20.22.2.3.1" extension="2015-08-01"/>
    <!--Diagnostic Results section template-->
    <code code="30954-2" codeSystem="2.16.840.1.113883.6.1" codeSystemName="LOINC" displayName="Results"/>
    <title>Diagnostic Results</title>
    <@narrative.narrative entries=ehr_reports section="reports"/>
    <#list ehr_reports as entry>
    <entry typeCode="DRIV">
      <organizer classCode="BATTERY" moodCode="EVN">
        <templateId root="2.16.840.1.113883.10.20.22.4.1" extension="2015-08-01"/>
        <!--Result organizer template -->
        <id root="${UUID?api.toString()}"/>
        <code nullFlavor="NA"/>
        <statusCode code="completed"/>
        <effectiveTime>
          <low value="${entry.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
          <high value="${entry.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
        </effectiveTime>
        <#list entry.observations as obs>
        <component>
          <observation classCode="OBS" moodCode="EVN">
          <templateId root="2.16.840.1.113883.10.20.22.4.2" extension="2015-08-01"/>
            <id root="${UUID?api.toString()}"/>
            <@codes.code_section codes=obs.codes section="reports" counter=entry?counter />
            <text>
              <reference value="#reports-desc-${entry?counter}"/>
            </text>
            <statusCode code="completed"/>
            <effectiveTime value="${obs.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
            <#if obs.value??>
            <#if obs.value?is_number>
            <value xsi:type="PQ" value="${obs.value}" <#if obs.unit??>unit="${obs.unit}"</#if>/>
            <#elseif obs.value?is_boolean>
            <value xsi:type="BL" value="${obs.value}" />
            <#elseif obs.value?is_string>
            <value xsi:type="ST">{entry.value}</value>
            </#if>
            <#else>
            <value xsi:type="PQ" nullFlavor="UNK"/>
            </#if>
          </observation>
        </component>
        </#list>
      </organizer>
    </entry>
    </#list>
  </section>
</component>
