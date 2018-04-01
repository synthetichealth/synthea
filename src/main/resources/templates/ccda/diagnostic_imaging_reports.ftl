<component>
  <!-- Imaging Studies -->
  <section classCode="DOCSECT" moodCode="EVN">
    <templateId root="2.16.840.1.113883.10.20.6.1.1"/>
    <code code="121181" codeSystem="1.2.840.10008.2.16.4" codeSystemName="DICOM Object Catalog" displayName="DICOM Object Catalog Section"/>
    <title>Imaging Studies</title>
    <#list ehr_imaging_studies as study>
    <entry typeCode="COMP">
      <!-- Study -->
      <act classCode="ACT" moodCode="EVN">
        <templateId root="2.16.840.1.113883.10.20.6.2.6"/>
        <id root="${randomDicomUid(0, 0)}"/>
        <code code="113014" codeSystem="1.2.840.10008.2.16.4" codeSystemName="DICOM Controlled Terminology" displayName="Study"/>
        <effectiveTime value="${study.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
        <!-- Series -->
        <#list study.series as series>
        <entryRelationship typeCode="COMP">
          <act classCode="ACT" moodCode="EVN">
            <templateId root="2.16.840.1.113883.10.20.22.4.63"/>
            <id root="${randomDicomUid(series?counter, 0)}"/>
            <code code="113015" codeSystem="1.2.840.10008.2.16.4" codeSystemName="DICOM Controlled Terminology" displayName="Series">
            	<qualifier>
                <name code="121139" codeSystem="1.2.840.10008.2.16.4" codeSystemName="DICOM Controlled Terminology" displayName="Modality"></name>
                <value code="${series.modality.code}" codeSystem="1.2.840.10008.2.16.4" codeSystemName="DICOM Controlled Terminology" displayName="${series.modality.display}"></value>
            	</qualifier>
            </code>
            <!-- Instances -->
            <#list series.instances as instance>
            <entryRelationship typeCode="COMP">
              <observation classCode="DGIMG" moodCode="EVN">
                <templateId root="2.16.840.1.113883.10.20.6.2.8"/>
                <id root="${randomDicomUid(series?counter, instance?counter)}"/>
                <code code="${instance.sopClass.code}" codeSystem="1.2.840.10008.2.6.1" codeSystemName="DCMUID" displayName="${instance.sopClass.display}"></code>
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
