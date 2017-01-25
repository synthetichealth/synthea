module Synthea
  module Output
    class Record
      attr_accessor :patient_info, :encounters, :observations, :conditions, :procedures, :immunizations, :medications, :careplans
      def initialize
        @patient_info = { expired: false, uuid: SecureRandom.uuid }
        @encounters = []
        @observations = []
        @conditions = []
        @procedures = []
        @immunizations = []
        @medications = []
        @careplans = []
      end

      # birth and basic information are stored as person attributes and can be referred to when writing other records.
      # No need to duplicate here.
      def death(time)
        @patient_info[:deathdate] = time
        @patient_info[:expired] = true
      end

      def observation(type, time, value, fhir_method = :observation, ccda_method = :vital_sign)
        @observations << {
          'type' => type,
          'time' => time,
          'value' => value,
          'fhir' => fhir_method,
          'ccda' => ccda_method
        }
      end

      def find_observation(type)
        # Returns the most recent observation found matching the referenced type
        @observations.reverse.find { |o| o['type'] == type }
      end

      def condition(type, time, fhir_method = :condition, ccda_method = :condition)
        @conditions << {
          'type' => type,
          'time' => time,
          'fhir' => fhir_method,
          'ccda' => ccda_method
        }
      end

      def find_condition(type)
        # Returns the most recent condition found matching the referenced type
        @conditions.reverse.find { |c| c['type'] == type }
      end

      def end_condition(type, time)
        cond = find_condition(type)
        cond['end_time'] = time
      end

      def diagnosed_condition?(type)
        # The condition is recorded in the patient's record, and active
        cond = find_condition(type)
        !cond.nil? && cond['end_time'].nil?
      end

      def procedure(type, time, options = {})
        @procedures << {
          'type' => type,
          'time' => time,
          'fhir' => :procedure,
          'ccda' => :procedure
        }.merge(options)
      end

      def find_procedure(type)
        @procedures.reverse.find { |p| p['type'] == type }
      end

      def procedure_performed?(type)
        p = find_procedure(type)
        !p.nil?
      end

      def diagnostic_report(type, time, num_obs, fhir_method = :diagnostic_report, ccda_method = :no_action)
        @observations << {
          'type' => type,
          'time' => time,
          'numObs' => num_obs,
          'fhir' => fhir_method,
          'ccda' => ccda_method
        }
      end

      def encounter(type, time, options = {})
        @encounters << {
          'type' => type,
          'time' => time
        }.merge(options)
      end

      def find_encounter(type)
        @encounters.reverse.find { |e| e['type'] == type }
      end

      def encounter_end(type, time, options = {})
        enc = find_encounter(type)
        if !enc.nil? && enc['end_time'].nil?
          enc.merge(options)
          enc['end_time'] = time
        end
      end

      def immunization(imm, time, fhir_method = :immunization, ccda_method = :immunization)
        @immunizations << {
          'type' => imm,
          'time' => time,
          'fhir' => fhir_method,
          'ccda' => ccda_method
        }
      end

      def medication_start(type, time, reasons, rx_info = {})
        med = {
          'type' => type,
          'time' => time,
          'start_time' => time,
          'reasons' => reasons # an array of reasons
        }
        med['rx_info'] = rx_info
        @medications << med
      end

      def find_medication(type)
        # Returns the most recent medication found matching the referenced type
        @medications.reverse.find { |x| x['type'] == type }
      end

      def active_medication?(type)
        med = find_medication(type)
        !med.nil? && med['stop'].nil?
      end

      def update_med_reasons(type, reasons, update_time)
        prescription = find_medication(type)
        if prescription && prescription['stop'].nil?
          prescription['reasons'] = reasons
          prescription['time'] = update_time
        end
      end

      def medication_stop(type, time, reason)
        prescription = find_medication(type)
        if prescription && prescription['stop'].nil?
          prescription['stop'] = time
          prescription['stop_reason'] = reason
        end
      end

      def careplan_start(type, activities, time, reason)
        @careplans << {
          'type' => type,
          'activities' => activities,
          'time' => time,
          'start_time' => time,
          'reasons' => reason
        }
      end

      def find_careplan(type)
        # Returns the most recent careplan found matching the referenced type
        @careplans.reverse.find { |c| c['type'] == type }
      end

      def active_careplan?(type)
        cp = find_careplan(type)
        !cp.nil? && cp['stop'].nil?
      end

      def careplan_stop(type, time)
        careplan = find_careplan(type)
        careplan['stop'] = time if careplan && careplan['stop'].nil?
      end

      def update_careplan_reasons(type, reasons, update_time)
        careplan = find_careplan(type)
        if careplan && careplan['stop'].nil?
          careplan['reasons'] = reasons
          careplan['time'] = update_time
        end
      end
    end
  end
end
