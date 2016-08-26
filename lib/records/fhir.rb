module Synthea
	module Output
		module FhirRecord

      def self.convert_to_fhir (entity)
        synthea_record = entity.record_synthea
        indices = {observations: 0, conditions: 0, procedures: 0, immunizations: 0, careplans: 0, medications: 0}
        fhir_record = FHIR::Bundle.new
        fhir_record.type = 'collection'
        patient = basic_info(entity, fhir_record)
        synthea_record.encounters.each do |encounter|
           curr_encounter = encounter(encounter, fhir_record, patient)
          [:conditions, :observations, :procedures, :immunizations, :careplans, :medications].each do |attribute| 
            entry = synthea_record.send(attribute)[indices[attribute]]
            while entry && entry['time'] <= encounter['time'] do
              method = entry['fhir']
              method = attribute.to_s if method.nil?
              send(method, entry, fhir_record, patient, curr_encounter)
              indices[attribute] += 1
              entry = synthea_record.send(attribute)[indices[attribute]]
            end
          end
        end
        fhir_record
      end

      def self.basic_info (entity, fhir_record)
        if entity[:race] == :hispanic 
          raceFHIR = :other
          ethnicityFHIR = entity[:ethnicity]
        else 
          raceFHIR = entity[:ethnicity]
          ethnicityFHIR = :nonhispanic
        end
        resourceID = SecureRandom.uuid.to_s.strip
        patientResource = FHIR::Patient.new({
          'id' => resourceID,
          'identifier' => [{
              'system' => 'https://github.com/synthetichealth/synthea',
              'value' => entity.record_synthea.patient_info[:uuid]
            }],
          'name' => [{'given' => [entity[:name_first]],
                      'family' => [entity[:name_last]],
                      'use' => 'official'
                    }],
          'gender' => ('male' if entity[:gender] == 'M') || ('female' if entity[:gender] == 'F'),
          'birthDate' => convertFhirDateTime(entity.event(:birth).time),
          'address' => [FHIR::Address.new(entity[:address])],
          'extension' => [
            #race
            {
              'url' => 'http://hl7.org/fhir/StructureDefinition/us-core-race',
              'valueCodeableConcept' => {
                'text' => 'race',
                'coding' => [{
                  'display'=>raceFHIR.to_s.capitalize,
                  'code'=>RACE_ETHNICITY_CODES[raceFHIR],
                  'system'=>'http://hl7.org/fhir/v3/Race'
                }]
              }
            },
            #ethnicity
            {
              'url' => 'http://hl7.org/fhir/StructureDefinition/us-core-ethnicity',
              'valueCodeableConcept' => {
                'text' => 'ethnicity',
                'coding' => [{
                  'display'=>ethnicityFHIR.to_s.capitalize,
                  'code'=>RACE_ETHNICITY_CODES[ethnicityFHIR],
                  'system'=>'http://hl7.org/fhir/v3/Ethnicity'
                }]
              }
            },
            {
              'url' => 'http://standardhealthrecord.org/fhir/extensions/wkt-geospatialpoint',
              'valueString' => "POINT (#{entity[:coordinates_address].x}, #{entity[:coordinates_address].y})"
            }
          ]
        })
        if !entity[:is_alive]
          patientResource.deceasedDateTime = convertFhirDateTime(entity.record_synthea.patient_info[:deathdate], 'time')
        end
        entry = FHIR::Bundle::Entry.new
        entry.fullUrl = "urn:uuid:#{resourceID}"
        entry.resource = patientResource
        fhir_record.entry << entry
        entry
      end

      def self.condition(condition, fhir_record, patient, encounter)
        resourceID = SecureRandom.uuid
        conditionData = COND_LOOKUP[condition['type']]
        fhir_condition = FHIR::Condition.new({
          'id' => resourceID,
          'subject' => {'reference'=>"#{patient.fullUrl}"},
          'code' => {
            'coding'=>[{
              'code'=> conditionData[:codes]['SNOMED-CT'][0],
              'display'=> conditionData[:description],
              'system' => 'http://snomed.info/sct'
            }],
          },
          'verificationStatus' => 'confirmed',
          'onsetDateTime' => convertFhirDateTime(condition['time'],'time'),
          'context' => {'reference'=>"#{encounter.fullUrl}"}
        })
        if condition['end_time']
          fhir_condition.abatementDateTime = convertFhirDateTime(condition['end_time'], 'time')
        end
        entry = FHIR::Bundle::Entry.new
        entry.fullUrl = "urn:uuid:#{resourceID}"
        entry.resource = fhir_condition
        fhir_record.entry << entry
      end

			def self.encounter(encounter, fhir_record, patient)
        resourceID = SecureRandom.uuid.to_s
        encounterData = ENCOUNTER_LOOKUP[encounter['type']]
        fhir_encounter = FHIR::Encounter.new({
          'id' => resourceID,
          'status' => 'finished',
          'class' => {'code' => encounterData[:class]},
          'type' => [{'coding' => [{'code' => encounterData[:codes]['SNOMED-CT'][0], 'system'=>'http://snomed.info/sct'}], 'text' => encounterData[:description]}],
          'patient' => {'reference'=>"#{patient.fullUrl}"},
          'period' => {'start' => convertFhirDateTime(encounter['time'],'time'), 'end' => convertFhirDateTime(encounter['time']+15.minutes, 'time')}
        })
        entry = FHIR::Bundle::Entry.new
        entry.fullUrl = "urn:uuid:#{resourceID}"
        entry.resource = fhir_encounter
        fhir_record.entry << entry
        entry
      end

      def self.allergy(allergy, fhir_record, patient, encounter)
        snomed_code = COND_LOOKUP[allergy['type']][:codes]['SNOMED-CT'][0]
        allergy = FHIR::AllergyIntolerance.new({
          'attestedDate' => convertFhirDateTime(allergy['time'],'time'),
          'status' => 'active-confirmed',
          'type' => 'allergy',
          'category' => 'food',
          'criticality' => ['low','high'].sample,
          'patient' => {'reference'=>"#{patient.fullUrl}"},
          'code' => {'coding'=>[{
              'code'=>snomed_code,
              'display'=>allergy['type'].to_s.split('food_allergy_')[1],
              'system' => 'http://snomed.info/sct'
              }]}
        })
        entry = FHIR::Bundle::Entry.new
        entry.resource = allergy
        fhir_record.entry << entry
      end

      def self.observation(observation, fhir_record, patient, encounter)
        obs_data = OBS_LOOKUP[observation['type']]
        entry = FHIR::Bundle::Entry.new
        resourceID = SecureRandom.uuid
        entry.fullUrl = "urn:uuid:#{resourceID}"
        entry.resource = FHIR::Observation.new({
          'id' => resourceID,
          'status'=>'final',
          'code'=>{
            'coding'=>[{'system'=>'http://loinc.org','code'=>obs_data[:code],'display'=>obs_data[:description]}]
          },
          'subject'=> { 'reference'=> "#{patient.fullUrl}"},
          'encounter'=> { 'reference'=> "#{encounter.fullUrl}"},
          'effectiveDateTime' => convertFhirDateTime(observation['time'],'time'),
          'valueQuantity'=>{'value'=>observation['value'],'unit'=>obs_data[:unit]}
        })
        fhir_record.entry << entry
      end

      def self.multi_observation(multiObs, fhir_record, patient, encounter)
        entry = FHIR::Bundle::Entry.new
        resourceID = SecureRandom.uuid
        entry.fullUrl = "urn:uuid:#{resourceID}"
        observations = fhir_record.entry.pop(multiObs['value'])
        multi_data = OBS_LOOKUP[multiObs['type']]
        fhir_observation = FHIR::Observation.new({
          'id' => resourceID,
          'status'=>'final',
          'code'=>{
            'coding'=>[{'system'=>'http://loinc.org','code'=>multi_data[:code],'display'=>multi_data[:description]}]
          },
          'subject'=> { 'reference'=> "#{patient.fullUrl}"},
          'encounter'=> { 'reference'=> "#{encounter.fullUrl}"},
          'effectiveDateTime' => convertFhirDateTime(multiObs['time'],'time')
        })
        observations.each do |obs| 
          fhir_observation.component << FHIR::Observation::Component.new({'code' => obs.resource.code.to_hash, 'valueQuantity' => obs.resource.valueQuantity.to_hash})
        end
        entry.resource = fhir_observation
        fhir_record.entry << entry
      end

      def self.diagnostic_report(report, fhir_record, patient, encounter)
        entry = FHIR::Bundle::Entry.new
        resourceID = SecureRandom.uuid
        entry.fullUrl = "urn:uuid:#{resourceID}"
        report_data = OBS_LOOKUP[report['type']]
        entry.resource = FHIR::DiagnosticReport.new({
          'id'=>resourceID,
          'status'=>'final',
          'code'=>{
            'coding'=>[{'system'=>'http://loinc.org','code'=>report_data[:code],'display'=>report_data[:description]}]
          },
          'subject'=> { 'reference'=> "#{patient.fullUrl}"},
          'encounter'=> { 'reference'=> "#{encounter.fullUrl}"},
          'effectiveDateTime' => convertFhirDateTime(report['time'],'time'),
          'issued' => convertFhirDateTime(report['time'],'time'),
          'performer' => [{ 'display' => 'Hospital Lab'}]
        })
        entry.resource.result = []
        obsEntries = fhir_record.entry.last(report['numObs'])
        obsEntries.each do |e|
          entry.resource.result << FHIR::Reference.new({'reference'=>"#{e.fullUrl}",'display'=>e.resource.code.coding.first.display})
        end
        fhir_record.entry << entry
      end

      def self.procedure(procedure, fhir_record, patient, encounter)
        reason = fhir_record.entry.find{|e| e.resource.is_a?(FHIR::Condition) && e.resource.code.coding.find{|c|c.code==procedure['reason']} }
        proc_data = PROCEDURE_LOOKUP[procedure['type']]
        fhir_procedure = FHIR::Procedure.new({
          'subject' => { 'reference' => "#{patient.fullUrl}"},
          'status' => 'completed',
          'code' => { 
            'coding' => [{'code'=>proc_data[:codes]['SNOMED-CT'][0], 'display'=>proc_data[:description], 'system'=>'http://snomed.info/sct'}],
            'text' => proc_data[:description] },
          # 'reasonReference' => { 'reference' => reason.resource.id },
          # 'performer' => { 'reference' => doctor_no_good },
          'performedDateTime' => convertFhirDateTime(procedure['time'],'time'),
          'encounter' => { 'reference' => "#{encounter.fullUrl}" },
        })
        fhir_procedure.reasonReference = FHIR::Reference.new({'reference'=>"#{reason.fullUrl}",'display'=>reason.resource.code.text}) if reason

        entry = FHIR::Bundle::Entry.new
        entry.resource = fhir_procedure
        fhir_record.entry << entry
      end

      def self.immunization(imm, fhir_record, patient, encounter)
        immunization = FHIR::Immunization.new({
          'status'=>'completed',
          'date' => convertFhirDateTime(imm['time'],'time'),
          'vaccineCode'=>{
            'coding'=>[IMM_SCHEDULE[imm['type']][:code]]
          },
          'patient'=> { 'reference'=> "#{patient.fullUrl}"},
          'wasNotGiven' => false,
          'reported' => false,
          'encounter'=> { 'reference'=> "#{encounter.fullUrl}"}
        })
        entry = FHIR::Bundle::Entry.new
        entry.resource = immunization
        fhir_record.entry << entry
      end

      def self.careplans(plan, fhir_record, patient, encounter)
        careplanData = CAREPLAN_LOOKUP[plan['type']]
        reasons = []
        plan['reasons'].each do |reason|
          reasonCode = COND_LOOKUP[reason][:codes]['SNOMED-CT'][0]
          r = fhir_record.entry.find{|e| e.resource.is_a?(FHIR::Condition) && reasonCode == e.resource.code.coding[0].code }
          reasons << r unless r.nil?
        end
        
        careplan = FHIR::CarePlan.new({
          'subject' => {'reference'=> "#{patient.fullUrl}"},
          'context' => {'reference'=> "#{encounter.fullUrl}"},
          'period' => {'start'=>convertFhirDateTime(plan['start_time'])},
          'category' => [{
            'coding'=>[{
              'code'=> careplanData[:codes]['SNOMED-CT'][0],
              'display'=> careplanData[:description],
              'system' => 'http://snomed.info/sct'
            }]
          }],
          'activity' => [],
          'addresses' => [] 
        })
        reasons.each do |r|
          careplan.addresses << FHIR::Reference.new({'reference'=> "#{r.fullUrl}"}) unless reasons.nil? || reasons.empty?
        end
        if plan['stop']
          careplan.period.end = convertFhirDateTime(plan['stop'])
          careplan.status = 'completed'
        else
          careplan.status = 'active'
        end 
        plan['activities'].each do |activity|
          activityData = CAREPLAN_LOOKUP[activity]
          careplan.activity << FHIR::CarePlan::Activity.new({
            'detail'=>{
              'code'=>{
                'coding'=>[{
                  'code'=> activityData[:codes]['SNOMED-CT'][0],
                  'display'=> activityData[:description],
                  'system' => 'http://snomed.info/sct'
                }]
              }
            }
          })
        end
        entry = FHIR::Bundle::Entry.new
        entry.resource = careplan
        fhir_record.entry << entry        
      end

      def self.medications(prescription, fhir_record, patient, encounter)
        medData = MEDICATION_LOOKUP[prescription['type']]
        reasons = []
        prescription['reasons'].each do |reason|
          reasonCode = COND_LOOKUP[reason][:codes]['SNOMED-CT'][0]
          r = fhir_record.entry.find{|e| e.resource.is_a?(FHIR::Condition) && reasonCode == e.resource.code.coding[0].code }
          reasons << r unless r.nil?
        end
        medOrder = FHIR::MedicationOrder.new({
          'medicationCodeableConcept'=>{
            'coding'=>[{
              'code'=> medData[:codes]['RxNorm'][0],
              'display'=> medData[:description],
              'system' => 'http://www.nlm.nih.gov/research/umls/rxnorm'
            }]
          },
          'patient' => {'reference'=> "#{patient.fullUrl}"},
          'encounter' => {'reference'=> "#{encounter.fullUrl}"},
          'dateWritten' => convertFhirDateTime(prescription['start_time']),
          'reasonReference' => [],
          'eventHistory' => []
        })
        reasons.each do |r|
          medOrder.reasonReference << FHIR::Reference.new({'reference'=> "#{r.fullUrl}"})
        end
        if prescription['stop']
          medOrder.status = 'stopped' 

          event = FHIR::MedicationOrder::EventHistory.new({
              'status' => 'stopped',
              'dateTime' => convertFhirDateTime(prescription['stop']),
               })

          reasonData = REASON_LOOKUP[prescription['stop_reason']]
          if reasonData
            event.reason = FHIR::CodeableConcept.new({
              'coding'=>[{
                'code'=> reasonData[:codes]['SNOMED-CT'][0],
                'display'=> reasonData[:description],
                'system' => 'http://snomed.info/sct'
              }]
            })
          end
          medOrder.eventHistory << event
        else
          medOrder.status = 'active'
        end
        entry = FHIR::Bundle::Entry.new
        entry.resource = medOrder
        fhir_record.entry << entry        
      end

      def self.convertFhirDateTime(date, option = nil)
        date = Time.at(date) if date.is_a?(Integer)
        if option == 'time'
          x = date.to_s.sub(' ', 'T')
          x = x.sub(' ', '')
          x = x.insert(-3, ":")
          return Regexp.new(FHIR::PRIMITIVES['dateTime']['regex']).match(x.to_s).to_s
        else
          return Regexp.new(FHIR::PRIMITIVES['date']['regex']).match(date.to_s).to_s
        end
      end
		end
	end
end