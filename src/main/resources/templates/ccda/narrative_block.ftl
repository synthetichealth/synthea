<#macro narrative section entries>
<text>
  <table border="1" width="100%">
    <thead>
      <tr>
        <th>Start</th>
        <th>Stop</th>
        <th>Description</th>
        <th>Code</th>
        <#if entries[0].value??>
        <th>Value</th>
        </#if>
      </tr>
    </thead>
    <tbody>
      <#list entries as entry>
        <tr>
          <td>${entry.start?number_to_datetime?iso_local}</td>
          <td><#if entry.stop != 0>${entry.stop?number_to_datetime?iso_local}</#if></td>
          <td ID="${section}-desc-${entry?counter}">${entry.codes[0].display}</td>
          <td ID="${section}-code-${entry?counter}">${entry.codes[0].system} ${entry.codes[0].code}</td>
          <#if entry.value??>
          <td>${entry.value} ${(entry.unit)!""}</td>
          <#elseif entry.category?? && entry.observations?? && entry.observations?has_content>
          <td>
          <#list entry.observations as obs>
          <#if obs.value??>
          ${obs.codes[0].display} ${obs.value?c} ${obs.unit}
          </#if>
          </#list>
          </td>
          </#if>
        </tr>
      </#list>
    </tbody>
  </table>
</text>
</#macro>
