<#setting number_format="computer">

${time?number_to_date?string["yyyy-MM-dd"]}

# Chief Complaint
<#if ehr_symptoms?has_content>
<#list ehr_symptoms as symptom>
- ${symptom}
</#list><#else>No complaints.</#if>

# History of Present Illness
<#if name??>${name?keep_before_last(" ")}<#else><#if gender=='F'>Jane<#else>John</#if> Doe</#if>
 is a <#if ehr_ageInYears gt 0>${ehr_ageInYears} year-old<#elseif ehr_ageInMonths gt 0>${ehr_ageInMonths} month-old<#else>newborn</#if> ${ethnicity_display_lookup[race]} ${race} <#if gender=='F'>female<#else>male</#if>.<#if ehr_activeConditions?has_content> Patient has a history of <#list ehr_activeConditions as display>${display?lower_case}<#sep>, </#list>.</#if>

# Social History
<#if ehr_ageInYears gt 18 && marital_status??><#if marital_status=='M'>Patient is married.<#else>Patient is single.</#if></#if><#if ehr_ageInYears gt 18 && homeless??> <#if homelessness_category=='chronic'>Patient is chronically homeless.<#else>Patient is temporarily homeless.</#if></#if><#if opioid_addiction_careplan??> Patient has a documented history of opioid addiction.</#if><#if ehr_ageInYears gt 16 && smoker??> Patient is an active smoker<#elseif quit_smoking_age??> Patient quite smoking at age ${quit_smoking_age}<#else> Patient has never smoked</#if><#if alcoholic??> and is an alcoholic.<#else>.</#if>
<#if ehr_ageInYears gte 16 && sexual_orientation??> Patient identifies as ${sexual_orientation}.</#if>

<#if socioeconomic_category??>Patient comes from a ${socioeconomic_category?lower_case} socioeconomic background.</#if>
<#if ehr_ageInYears gte 18 && education??><#if education == 'less_than_hs'> Patient did not finish high school.<#elseif education == 'hs_degree'> Patient has a high school education.<#elseif education == 'some_college'> Patient has completed some college courses.<#elseif education == 'bs_degree'> Patient is a college graduate.</#if></#if>
Patient currently has ${ehr_insurance?replace("_", " ")}.

# Allergies
<#if ehr_activeAllergies?has_content><#list ehr_activeAllergies as allergy>${allergy?lower_case}<#sep>, </#list><#else>No Known Allergies.</#if>

# Medications
<#if ehr_activeMedications?has_content><#list ehr_activeMedications as medication>${medication?lower_case}<#sep>; </#list><#else>No Active Medications.</#if>

# Assessment and Plan
<#if ehr_conditions?has_content>Patient is presenting with <#list ehr_conditions as entry>${entry.codes[0].display?lower_case}<#sep>, </#list>. </#if><#if ehr_allergies?has_content>Patient is presenting with <#list ehr_allergies as entry>${entry.codes[0].display?lower_case}<#sep>, </#list>. </#if>

## Plan
<#if ehr_immunizations?has_content>Patient was given the following immunizations: <#list ehr_immunizations as entry>${entry.codes[0].display?lower_case}<#sep>, </#list>. </#if>
<#if ehr_procedures?has_content>The following procedures were conducted:
<#list ehr_procedures as entry>
- ${entry.codes[0].display?lower_case}
</#list></#if>
<#if ehr_medications?has_content>The patient was prescribed the following medications:
<#list ehr_medications as entry>
- ${entry.codes[0].display?lower_case}
</#list></#if>
<#if ehr_careplans?has_content>The patient was placed on a careplan:
<#list ehr_careplans as entry>
- ${entry.codes[0].display?lower_case}
</#list></#if>
