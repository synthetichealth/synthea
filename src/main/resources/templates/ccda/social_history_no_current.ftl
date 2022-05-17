<component>
  <!--Social History - CCDA-->
  <section>
    <templateId root="2.16.840.1.113883.10.20.22.2.17"/>
    <templateId root="2.16.840.1.113883.10.20.22.2.17" extension="2015-08-01"/>
    <!-- Social history section template -->
    <code code="29762-2" codeSystem="2.16.840.1.113883.6.1"/>
    <title>Social History</title>
    <text>There is no current social history at the time of this document's creation.</text>
    <entry typeCode="DRIV">
      <observation classCode="OBS" moodCode="EVN">
        <templateId root="2.16.840.1.113883.10.20.22.4.78"/>
        <templateId root="2.16.840.1.113883.10.20.22.4.78" extension="2014-06-09"/>
        <!-- Smoking Status Meaningful Use template -->
        <id root="${UUID?api.toString()}"/>
        <code code="72166-2" codeSystem="2.16.840.1.113883.6.1" displayName="Tobacco smoking status NHIS" />
        <statusCode code="completed"/>
        <effectiveTime value="${time?number_to_date?string["yyyyMMddHHmmss"]}"/>
        <value xsi:type="CD" code="266927001" displayName="Unknown if ever smoked" codeSystem="2.16.840.1.113883.6.96"/>
      </observation>
    </entry>
  </section>
</component>
