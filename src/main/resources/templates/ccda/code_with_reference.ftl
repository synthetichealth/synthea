<#import "code_oid_lookup.ftl" as lookup>
<#macro code_section codes section counter tag="code" extra="">
<#if codes?has_content>
        <${tag} ${extra} code="${codes[0].code}" codeSystem="<@lookup.oid_for_code_system system=codes[0].system/>" displayName="${codes[0].display}">
          <originalText><reference value="#${section}-desc-#{counter}"/></originalText>
          <#list codes as code>
          <translation code="${code.code}" codeSystem="<@lookup.oid_for_code_system system=code.system/>" displayName="${code.display}"/>
          </#list>
        </${tag}>
<#else>
        <${tag} nullFlavor="UNK"/>
</#if>
</#macro>
