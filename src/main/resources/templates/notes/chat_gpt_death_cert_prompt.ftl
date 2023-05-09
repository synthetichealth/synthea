${name?keep_before_last(" ")} is a <#if ehr_ageInYears gt 0>${ehr_ageInYears} year-old<#elseif ehr_ageInMonths gt 0>${ehr_ageInMonths} month-old<#else>newborn</#if> ${ethnicity} ${race} <#if gender=='F'>female<#else>male</#if> died on ${date_of_death?number_to_date?string["yyyy-MM-dd"]}.
During their life, ${name?keep_before_last(" ")} had the following conditions: <#list ehr_activeConditions as display>${display?lower_case}<#sep>, </#list>.
Prior to their death, they experienced <#if ehr_symptoms?has_content><#list ehr_symptoms as symptom>${symptom}<#sep>, </#list> symptoms<#else>no symptoms</#if>.
The cause of death was ${cause_of_death}.
Write a death certificate for this person. Include a scenario that would lead to the cause of death.