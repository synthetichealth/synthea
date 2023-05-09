${name?keep_before_last(" ")} is a <#if ehr_ageInYears gt 0>${ehr_ageInYears} year-old<#elseif ehr_ageInMonths gt 0>${ehr_ageInMonths} month-old<#else>newborn</#if> ${ethnicity} ${race} <#if gender=='F'>female<#else>male</#if> who has a ${ehr_encounter_type} for ${ehr_encounter_reason?lower_case}.
${name?keep_before_last(" ")} is currently complaining of: <#list ehr_symptoms as symptom>${symptom}<#sep>, </#list>.
<#if ehr_activeMedications?has_content>The patient is currently taking the following medications: <#list ehr_activeMedications as medication>${medication?lower_case}<#sep>; </#list>.
<#else>The patient is not currently taking any medications.</#if>
<#if ehr_medications?has_content>The patient was prescribed the following medications: <#list ehr_medications as entry>${entry.codes[0].display?lower_case}<#sep>; </#list></#if>
<#if ehr_procedures?has_content>The doctor performed: <#list ehr_activeProcedures as ap>${ap?lower_case}<#sep>; </#list>.</#if>
<#if ehr_immunizations?has_content>The patient was given the following immunizations: <#list ehr_immunizations as entry>${entry.codes[0].display?lower_case}<#sep>, </#list>. </#if>
Write a medical summary for this doctor's visit. Elaborate on the symptoms and ${ehr_encounter_reason?lower_case} condition.