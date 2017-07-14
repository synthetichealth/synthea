module Synthea
  module Output
    # rubocop:disable Style/ClassVars
    module CsvRecord
      def self.open_csv_files
        folder = Synthea::Output::Exporter.get_output_folder('csv')
        @@patients = File.open("#{folder}/patients.csv", 'w:UTF-8')
        @@allergies = File.open("#{folder}/allergies.csv", 'w:UTF-8')
        @@medications = File.open("#{folder}/medications.csv", 'w:UTF-8')
        @@conditions = File.open("#{folder}/conditions.csv", 'w:UTF-8')
        @@careplans = File.open("#{folder}/careplans.csv", 'w:UTF-8')
        @@observations = File.open("#{folder}/observations.csv", 'w:UTF-8')
        @@procedures = File.open("#{folder}/procedures.csv", 'w:UTF-8')
        @@immunizations = File.open("#{folder}/immunizations.csv", 'w:UTF-8')
        @@encounters = File.open("#{folder}/encounters.csv", 'w:UTF-8')
        write_csv_headers if Synthea::Config.exporter.csv.export_headers
      end

      def self.write_csv_headers
        @@patients.write("ID,BIRTHDATE,DEATHDATE,SSN,DRIVERS,PASSPORT,PREFIX,FIRST,LAST,SUFFIX,MAIDEN,MARITAL,RACE,ETHNICITY,GENDER,BIRTHPLACE,ADDRESS\n")
        @@allergies.write("START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION\n")
        @@medications.write("START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION\n")
        @@conditions.write("START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION\n")
        @@careplans.write("ID,START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION\n")
        @@observations.write("DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,VALUE,UNITS\n")
        @@procedures.write("DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION\n")
        @@immunizations.write("DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION\n")
        @@encounters.write("ID,DATE,PATIENT,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION\n")
      end

      def self.close_csv_files
        @@patients.close
        @@allergies.close
        @@medications.close
        @@conditions.close
        @@careplans.close
        @@observations.close
        @@procedures.close
        @@immunizations.close
        @@encounters.close
      end

      def self.convert_to_csv(entity, end_time = Time.now)
        synthea_record = entity.record_synthea
        patient_id = SecureRandom.uuid.to_s.strip
        basic_info(patient_id, entity, end_time)

        indices = { observations: 0, conditions: 0, procedures: 0, immunizations: 0, careplans: 0, medications: 0 }
        synthea_record.encounters.each do |encounter|
          encounter_id = encounter(encounter, patient_id)
          encounter_end = encounter['end_time'] || synthea_record.patient_info[:deathdate] || end_time
          # if an encounter doesn't have an end date, either the patient died during the encounter, or they are still in the encounter
          [:conditions, :observations, :procedures, :immunizations, :careplans, :medications].each do |attribute|
            entry = synthea_record.send(attribute)[indices[attribute]]
            while entry && entry['time'] <= encounter_end
              method = entry['fhir']
              method = attribute.to_s if method.nil?
              send(method, entry, patient_id, encounter_id)
              indices[attribute] += 1
              entry = synthea_record.send(attribute)[indices[attribute]]
            end
          end
        end

        # quality of life scores are not associated with a particular encounter and use SNOMED codes, use quality_of_life_observation method to export
        daly_recorded = false
        qaly_recorded = false
        synthea_record.observations.reverse_each do |observation|
          if observation['type'] == :DALY
            quality_of_life_observation(observation, patient_id)
            daly_recorded = true
          end
          if observation['type'] == :QALY
            quality_of_life_observation(observation, patient_id)
            qaly_recorded = true
          end
          break if daly_recorded && qaly_recorded
        end
      end

      def self.clean_column(data)
        if data.is_a?(Hash)
          data.values.join(' ').tr(',', ' ')
        elsif data.is_a?(Time)
          data.strftime('%Y-%m-%d')
        else
          data.to_s.tr(',', ' ')
        end
      end

      def self.basic_info(patient_id, entity, end_time)
        @@patients.write("#{patient_id},")
        @@patients.write(clean_column(entity.event(:birth).time))
        if entity.alive?(end_time)
          @@patients.write(',')
        else
          @@patients.write(",#{clean_column(entity.record_synthea.patient_info[:deathdate])}")
        end
        columns = [
          :identifier_ssn, :identifier_drivers, :identifier_passport,
          :name_prefix, :name_first, :name_last, :name_suffix, :name_maiden, :marital_status,
          :race, :ethnicity, :gender, :birth_place, :address
        ]
        columns.each do |col|
          @@patients.write(',')
          @@patients.write(clean_column(entity[col]))
        end
        @@patients.write("\n")
      end

      def self.allergy(allergy, patient_id, encounter_id)
        allergy_data = COND_LOOKUP[allergy['type']]
        start = allergy['time'].strftime('%Y-%m-%d')
        stop = allergy['end_time'].strftime('%Y-%m-%d') if allergy['end_time']
        @@allergies.write("#{start},#{stop},#{patient_id},#{encounter_id},#{allergy_data[:codes]['SNOMED-CT'].first},#{clean_column(allergy_data[:description])}\n")
      end

      def self.condition(condition, patient_id, encounter_id)
        condition_data = COND_LOOKUP[condition['type']]
        start = condition['time'].strftime('%Y-%m-%d')
        stop = condition['end_time'].strftime('%Y-%m-%d') if condition['end_time']
        @@conditions.write("#{start},#{stop},#{patient_id},#{encounter_id},#{condition_data[:codes]['SNOMED-CT'].first},#{clean_column(condition_data[:description])}\n")
      end

      def self.encounter(encounter, patient_id)
        encounter_id = SecureRandom.uuid.to_s.strip
        encounter_data = ENCOUNTER_LOOKUP[encounter['type']]
        encounter_code = encounter_data[:codes]['SNOMED-CT'].first
        encounter_desc = clean_column(encounter_data[:description])
        if encounter['reason']
          reason_data = COND_LOOKUP[encounter['reason']]
          reason_code = reason_data[:codes]['SNOMED-CT'].first
          reason_desc = clean_column(reason_data[:description])
        end
        @@encounters.write("#{encounter_id},#{encounter['time'].strftime('%Y-%m-%d')},#{patient_id},#{encounter_code},#{encounter_desc},#{reason_code},#{reason_desc}\n")
        encounter_id
      end

      def self.observation(observation, patient_id, encounter_id)
        # use quality_of_life_observation method, and not this method, for DALY and QALY export
        return if observation['type'] == :DALY || observation['type'] == :QALY

        obs_data = OBS_LOOKUP[observation['type']]
        if obs_data[:value_type] == 'condition'
          condition_data = COND_LOOKUP[observation['value']]
          obs_code = condition_data[:codes]['SNOMED-CT'].first
          obs_desc = clean_column(condition_data[:description])
          obs_value = nil
          obs_unit = nil
        else
          obs_code = obs_data[:code]
          obs_desc = clean_column(obs_data[:description])
          obs_value = observation['value']
          obs_value = obs_value.round(2) if obs_value.is_a?(Numeric)
          obs_unit = obs_data[:unit]
        end
        @@observations.write("#{observation['time'].strftime('%Y-%m-%d')},#{patient_id},#{encounter_id},#{obs_code},#{obs_desc},#{obs_value},#{obs_unit}\n")
      end

      def self.quality_of_life_observation(observation, patient_id)
        qol_data = QOL_CODES[observation['type']]
        qol_code = qol_data[:codes]['SNOMED-CT'][0]
        qol_desc = clean_column(qol_data[:description])
        qol_value = observation['value']
        qol_unit = qol_data[:unit]
        @@observations.write("#{observation['time'].strftime('%Y-%m-%d')},#{patient_id},,#{qol_code},#{qol_desc},#{qol_value},#{qol_unit}\n")
      end

      def self.multi_observation(_multi_obs, _patient_id, _encounter_id)
        # do nothing
      end

      def self.diagnostic_report(_diagnostic_report, _patient_id, _encounter_id)
        # do nothing
      end

      def self.procedure(procedure, patient_id, encounter_id)
        proc_data = PROCEDURE_LOOKUP[procedure['type']]
        proc_code = proc_data[:codes]['SNOMED-CT'].first
        proc_desc = clean_column(proc_data[:description])
        if procedure['reason']
          reason_data = COND_LOOKUP[procedure['reason']]
          reason_code = reason_data[:codes]['SNOMED-CT'].first
          reason_desc = clean_column(reason_data[:description])
        end
        @@procedures.write("#{procedure['time'].strftime('%Y-%m-%d')},#{patient_id},#{encounter_id},#{proc_code},#{proc_desc},#{reason_code},#{reason_desc}\n")
      end

      def self.immunization(imm, patient_id, encounter_id)
        imm_data = IMM_SCHEDULE[imm['type']]
        imm_code = imm_data[:code]['code']
        imm_desc = clean_column(imm_data[:code]['display'])
        @@immunizations.write("#{imm['time'].strftime('%Y-%m-%d')},#{patient_id},#{encounter_id},#{imm_code},#{imm_desc}\n")
      end

      def self.careplans(plan, patient_id, encounter_id)
        careplan_id = SecureRandom.uuid.to_s.strip
        careplan_data = CAREPLAN_LOOKUP[plan['type']]
        careplan_code = careplan_data[:codes]['SNOMED-CT'].first
        careplan_desc = clean_column(careplan_data[:description])
        stop = plan['stop'].strftime('%Y-%m-%d') if plan['stop']
        if plan['reasons'].first
          primary_reason_data = COND_LOOKUP[plan['reasons'].first]
          primary_reason_code = primary_reason_data[:codes]['SNOMED-CT'].first
          primary_reason_desc = clean_column(primary_reason_data[:description])
        end
        @@careplans.write("#{careplan_id},#{plan['start_time'].strftime('%Y-%m-%d')},#{stop},#{patient_id},#{encounter_id},#{careplan_code},#{careplan_desc},#{primary_reason_code},#{primary_reason_desc}\n")
        plan['activities'].each do |activity|
          activity_data = CAREPLAN_LOOKUP[activity]
          activity_code = activity_data[:codes]['SNOMED-CT'].first
          activity_desc = clean_column(activity_data[:description])
          @@careplans.write("#{careplan_id},#{plan['start_time'].strftime('%Y-%m-%d')},#{stop},#{patient_id},#{encounter_id},#{activity_code},#{activity_desc},#{primary_reason_code},#{primary_reason_desc}\n")
        end
      end

      def self.medications(prescription, patient_id, encounter_id)
        med_data = MEDICATION_LOOKUP[prescription['type']]
        med_code = med_data[:codes]['RxNorm'].first
        med_desc = clean_column(med_data[:description])
        stop = prescription['stop'].strftime('%Y-%m-%d') if prescription['stop']
        if prescription['reasons'].first
          primary_reason_data = COND_LOOKUP[prescription['reasons'].first]
          primary_reason_code = primary_reason_data[:codes]['SNOMED-CT'].first
          primary_reason_desc = clean_column(primary_reason_data[:description])
        end
        @@medications.write("#{prescription['start_time'].strftime('%Y-%m-%d')},#{stop},#{patient_id},#{encounter_id},#{med_code},#{med_desc},#{primary_reason_code},#{primary_reason_desc}\n")
      end
    end
    # rubocop:enable Style/ClassVars
  end
end
