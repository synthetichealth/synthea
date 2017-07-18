module Synthea
  module Output
    module FHIRVersionIndependent
      SHR_EXT = 'http://standardhealthrecord.org/fhir/StructureDefinition/'.freeze

      def self.quality_of_life_observation(observation, fhir_record, patient, fhir_package)
        qol_data = QOL_CODES[observation['type']]
        entry = fhir_package::Bundle::Entry.new
        resource_id = SecureRandom.uuid
        entry.fullUrl = "urn:uuid:#{resource_id}"

        entry.resource = fhir_package::Observation.new('id' => resource_id,
                                                       'status' => 'final',
                                                       'code' => {
                                                         'coding' => [{ 'system' => 'http://snomed.info/sct', 'code' => qol_data[:codes]['SNOMED-CT'][0], 'display' => qol_data[:description] }],
                                                         'text' => qol_data[:description]
                                                       },
                                                       'category' => {
                                                         'coding' => [{ 'system' => 'http://hl7.org/fhir/observation-category', 'code' => observation['category'] }]
                                                       },
                                                       'subject' => { 'reference' => patient.fullUrl.to_s },
                                                       'effectiveDateTime' => Synthea::Output::FhirRecord.convert_fhir_date_time(observation['time'], 'time'),
                                                       'issued' => Synthea::Output::FhirRecord.convert_fhir_date_time(observation['time'], 'time'))
        entry.resource.valueQuantity = fhir_package::Quantity.new('value' => observation['value'], 'unit' => qol_data[:unit], 'code' => qol_data[:unit], 'system' => 'http://unitsofmeasure.org/')

        if fhir_package == FHIR && Synthea::Config.exporter.fhir.use_shr_extensions
          entry.resource.meta = FHIR::Meta.new('profile' => ["#{SHR_EXT}shr-observation-Observation"]) # all Observations are Observations
        end

        fhir_record.entry << entry
      end
    end
  end
end
