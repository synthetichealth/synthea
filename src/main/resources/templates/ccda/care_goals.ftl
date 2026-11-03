<#import "code_oid_lookup.ftl" as lookup>
<#import "code_with_reference.ftl" as codes>
<component>
  <!--Plan of Care-->
  <section>
    <templateId root="2.16.840.1.113883.10.20.22.2.10"/>
    <templateId root="2.16.840.1.113883.10.20.22.2.10" extension="2014-06-09"/>
    <!--Plan of Care section template-->
    <code code="18776-5" codeSystem="2.16.840.1.113883.6.1" codeSystemName="LOINC" displayName="Treatment plan"/>
    <title>Plan of Care</title>
<text>
  <table border="1" width="100%">
    <thead>
      <tr>
        <th>Start</th>
        <th>Stop</th>
        <th>Description</th>
        <th>Code</th>
      </tr>
    </thead>
    <tbody>
      <#list ehr_careplans as entry>
        <tr>
          <td>${entry.start?number_to_datetime?iso_local}</td>
          <td><#if entry.stop != 0>${entry.stop?number_to_datetime?iso_local}</#if></td>
          <td ID="careplans-desc-${entry?counter}">${entry.codes[0].display}</td>
          <td ID="careplans-code-${entry?counter}">${entry.codes[0].system} ${entry.codes[0].code}</td>
        </tr>
      </#list>
    </tbody>
  </table>
  <#assign has_details = false>
  <#list ehr_careplans as entry>
    <#if (entry.stop == 0 || entry.stop gt time) && (entry.activities?has_content || ehr_careplan_goals[entry?index]?has_content)>
      <#assign has_details = true>
    </#if>
  </#list>
  <#if has_details>
  <table border="1" width="100%">
    <thead>
      <tr>
        <th>Care Plan</th>
        <th>Type</th>
        <th>Description</th>
      </tr>
    </thead>
    <tbody>
      <#list ehr_careplans as entry>
        <#if entry.stop == 0 || entry.stop gt time>
          <#list ehr_careplan_goals[entry?index] as goal>
        <tr>
          <td>${entry.codes[0].display}</td>
          <td>Goal</td>
          <td ID="careplans-goal-${entry?counter}-${goal?counter}">${goal.text?xml}</td>
        </tr>
          </#list>
          <#list entry.activities as activity>
        <tr>
          <td>${entry.codes[0].display}</td>
          <td>Activity</td>
          <td ID="careplans-activity-${entry?counter}-${activity?counter}">${activity.display?xml}</td>
        </tr>
          </#list>
        </#if>
      </#list>
    </tbody>
  </table>
  </#if>
</text>
    <#list ehr_careplans as entry>
    <#if entry.stop == 0 || entry.stop gt time>
    <#assign careplan_counter = entry?counter>
    <#list ehr_careplan_goals[entry?index] as goal>
    <entry>
      <observation classCode="OBS" moodCode="GOL">
        <!-- Goal Observation template -->
        <templateId root="2.16.840.1.113883.10.20.22.4.121"/>
        <id root="${goal.uuid}"/>
        <#if goal.code??>
        <code code="${goal.code.code}" codeSystem="<@lookup.oid_for_code_system system=goal.code.system/>" displayName="${goal.code.display?xml}">
          <originalText><reference value="#careplans-goal-${careplan_counter}-${goal?counter}"/></originalText>
        </code>
        <#else>
        <code nullFlavor="OTH">
          <originalText><reference value="#careplans-goal-${careplan_counter}-${goal?counter}"/></originalText>
        </code>
        </#if>
        <text>
          <reference value="#careplans-goal-${careplan_counter}-${goal?counter}"/>
        </text>
        <statusCode code="active"/>
        <effectiveTime value="${entry.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
      </observation>
    </entry>
    </#list>
    <entry>
      <act classCode="ACT" moodCode="INT">
        <!-- Planned Act (V2) template -->
        <templateId root="2.16.840.1.113883.10.20.22.4.39"/>
        <templateId root="2.16.840.1.113883.10.20.22.4.39" extension="2014-06-09"/>
        <id root="${entry.uuid}"/>
        <@codes.code_section codes=entry.codes section="careplans" counter=entry?counter />
        <text>
          <reference value="#careplans-desc-${entry?counter}"/>
        </text>
        <statusCode code="active"/>
        <effectiveTime>
          <low value="${entry.start?number_to_date?string["yyyyMMddHHmmss"]}"/>
        </effectiveTime>
        <#list entry.activities as activity>
        <entryRelationship typeCode="COMP">
          <procedure classCode="PROC" moodCode="INT">
            <!-- Planned Procedure (V2) template -->
            <templateId root="2.16.840.1.113883.10.20.22.4.41"/>
            <templateId root="2.16.840.1.113883.10.20.22.4.41" extension="2014-06-09"/>
            <id root="${UUID?api.toString()}"/>
            <code code="${activity.code}" codeSystem="<@lookup.oid_for_code_system system=activity.system/>" displayName="${activity.display?xml}">
              <originalText><reference value="#careplans-activity-${entry?counter}-${activity?counter}"/></originalText>
            </code>
            <text>
              <reference value="#careplans-activity-${entry?counter}-${activity?counter}"/>
            </text>
            <statusCode code="active"/>
          </procedure>
        </entryRelationship>
        </#list>
        <#list ehr_careplan_goals[entry?index] as goal>
        <entryRelationship typeCode="RSON">
          <act classCode="ACT" moodCode="EVN">
            <!-- Entry Reference template, pointing at the Goal Observation above -->
            <templateId root="2.16.840.1.113883.10.20.22.4.122"/>
            <id root="${goal.uuid}"/>
            <code nullFlavor="NP"/>
            <statusCode code="completed"/>
          </act>
        </entryRelationship>
        </#list>
      </act>
    </entry>
    </#if>
    </#list>
  </section>
</component>
