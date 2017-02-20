module Synthea
  module Output
    module TextRecord
      def self.convert_to_text(entity, end_time = Time.now)
        synthea_record = entity.record_synthea
        text_record = []
        text_record << basic_info(entity, '', end_time)

        # Allergies
        text_record << 'ALLERGIES:'
        synthea_record.conditions.reverse.each do |item|
          condition(item, text_record, nil, nil) if item['fhir'] == :allergy
        end
        breakline(text_record)

        # Medications
        text_record << 'MEDICATIONS:'
        synthea_record.medications.reverse.each do |item|
          medications(item, text_record, nil, nil)
        end
        breakline(text_record)

        # Conditions
        text_record << 'CONDITIONS:'
        synthea_record.conditions.reverse.each do |item|
          condition(item, text_record, nil, nil) if item['fhir'] == :condition
        end
        breakline(text_record)

        # Care Plans
        text_record << 'CARE PLANS:'
        synthea_record.careplans.reverse.each do |item|
          careplans(item, text_record, nil, nil)
        end
        breakline(text_record)

        # Observations
        text_record << 'OBSERVATIONS:'
        if synthea_record.observations && !synthea_record.observations.empty?
          latest = synthea_record.observations.last['time']
          synthea_record.observations.each do |item|
            observation(item, text_record, nil, nil) if item['time'] == latest
          end
        end
        breakline(text_record)

        # Procedures
        text_record << 'PROCEDURES:'
        synthea_record.procedures.reverse.each do |item|
          procedure(item, text_record, nil, nil)
        end
        breakline(text_record)

        # Immunizations
        text_record << 'IMMUNIZATIONS:'
        synthea_record.immunizations.reverse.each do |item|
          immunization(item, text_record, nil, nil)
        end
        breakline(text_record)

        # Encounters
        text_record << 'ENCOUNTERS:'
        synthea_record.encounters.reverse.each do |item|
          encounter(item, text_record, nil)
        end
        breakline(text_record)
        text_record.join("\n")
      end

      def self.breakline(text_record)
        text_record << '-' * 80
      end

      def self.basic_info(entity, text_record, end_time = Time.now)
        name = "#{entity[:name_first]} #{entity[:name_last]}\n"
        text_record << name
        text_record << name.gsub(/[A-Za-z0-9 ]/, '=')
        if entity[:race] == :hispanic
          text_record << "Race:           Other\n"
          text_record << "Ethnicity:      #{entity[:ethnicity].to_s.tr('_', ' ').split(' ').map(&:capitalize).join(' ')}\n"
        else
          text_record << "Race:           #{entity[:race].capitalize}\n"
          text_record << "Ethnicity:      Non-Hispanic\n"
        end
        text_record << "Gender:         #{entity[:gender]}\n"
        if entity.alive?(end_time)
          age = Synthea::Modules::Lifecycle.age(end_time, entity.event(:birth).time, nil)
        else
          age = 'DECEASED'
        end
        text_record << "Age:            #{age}\n"
        text_record << "Birth Date:     #{entity.event(:birth).time.strftime('%Y-%m-%d')}\n"
        text_record << "Marital Status: #{entity[:marital_status]}\n"
        breakline(text_record)
      end

      def self.condition(condition, text_record, _patient, _encounter)
        condition_data = COND_LOOKUP[condition['type']]
        start = condition['time'].strftime('%Y-%m-%d')
        stop = if condition['end_time']
                 condition['end_time'].strftime('%Y-%m-%d')
               else
                 '          '
               end
        text_record << "#{start} - #{stop} : #{condition_data[:description]}"
        text_record
      end

      def self.encounter(encounter, text_record, _patient)
        encounter_data = ENCOUNTER_LOOKUP[encounter['type']]
        reason_data = COND_LOOKUP[encounter['reason']] if encounter['reason']
        if reason_data
          text_record << "#{encounter['time'].strftime('%Y-%m-%d')} : Encounter for #{reason_data[:description]}"
        else
          text_record << "#{encounter['time'].strftime('%Y-%m-%d')} : #{encounter_data[:description]}"
        end
        text_record
      end

      def self.observation(observation, text_record, _patient, _encounter)
        obs_data = OBS_LOOKUP[observation['type']]
        if obs_data[:value_type] == 'condition'
          condition_data = COND_LOOKUP[observation['value']]
          obs_value = condition_data[:description]
        else
          obs_value = observation['value']
          obs_value = obs_value.round(1) if obs_value.is_a?(Numeric)
          obs_value = "#{obs_value} #{obs_data[:unit]}"
        end
        text_record << "#{observation['time'].strftime('%Y-%m-%d')} : #{obs_data[:description].ljust(40)} #{obs_value}"
        text_record
      end

      def self.multi_observation(_multi_obs, _text_record, _patient, _encounter)
        # do nothing
      end

      def self.diagnostic_report(_diagnostic_report, _text_record, _patient, _encounter)
        # do nothing
      end

      def self.procedure(procedure, text_record, _patient, _encounter)
        proc_data = PROCEDURE_LOOKUP[procedure['type']]
        if procedure['reason']
          reason_description = COND_LOOKUP[procedure['reason']][:description]
          text_record << "#{procedure['time'].strftime('%Y-%m-%d')} : #{proc_data[:description]} for #{reason_description}"
        else
          text_record << "#{procedure['time'].strftime('%Y-%m-%d')} : #{proc_data[:description]}"
        end
        text_record
      end

      def self.immunization(imm, text_record, _patient, _encounter)
        text_record << "#{imm['time'].strftime('%Y-%m-%d')} : #{IMM_SCHEDULE[imm['type']][:code]['display']}"
      end

      def self.careplans(plan, text_record, _patient, _encounter)
        careplan_data = CAREPLAN_LOOKUP[plan['type']]
        status = if plan['stop']
                   'STOPPED'
                 else
                   'CURRENT'
                 end
        text_record << "#{plan['start_time'].strftime('%Y-%m-%d')} [#{status}] : #{careplan_data[:description]}"
        plan['reasons'].each do |reason|
          reason_description = COND_LOOKUP[reason][:description]
          text_record << "#{' ' * 25}Reason: #{reason_description}"
        end
        plan['activities'].each do |activity|
          activity_data = CAREPLAN_LOOKUP[activity]
          text_record << "#{' ' * 25}Activity: #{activity_data[:description]}"
        end
        text_record
      end

      def self.medications(prescription, text_record, _patient, _encounter)
        med_data = MEDICATION_LOOKUP[prescription['type']]
        status = if prescription['stop']
                   'STOPPED'
                 else
                   'CURRENT'
                 end
        reason_description = nil
        prescription['reasons'].each do |reason|
          reason_description = COND_LOOKUP[reason][:description]
        end
        if reason_description
          text_record << "#{prescription['start_time'].strftime('%Y-%m-%d')} [#{status}] : #{med_data[:description]} for #{reason_description}"
        else
          text_record << "#{prescription['start_time'].strftime('%Y-%m-%d')} [#{status}] : #{med_data[:description]}"
        end
        text_record
      end
    end
  end
end
