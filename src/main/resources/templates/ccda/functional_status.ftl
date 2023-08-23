<#import "narrative_block.ftl" as narrative>
<#import "code_with_reference.ftl" as codes>
<component>
<!--Survey Responses in Functional Status-->
    <section>
        <templateId root="2.16.840.1.113883.10.20.22.2.14" extension="2014-06-09" />
        <templateId root="2.16.840.1.113883.10.20.22.2.14" />
        <!-- Functional Status Section template V2-->
        <code code="47420-5" codeSystem="2.16.840.1.113883.6.1" codeSystemName="LOINC" displayName="Functional Status" />
        <title>Functional Status</title>
        <@narrative.narrative entries=ehr_functional_statuses section="functional-status"/>
        <#list ehr_functional_statuses as entry>
        <entry>
         <observation classCode="OBS" moodCode="EVN">
             <templateId root="2.16.840.1.113883.10.20.22.4.69"/>
             <id root="${entry.uuid}"/>
             <@codes.code_section codes=entry.codes section="functional-status" counter=entry?counter />
             <text>
               <reference value="#functional-status-desc-${entry?counter}"/>
             </text>
             <statusCode code="completed"/>
             <effectiveTime value="${entry.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
             <value xsi:type="INT" value="${entry.value?round}"/>
         </observation>
        </entry>
        </#list>
    </section>
</component>