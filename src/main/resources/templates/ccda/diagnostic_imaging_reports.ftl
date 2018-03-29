<component>
  <!--Imaging Studies-->
  <section classCode="DOCSECT" moodCode="EVN">
    <templateId root="2.16.840.1.113883.10.20.22.1.5"/>
    <code code="18748-4" codeSystem="2.16.840.1.113883.6.1" codeSystemName="LOINC" displayName="Diagnostic Imaging Report"/>
    <title>Imaging Studies</title>
    <#list ehr_imaging_studies as study>
    <entry typeCode="COMP">
      <!-- Study -->
      <act classCode="ACT" moodCode="EVN">
        <templateId root="2.16.840.1.113883.10.20.6.2.6"/>
        <id root="${randomDicomUid(0, 0)}"/>
        <code code="113014" codeSystem="1.2.840.10008.2.16.4" codeSystemName="DCM" displayName="Study"/>
        <!-- Series -->
        <#list study.series as series>
        <entryRelationship typeCode="COMP">
          <act classCode="ACT" moodCode="EVN">
            <id root="${randomDicomUid(series?counter, 0)}"/>
            <code code="113015" codeSystem="1.2.840.10008.2.16.4" codeSystemName="DCM" displayName="Series">
							<qualifier>
								<name code="121139" codeSystem="1.2.840.10008.2.16.4" codeSystemName="DCM" displayName="Modality"> </name>
								<value code="${series.modality.code}" codeSystem="1.2.840.10008.2.16.4" codeSystemName="DCM" displayName="${series.modality.display}"> </value>
							</qualifier>
						</code>
            <!-- Instances -->
            <#list series.instances as instance>
            <entryRelationship typeCode="COMP">
              <observation classCode="DGIMG" moodCode="EVN">
                <templateId root="2.16.840.1.113883.10.20.6.2.8"/>
                <id root="${randomDicomUid(series?counter, instance?counter)}"/>
                <code code="${instance.sopClass.code}" codeSystem="1.2.840.10008.2.6.1" codeSystemName="DCMUID" displayName="${instance.sopClass.display}"> </code>
                <effectiveTime value="${study.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
              </observation>
            </entryRelationship>
            </#list>
          </act>
        </entryRelationship>
        </#list>
      </act>
    </entry>
    </#list>
  </section>
</component>
