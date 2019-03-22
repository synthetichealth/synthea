<#import "narrative_block.ftl" as narrative>
<#import "code_with_reference.ftl" as codes>
<component>
  <!--Social History - CCDA-->
  <section>
    <templateId root="2.16.840.1.113883.10.20.22.2.17"/>
    <templateId root="2.16.840.1.113883.10.20.22.2.17" extension="2015-08-01"/>
    <!-- Social history section template -->
    <code code="29762-2" codeSystem="2.16.840.1.113883.6.1"/>
    <title>Social History</title>
    <#if ehr_social_history?has_content>
    <@narrative.narrative entries=ehr_social_history section="social_history"/>
    <#list ehr_social_history as entry>
    <entry typeCode="DRIV">
      <observation classCode="OBS" moodCode="EVN">
        <templateId root="2.16.840.1.113883.10.20.22.4.38"/>
        <!-- Social history observation template -->
        <id root="${UUID?api.toString()}"/>
        <@codes.code_section codes=entry.codes section="social_history" counter=entry?counter />
        <statusCode code="completed"/>
        <effectiveTime value="${entry.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
      </observation>
    </entry>
    </#list>
    <#else>
    <text>There is no current social history except smoking status.</text>
    </#if>
    <#if ehr_smoking_history?has_content>
    <entry typeCode="DRIV">
      <observation classCode="OBS" moodCode="EVN">
        <templateId root="2.16.840.1.113883.10.20.22.4.78"/>
        <templateId root="2.16.840.1.113883.10.20.22.4.78" extension="2014-06-09"/>
        <!-- Smoking Status Meaningful Use template -->
        <id root="${UUID?api.toString()}"/>
        <code code="72166-2" codeSystem="2.16.840.1.113883.6.1" displayName="Tobacco smoking status NHIS" />
        <statusCode code="completed"/>
        <effectiveTime value="${ehr_smoking_history.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
        <value xsi:type="CD" code="${ehr_smoking_history.value.code}" displayName="${ehr_smoking_history.value.display}" codeSystem="2.16.840.1.113883.6.96"/>
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
                <name>${preferredProviderambulatory.name?replace("&", "&amp;")}</name>
                <telecom nullFlavor="NA"/>
                <addr>
                  <streetAddressLine>${preferredProviderambulatory.address?replace("&", "&amp;")}</streetAddressLine>
                  <city>${preferredProviderambulatory.city}</city>
                  <state>${preferredProviderambulatory.state}</state>
                  <postalCode>${preferredProviderambulatory.zip}</postalCode>
                </addr>
              </representedOrganization>
            </assignedAuthor>
          </author>
      </observation>
    </entry>
    </#if>
  </section>
</component>
