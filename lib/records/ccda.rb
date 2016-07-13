module Synthea
	module Output
		module CcdaRecord

      def self.convert_to_ccda (entity)
        synthea_record = entity.record_synthea
        indices = {observations: 0, conditions: 0, procedures: 0, immunizations: 0, careplans: 0, medications: 0}
        ccda_record = ::Record.new
        basic_info(entity, ccda_record)
        synthea_record.encounters.each do |encounter|
          encounter(encounter, ccda_record)
          [:conditions, :observations, :procedures, :immunizations, :careplans, :medications].each do |attribute| 
            entry = synthea_record.send(attribute)[indices[attribute]]
            while entry && entry['time'] <= encounter['time'] do
              #Exception: blood pressure needs to take two observations as an argument
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

      def self.basic_info (entity, ccda_record)
        patient = ccda_record
        patient.first = entity[:name_first]
        patient.last = entity[:name_last]
        patient.gender = entity[:gender]
        patient.birthdate = entity.event(:birth).time.to_i

        patient.addresses << Address.new
        patient.addresses.first.street = entity[:address]['line']
        patient.addresses.first.city = entity[:address]['city']
        patient.addresses.first.state = entity[:address]['state']
        patient.addresses.first.zip = entity[:address]['postalCode']

        patient.deathdate = nil
        patient.expired = false

        # patient.religious_affiliation
        # patient.effective_time
        patient.race = { 'name' => entity[:race].to_s.capitalize, 'code' => RACE_ETHNICITY_CODES[ entity[:race] ] }
        patient.ethnicity = { 'name' => entity[:ethnicity].to_s.capitalize, 'code' => RACE_ETHNICITY_CODES[ entity[:ethnicity] ] }
        if !entity[:is_alive]
          patient.deathdate = entity.record_synthea.patient_info[:deathdate].to_i
          patient.expired = true
        end
      end

      def self.vital_sign(lab, ccda_record)
        type = lab['type']
        time = lab['time']
        value = lab['value']
        ccda_record.vital_signs << VitalSign.new({
          "codes" => {'LOINC' => [OBS_LOOKUP[type][:code]]},
          "description" => OBS_LOOKUP[type][:description],
          "start_time" => time.to_i,
          "end_time" => time.to_i,
          # "oid" => "2.16.840.1.113883.3.560.1.5",
          "values" => [{
            "_type" => "PhysicalQuantityResultValue",
            "scalar" => value,
            "units" => OBS_LOOKUP[type][:unit]
          }],
        })
      end

      def self.condition(condition, ccda_record)
        type = condition['type']
        time = condition['time']

        ccda_condition = {
          "codes" => COND_LOOKUP[type][:codes],
          "description" => COND_LOOKUP[type][:description],
          "start_time" => time.to_i
          # "oid" => "2.16.840.1.113883.3.560.1.2"
        }
        ccda_condition["end_time"] = condition['end_time'] if condition['end_time']
        ccda_record.conditions << Condition.new(ccda_condition)
      end

      def self.encounter(encounter, ccda_record)
        type = encounter['type']
        time = encounter['time']
        ccda_record.encounters << Encounter.new({
          "codes" => ENCOUNTER_LOOKUP[type][:codes],
          "description" => ENCOUNTER_LOOKUP[type][:description],
          "start_time" => time.to_i,
          "end_time" => time.to_i + 15.minutes
          # "oid" => "2.16.840.1.113883.3.560.1.79"
        })
      end

      def self.immunization(immunization, ccda_record)
        type = immunization['type']
        time = immunization['time']
        ccda_record.immunizations << Immunization.new({
          "codes" => { "CVX" => [IMM_SCHEDULE[type][:code]["code"]]},
          "description" => [IMM_SCHEDULE[type][:code]["display"]],
          "time" => time.to_i
        })
      end


      def self.procedure(procedure, ccda_record)
        type = procedure['type']
        time = procedure['time']
        ccda_record.procedures << Procedure.new({
          "codes" => PROCEDURE_LOOKUP[type][:codes],
          "description" => PROCEDURE_LOOKUP[type][:description],
          "start_time" => time.to_i,
          "end_time" => time.to_i + 15.minutes,
        })
      end

      def self.careplans(plan, ccda_record)
        type = plan['type']
        time = plan['time']
        entries = [type] + plan['activities']
        entries.each do |entry| 
          care_goal = CareGoal.new({
            'codes' => CAREPLAN_LOOKUP[entry][:codes],
            'description' => CAREPLAN_LOOKUP[entry][:description],
            'start_time' => time.to_i,
            'reason' => COND_LOOKUP[plan['reason']]
          })
          if plan['stop']
            care_goal['end_time'] = plan['stop'].to_i 
            care_goal.status='inactive'
          else
            care_goal.status='active'
          end
          ccda_record.care_goals << care_goal
        end
      end

      def self.medications(prescription, ccda_record)
        type = prescription['type']
        time = prescription['time']
        medication = Medication.new({
          'codes' =>  MEDICATION_LOOKUP[type][:codes],
          'description' => MEDICATION_LOOKUP[type][:description],
          'start_time' => time.to_i,
          'reason' => COND_LOOKUP[prescription['reasons'][0]]
        })
        if prescription['stop']
          medication['end_time'] = prescription['stop'].to_i 
          medication.status='inactive'
        else
          medication.status='active'
        end
        ccda_record.medications << medication
      end
		end
	end
end