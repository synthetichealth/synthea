<#import "narrative_block.ftl" as narrative>
<#import "code_with_reference.ftl" as codes>
<component>
  <!--Medical Equipment - Not C32-->
  <section>
    <templateId root="2.16.840.1.113883.10.20.22.2.23"  extension="2014-06-09"/>
    <!-- Medical equipment section template -->
    <code code="46264-8" codeSystem="2.16.840.1.113883.6.1"/>
    <title>Medical Equipment</title>
    <@narrative.narrative entries=ehr_medical_equipment section="medical_equipment"/>
    <#list ehr_medical_equipment as entry>
    <entry typeCode="DRIV">
      <supply classCode="SPLY" moodCode="EVN">
        <templateId root="2.16.840.1.113883.10.20.22.4.50"/>
        <!-- Supply activity template -->
        <id root="${UUID?api.toString()}"/>
        <statusCode code="completed"/>
        <effectiveTime value="${entry.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
        <participant typeCode="DEV">
          <participantRole classCode="MANU">
            <templateId root="2.16.840.1.113883.10.20.22.4.37"/>
            <!-- Product instance template -->
            <addr nullFlavor="NA" />
            <telecom nullFlavor="NA" />
            <playingDevice>
            <@codes.code_section codes=entry.codes section="medical_equipment" counter=entry?counter />
            </playingDevice>
            <#if entry.manufacturer??>
            <scopingEntity>
              <id root="${UUID?api.toString()}"/>
              <desc>${entry.manufacturer}</desc>
            </scopingEntity>
            </#if>
          </participantRole>
        </participant>
      </supply>
    </entry>
    </#list>
  </section>
</component>
