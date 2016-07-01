module Synthea
  module Modules
    class Immunizations < Synthea::Rules

      # This is a complete, but fairly simplistic approach to synthesizing immunizations.  It is encounter driven;
      # whenever an encounter occurs, the doctor checks for due immunizations and gives them.  In at least one case
      # (HPV) this means that the immunization schedule isn't strictly followed since the encounter schedule doesn't
      # match the immunization schedule (e.g., 11yrs, 11yrs2mo, 11yrs6mo) -- but in most cases they do line up.
      # This module also assumes perfect doctors and compliant patients.  Every patient eventually receives every
      # recommended immunization (unless they die first).  This module also does not implement any deviations or
      # contraindications based on patient conditions.  For now, we've avoided specific brand names, preferring the
      # general CVX codes.

      # Blank rule to show relationship between immunizations and age
      rule :immunizations, [:age], [] do |time, entity|
      end

      
      
      #-----------------------------------------------------------------------#

      def self.perform_encounter(entity, time)
        entity[:immunizations] ||= {}
        patient = entity.record_synthea
        birthdate = entity.event(:birth).time
        age_in_months = Synthea::Modules::Lifecycle.age(time, birthdate, nil, :months)
        IMM_SCHEDULE.each_key do |imm|
          if immunization_due(imm, age_in_months, entity[:immunizations][imm])
            entity[:immunizations][imm] ||= []
            entity[:immunizations][imm] << time
            patient.immunization(imm, time, :immunization)
          end
        end
      end

      def self.immunization_due(imm,age_in_months,history)
        history ||= []
        at_months = IMM_SCHEDULE[imm][:at_months]
        if history.length < at_months.length
          return age_in_months >= at_months[history.length]
        end
        return false
      end
    end
  end
end
