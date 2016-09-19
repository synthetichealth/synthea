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
          if immunization_due(imm, birthdate, age_in_months, time, entity[:immunizations][imm] || [])
            entity[:immunizations][imm] ||= []
            entity[:immunizations][imm] << time
            # puts "Administering #{IMM_SCHEDULE[imm][:code]['display']} to #{entity[:name_first]} age #{age_in_months/12} in year #{time.year}"
            patient.immunization(imm, time, :immunization, :immunization)
          end
        end
      end

      def self.immunization_due(imm, birthdate, age_in_months, time, history)

        at_months = IMM_SCHEDULE[imm][:at_months]
        first_available = IMM_SCHEDULE[imm][:first_available]

        # Don't administer if the immunization wasn't historically available at the date of the encounter
        return false if first_available && time.year < first_available

        # Don't administer if all recommended doses have already been given
        return false if history.length >= at_months.length

        # See if the patient should recieve a dose based on their current age and the recommended dose ages;
        # we can't just see if greater than the recommended age for the next dose they haven't received
        # because ie we don't want to administer the HPV vaccine to someone who turns 90 in 2006 when the
        # vaccine is released; we can't just use a simple test of, say, within 4 years after the recommended
        # age for the next dose they haven't received because ie PCV13 is given to kids and seniors but was
        # only available starting in 2010, so a senior in 2012 who has never received a dose should get one,
        # but only one; what we do is

        # 1) eliminate any recommended doses that are not within 4 years of the patient's age
        at_months = at_months.reject { |am| age_in_months - am >= 48 }

        # 2) eliminate recommended doses that were actually administered
        history.each do |date|
          age_at_date = Synthea::Modules::Lifecycle.age(date, birthdate, nil, :months)
          # Note: we use drop(1) to non-destructively create a copy of at_months without the first element
          # so that we don't change the global at_months data
          at_months = at_months.drop(1) if at_months.size > 0 && age_at_date >= at_months.first && age_at_date - at_months.first < 48
        end

        # 3) see if there are any recommended doses remaining that this patient is old enough for
        return at_months.size > 0 && age_in_months >= at_months.first

        # Note: the approach used here still has some issues, mostly due to odd interactions between
        # vaccination availability dates and how we schedule adult vaccinations. For example, the PCV13
        # vaccine is administered to adults over 65, but if the adult is already older than 69 (65 + 4) when
        # the vaccine is made available in 2012, step 1 above causes the dose to not be administered at all

      end
    end
  end
end
