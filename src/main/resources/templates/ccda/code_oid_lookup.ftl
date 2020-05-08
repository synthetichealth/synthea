<#macro oid_for_code_system system>
<#if system == "LOINC" || system == "http://loinc.org">
2.16.840.1.113883.6.1<#t>
<#elseif system == "SNOMED-CT" || system == "http://snomed.info/sct">
2.16.840.1.113883.6.96<#t>
<#elseif system == "CPT">
2.16.840.1.113883.6.12<#t>
<#elseif system == "RxNorm">
2.16.840.1.113883.6.88<#t>
<#elseif system == "ICD-9-CM">
2.16.840.1.113883.6.103<#t>
<#elseif system == "ICD-9-PCS">
2.16.840.1.113883.6.104<#t>
<#elseif system == "ICD-10-PCS">
2.16.840.1.113883.6.4<#t>
<#elseif system == "ICD-10-CM">
2.16.840.1.113883.6.90<#t>
<#elseif system == "HCP">
2.16.840.1.113883.6.14<#t>
<#elseif system == "HCPCS">
2.16.840.1.113883.6.285<#t>
<#elseif system == "HL7 Marital Status">
2.16.840.1.113883.5.2<#t>
<#elseif system == "CVX" || system == "http://hl7.org/fhir/sid/cvx">
2.16.840.1.113883.12.292<#t>
<#elseif system == "HITSP C80 Observation Status">
2.16.840.1.113883.5.83<#t>
<#elseif system == "NCI Thesaurus">
2.16.840.1.113883.3.26.1.1<#t>
<#elseif system == "FDA">
2.16.840.1.113883.3.88.12.80.20<#t>
<#elseif system == "UNII">
2.16.840.1.113883.4.9<#t>
<#elseif system == "NDC">
2.16.840.1.113883.6.69<#t>
<#elseif system == "HL7 ActStatus">
2.16.840.1.113883.5.14<#t>
<#elseif system == "HL7 Healthcare Service Location">
2.16.840.1.113883.6.259<#t>
<#elseif system == "DischargeDisposition">
2.16.840.1.113883.12.112<#t>
<#elseif system == "HL7 Act Code">
2.16.840.1.113883.5.4<#t>
<#elseif system == "HL7 Relationship Code">
2.16.840.1.113883.1.11.18877<#t>
<#elseif system == "CDC Race">
2.16.840.1.113883.6.238<#t>
<#elseif system == "NLM MeSH">
2.16.840.1.113883.6.177<#t>
<#elseif system == "Religious Affiliation">
2.16.840.1.113883.5.1076<#t>
<#elseif system == "HL7 ActNoImmunicationReason">
2.16.840.1.113883.1.11.19717<#t>
<#elseif system == "NUBC">
2.16.840.1.113883.3.88.12.80.33<#t>
<#elseif system == "HL7 Observation Interpretation">
2.16.840.1.113883.1.11.78<#t>
<#elseif system == "Source of Payment Typology">
2.16.840.1.113883.3.221.5<#t>
<#elseif system == "CDT">
2.16.840.1.113883.6.13<#t>
<#elseif system == "AdministrativeSex">
2.16.840.1.113883.18.2<#t>
<#else>
2.16.840.1.113883.19<#t>
</#if>
</#macro>
