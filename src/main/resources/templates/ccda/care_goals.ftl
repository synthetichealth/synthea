<#import "narrative_block.ftl" as narrative>
<component>
  <!--Plan of Care-->
  <section>
    <templateId root="2.16.840.1.113883.10.20.22.2.10" extension="2014-06-09"/>
    <!--Plan of Care section template-->
    <code code="18776-5" codeSystem="2.16.840.1.113883.6.1" codeSystemName="LOINC" displayName="Treatment plan"/>
    <title>Plan of Care</title>
    <@narrative.narrative entries=ehr_careplans section="careplans"/>
  </section>
</component>
