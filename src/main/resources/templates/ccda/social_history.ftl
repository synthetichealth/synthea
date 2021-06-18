<#import "narrative_block.ftl" as narrative>
<#import "code_with_reference.ftl" as codes>
<component>
  <!--Social History - CCDA-->
  <section>
    <templateId root="2.16.840.1.113883.10.20.22.2.17" extension="2015-08-01"/>
    <!-- Social history section template -->
    <code code="29762-2" codeSystem="2.16.840.1.113883.6.1"/>
    <title>Social History</title>
    <@narrative.narrative entries=ehr_social_history section="social_history"/>
    <#list ehr_social_history as entry>
    <entry typeCode="DRIV">
      <observation classCode="OBS" moodCode="EVN">
        <templateId root="2.16.840.1.113883.10.20.22.4.38" extension="2015-08-01"/>
        <!-- Social history observation template -->
        <id root="${UUID?api.toString()}"/>
        <@codes.code_section codes=entry.codes section="social_history" counter=entry?counter />
        <statusCode code="completed"/>
        <effectiveTime value="${entry.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
      </observation>
    </entry>
    </#list>
  </section>
</component>
