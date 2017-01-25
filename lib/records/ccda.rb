module Synthea
  module Output
    module CcdaRecord
      def self.convert_to_ccda(entity, end_time = Time.now)
        synthea_record = entity.record_synthea
        indices = { observations: 0, conditions: 0, procedures: 0, immunizations: 0, careplans: 0, medications: 0 }
        ccda_record = ::Record.new
        basic_info(entity, ccda_record, end_time)
        synthea_record.encounters.each do |encounter|
          encounter(encounter, ccda_record)
          [:conditions, :observations, :procedures, :immunizations, :careplans, :medications].each do |attribute|
            entry = synthea_record.send(attribute)[indices[attribute]]
            while entry && entry['time'] <= encounter['time']
              # Exception: blood pressure needs to take two observations as an argument
              method = entry['ccda']
              method = attribute.to_s if method.nil?
              send(method, entry, ccda_record) unless method == :no_action
              indices[attribute] += 1
              entry = synthea_record.send(attribute)[indices[attribute]]
            end
          end
        end
        ccda_record
      end

      def self.basic_info(entity, ccda_record, end_time = Time.now)
        patient = ccda_record
        patient.title = entity[:prefix]
        patient.first = entity[:name_first]
        patient.last = entity[:name_last]
        # no field for suffix
        patient.gender = entity[:gender]
        patient.birthdate = entity.event(:birth).time.to_i

        patient.medical_record_number = entity.record_synthea.patient_info[:uuid]
        patient.medical_record_assigner = 'https://github.com/synthetichealth/synthea'

        patient.addresses << Address.new
        patient.addresses.first.street = entity[:address]['line']
        patient.addresses.first.city = entity[:address]['city']
        patient.addresses.first.state = entity[:address]['state']
        patient.addresses.first.zip = entity[:address]['postalCode']
        patient.addresses.first.use = 'Place of Residence'

        patient.addresses << Address.new
        patient.addresses.last.city = entity[:birth_place]['city']
        patient.addresses.last.state = entity[:birth_place]['state']
        patient.addresses.last.use = 'Place of Birth'

        patient.telecoms << Telecom.new
        patient.telecoms.last.value = entity[:telephone]

        patient.deathdate = nil
        patient.expired = false
        patient.marital_status = { 'name' => entity[:marital_status], 'code' => entity[:marital_status] } if entity[:marital_status]

        # patient.religious_affiliation
        # patient.effective_time
        patient.race = { 'name' => entity[:race].to_s.capitalize, 'code' => RACE_ETHNICITY_CODES[entity[:race]] }
        patient.ethnicity = { 'name' => entity[:ethnicity].to_s.capitalize, 'code' => RACE_ETHNICITY_CODES[entity[:ethnicity]] }

        unless entity.alive?(end_time)
          patient.deathdate = entity.record_synthea.patient_info[:deathdate].to_i
          patient.expired = true
          # TODO: would like to put cause of death on the record, though different IGs seem to provide different templates
          # ex, IHE IG -> "deceased observation"; "Public Health & Emergency Response WG" -> "Cause of Death"
        end
      end

      def self.vital_sign(lab, ccda_record)
        type = lab['type']
        time = lab['time']
        value = lab['value']
        ccda_record.vital_signs << VitalSign.new('codes' => { 'LOINC' => [OBS_LOOKUP[type][:code]] },
                                                 'description' => OBS_LOOKUP[type][:description],
                                                 'start_time' => time.to_i,
                                                 'end_time' => time.to_i,
                                                 # "oid" => "2.16.840.1.113883.3.560.1.5",
                                                 'values' => [{
                                                   '_type' => 'PhysicalQuantityResultValue',
                                                   'scalar' => value,
                                                   'units' => OBS_LOOKUP[type][:unit]
                                                 }])
      end

      def self.condition(condition, ccda_record)
        type = condition['type']
        time = condition['time']

        ccda_condition = {
          'codes' => COND_LOOKUP[type][:codes],
          'description' => COND_LOOKUP[type][:description],
          'start_time' => time.to_i
          # "oid" => "2.16.840.1.113883.3.560.1.2"
        }
        ccda_condition['end_time'] = condition['end_time'] if condition['end_time']
        ccda_record.conditions << Condition.new(ccda_condition)
      end

      def self.encounter(encounter, ccda_record)
        type = encounter['type']
        time = encounter['time']
        reason_data = COND_LOOKUP[encounter['reason']] if encounter['reason']
        # find the ccda condition based on the code
        # encounter.reason is an entry
        reason = ccda_record.conditions.find { |c| c.codes == reason_data[:codes] } if reason_data

        end_time = encounter['end_time'] || encounter['time'] + 15.minutes

        if encounter['discharge']
          discharge = { 'code' => encounter['discharge'].code,
                        'code_system' => '2.16.840.1.113883.6.96' }
        end

        ccda_record.encounters << Encounter.new(
          'codes' => ENCOUNTER_LOOKUP[type][:codes],
          'description' => ENCOUNTER_LOOKUP[type][:description],
          'start_time' => time.to_i,
          'end_time' => end_time.to_i,
          'reason' => reason,
          'discharge_disposition' => discharge
          # "oid" => "2.16.840.1.113883.3.560.1.79"
        )
      end

      def self.immunization(immunization, ccda_record)
        type = immunization['type']
        time = immunization['time']
        ccda_record.immunizations << Immunization.new('codes' => { 'CVX' => [IMM_SCHEDULE[type][:code]['code']] },
                                                      'description' => [IMM_SCHEDULE[type][:code]['display']],
                                                      'time' => time.to_i)
      end

      def self.procedure(procedure, ccda_record)
        type = procedure['type']
        time = procedure['time']
        duration = procedure['duration'] || 15.minutes
        ccda_record.procedures << Procedure.new('codes' => PROCEDURE_LOOKUP[type][:codes],
                                                'description' => PROCEDURE_LOOKUP[type][:description],
                                                'start_time' => time.to_i,
                                                'end_time' => time.to_i + duration)
      end

      def self.careplans(plan, ccda_record)
        type = plan['type']
        time = plan['time']
        entries = [type] + plan['activities']
        entries.each do |entry|
          care_goal = CareGoal.new(
            'codes' => CAREPLAN_LOOKUP[entry][:codes],
            'description' => CAREPLAN_LOOKUP[entry][:description],
            'start_time' => time.to_i,
            'reason' => COND_LOOKUP[plan['reasons'][0]] # some data is lost here b/c HDS does not support multiple reasons.
          )
          if plan['stop']
            care_goal['end_time'] = plan['stop'].to_i
            care_goal.status = 'inactive'
          else
            care_goal.status = 'active'
          end
          ccda_record.care_goals << care_goal
        end
      end

      def self.medications(prescription, ccda_record)
        type = prescription['type']
        time = prescription['time']

        medication = Medication.new(
          'codes' => MEDICATION_LOOKUP[type][:codes],
          'description' => MEDICATION_LOOKUP[type][:description],
          'start_time' => time.to_i,
          'reason' => COND_LOOKUP[prescription['reasons'][0]] # some data is lost here b/c HDS does not support multiple reasons.
        )

        unless prescription['rx_info'].empty?
          rx_info = prescription['rx_info']
          fills = rx_info['refills'] + 1

          # Medication embeds FulfillmentHistory
          fulfillment_history = FulfillmentHistory.new(
            'quantity_dispensed' => rx_info['total_doses'],
            'dispense_date' => time.to_i
          )

          # And OrderInformation
          # Each separate refill is not recorded, only the number of refills and the initial order date
          order_information = OrderInformation.new(
            'order_number' => '1',
            'fills' => fills,
            'quantity_ordered' => rx_info['total_doses'],
            'order_date_time' => time.to_i
          )

          medication.allowed_administrations = rx_info['total_doses'] * fills
          medication.cumulative_medication_duration = rx_info['duration']
          medication.patient_instructions = rx_info['patient_instructions'] # The instruction SNOMED codes are not captured here.
          medication.fulfillment_history = fulfillment_history
          medication.order_information = order_information
        end

        if prescription['stop']
          medication['end_time'] = prescription['stop'].to_i
          medication.status = 'inactive'
        else
          medication.status = 'active'
        end
        ccda_record.medications << medication
      end
    end
  end
end
