<#import "narrative_block.ftl" as narrative>
<#import "code_with_reference.ftl" as codes>
<#import "code_oid_lookup.ftl" as lookup>
<component>
  <!--Vital Signs-->
  <section>
    <templateId root="2.16.840.1.113883.10.20.22.2.4.1" extension="2015-08-01"/>
    <!--Vital Signs section template-->
    <code code="8716-3" codeSystem="2.16.840.1.113883.6.1" codeSystemName="LOINC" displayName="Vital signs"/>
    <title>Vital Signs</title>
    <@narrative.narrative entries=ehr_observations section="observations"/>
    <entry typeCode="DRIV">
      <organizer classCode="CLUSTER" moodCode="EVN">
        <templateId root="2.16.840.1.113883.10.20.22.4.26" extension="2015-08-01"/>
        <!-- Vital signs organizer template -->
        <id root="${UUID?api.toString()}"/>
        <code code="46680005" codeSystem="2.16.840.1.113883.6.96" displayName="Vital signs" codeSystemName="SNOMED CT">
          <translation code="74728-7" displayName="Vital signs, weight, height, head circumference, oximetry, BMI, and BSA panel" codeSystem="2.16.840.1.113883.6.1" codeSystemName="LOINC" />
        </code>
        <statusCode code="completed"/>
        <effectiveTime value="${time?number_to_date?string["yyyyMMddHHmmss"]}"/>
        <#list ehr_observations as entry>
        <#if entry.value??>
        <component>
          <observation classCode="OBS" moodCode="EVN">
            <templateId root="2.16.840.1.113883.10.20.22.4.27" extension="2014-06-09"/>
            <!-- Result observation template -->
            <id root="${UUID?api.toString()}"/>
            <@codes.code_section codes=entry.codes section="observations" counter=entry?counter />
            <text>
              <reference value="#observations-desc-${entry?counter}"/>
            </text>
            <statusCode code="completed"/>
            <effectiveTime value="${entry.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
            <#if entry.value?is_number>
            <value xsi:type="PQ" value="${entry.value}" <#if entry.unit?? && (entry.unit?length > 0)>unit="${entry.unit}"</#if>/>
            <#elseif entry.value?is_boolean>
            <value xsi:type="BL" value="${entry.value?c}" />
            <#elseif entry.value?is_string>
            <value xsi:type="ST">${(entry.value)!""}</value>
            <#elseif entry.value?is_hash && entry.value.system?? && entry.value.code?? && entry.value.display??>
            <value xsi:type="CD" codeSystem="<@lookup.oid_for_code_system system=entry.value.system/>" code="${entry.value.code}" displayName="${entry.value.display}" />
            </#if>
          </observation>
        </component>
        <#elseif entry.observations?? && entry.observations?has_content>
        <#list entry.observations as obs>
        <component>
          <observation classCode="OBS" moodCode="EVN">
            <templateId root="2.16.840.1.113883.10.20.22.4.27" extension="2014-06-09"/>
            <!-- Result observation template -->
            <id root="${UUID?api.toString()}"/>
            <@codes.code_section codes=obs.codes section="observations" counter=entry?counter />
            <text>
              <reference value="#observations-desc-${entry?counter}"/>
            </text>
            <statusCode code="completed"/>
            <effectiveTime value="${obs.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
            <#if obs.value??>
            <#if obs.value?is_number>
            <value xsi:type="PQ" value="${obs.value}" <#if obs.unit?? && (obs.unit?length > 0)>unit="${obs.unit}"</#if>/>
            <#elseif obs.value?is_boolean>
            <value xsi:type="BL" value="${obs.value?c}" />
            <#elseif obs.value?is_string>
            <value xsi:type="ST">${(obs.value)!""}</value>
            <#elseif obs.value?is_hash && obs.value.system?? && obs.value.code?? && obs.value.display??>
            <value xsi:type="CD" codeSystem="<@lookup.oid_for_code_system system=obs.value.system/>" code="${obs.value.code}" displayName="${entry.value.display}" />
            </#if>
            <#else>
            <value xsi:type="PQ" nullFlavor="UNK"/>
            </#if>
          </observation>
        </component>
        </#list>
        <#else>
        <component>
          <observation classCode="OBS" moodCode="EVN">
            <templateId root="2.16.840.1.113883.10.20.22.4.27" extension="2014-06-09"/>
            <!-- Result observation template -->
            <id root="${UUID?api.toString()}"/>
            <@codes.code_section codes=entry.codes section="observations" counter=entry?counter />
            <text>
              <reference value="#observations-desc-${entry?counter}"/>
            </text>
            <statusCode code="completed"/>
            <effectiveTime value="${entry.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
            <value xsi:type="PQ" nullFlavor="UNK"/>
          </observation>
        </component>
        </#if>
        </#list>
      </organizer>
    </entry>
  </section>
</component>
