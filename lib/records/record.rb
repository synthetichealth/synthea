module Synthea
	module Output
		class Record
			attr_accessor :patient_info, :encounters, :observations, :conditions, :present, :procedures, :immunizations, :medications, :careplans
			def initialize
				@patient_info = {expired: false}
				@encounters = []
				@observations = []
				#store condition info
				@conditions = []
				#check presence of condition
				@present = {}
        @procedures = []
        @immunizations = []
        @medications = []
        @careplans = []
			end
			#birth and basic information are stored as person attributes and can be referred to when writing other records.
			#No need to duplicate here.
			def death(time)
				@patient_info[:deathdate] =  time
				@patient_info[:expired] = true
			end

			def observation(type, time, value, fhir_method=:observation, ccda_method=:vital_sign)
        @observations << {
          'type' => type,
          'time' => time,
          'value' => value,
          'fhir' => fhir_method, 
          'ccda' => ccda_method
        }
      end

      def condition(type, time, fhir_method=:condition, ccda_method=:condition)
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

      def procedure(type, time, reason, fhir_method=:procedure, ccda_method=:procedure)
        @present[type] = {
          'type' => type,
          'time' => time,
          'reason' => reason,
          'fhir' => fhir_method,
          'ccda' => ccda_method
        }
        @procedures << @present[type]
      end

      def diagnostic_report(type, time, numObs, fhir_method=:diagnostic_report, ccda_method=:no_action)
        @observations << {
          'type' => type,
          'time' => time,
          'numObs' => numObs,
          'fhir' => fhir_method,
          'ccda' => ccda_method
        }
      end

      def encounter(type, time) 
        @encounters << {
          'type' => type,
          'time' => time
        }
      end

      def immunization(imm, time, fhir_method=:immunization, ccda_method=:immunization)
        @immunizations << {
          'type' => imm,
          'time' => time,
          'fhir' => fhir_method,
          'ccda' => ccda_method
        }
      end

      def medication_start(type, time, reason)
        @medications << {
          'type' => type,
          'time' => time,
          'reason' => reason
        }
      end

      def medication_active?(type)
        !@medications.find{|x|x['type']==type && x['stop'].nil?}.nil?
      end

      def medication_stop(type, time, reason)
        prescription = @medications.find{|x|x['type']==type && x['stop'].nil?}
        if prescription
          prescription['stop'] = time
          prescription['stop_reason'] = reason
        end
      end

      def careplan_start(type, activities, time, reason)
        @careplans << {
          'type' => type,
          'status' => 'active',
          'activities' => activities,
          'time' => time,
          'reason' => reason
        }
      end

      def careplan_active?(type)
        !@careplans.find{|x|x['type']==type && x['status']=='active'}.nil?
      end

      def careplan_stop(type, time)
        careplan = @careplans.find{|x|x['type']==type && x['status']=='active'}
        if careplan
          careplan['status'] = 'completed' 
          careplan['stop'] = time
        end
      end
		end
	end
end