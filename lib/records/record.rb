module Synthea
  module Output
    class Record
      attr_accessor :patient_info, :encounters, :observations, :conditions, :present, :procedures, :immunizations, :medications, :careplans
      def initialize
        @patient_info = { expired: false, uuid: SecureRandom.uuid }
        @encounters = []
        @observations = []
        # store condition info
        @conditions = []
        # check presence of condition
        @present = {}
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

      def observation(type, time, value, options = {})
        @observations << {
          'type' => type,
          'time' => time,
          'value' => value,
          'fhir' => :observation,
          'ccda' => :vital_sign
        }.merge(options)
      end

      def condition(type, time, fhir_method = :condition, ccda_method = :condition)
        return if @present[type]
        @present[type] = {
          'type' => type,
          'time' => time,
          'fhir' => fhir_method,
          'ccda' => ccda_method
        }
        @conditions << @present[type]
      end

      def end_condition(type, time)
        @present[type]['end_time'] = time
        @present[type] = nil
      end

      def procedure(type, time, options = {})
        @present[type] = {
          'type' => type,
          'time' => time,
          'fhir' => :procedure,
          'ccda' => :procedure
        }.merge(options)
        @procedures << @present[type]
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

      def encounter_end(type, time, options = {})
        enc = @encounters.find { |x| x['type'] == type && x['end_time'].nil? }
        if enc
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

      def medication_active?(type)
        !@medications.find { |x| x['type'] == type && x['stop'].nil? }.nil?
      end

      def update_med_reasons(type, reasons, update_time)
        prescription = @medications.find { |x| x['type'] == type && x['stop'].nil? }
        if prescription
          prescription['reasons'] = reasons
          prescription['time'] = update_time
        end
      end

      def medication_stop(type, time, reason)
        prescription = @medications.find { |x| x['type'] == type && x['stop'].nil? }
        if prescription
          prescription['stop'] = time
          prescription['stop_reason'] = reason
        end
      end

      def careplan_start(type, activities, time, options = {})
        @careplans << {
          'type' => type,
          'activities' => activities,
          'time' => time,
          'start_time' => time,
          'reasons' => [],
          'goals' => []
        }.merge(options)
      end

      def careplan_active?(type)
        !@careplans.find { |x| x['type'] == type && x['stop'].nil? }.nil?
      end

      def careplan_stop(type, time)
        careplan = @careplans.find { |x| x['type'] == type && x['stop'].nil? }
        careplan['stop'] = time if careplan
      end

      def update_careplan_reasons(type, reasons, update_time)
        careplan = @careplans.find { |x| x['type'] == type && x['stop'].nil? }
        if careplan
          careplan['reasons'] = reasons
          careplan['time'] = update_time
        end
      end
    end
  end
end
