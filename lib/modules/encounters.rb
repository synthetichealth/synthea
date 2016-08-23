module Synthea
  module Modules
    class Encounters < Synthea::Rules

      # People have encounters
      rule :schedule_encounter, [:age], [:encounter] do |time, entity|
        if entity[:is_alive]
          unprocessed_events = entity.events.unprocessed_before(time,:encounter_ordered)
          unprocessed_events.each do |event|
            entity.events.process(event)

            schedule_variance = Synthea::Config.schedule.variance
            birthdate = entity.event(:birth).time
            deathdate = entity.event(:death).try(:time)

            age_in_years = entity[:age]
            if age_in_years >= 3
              delta = case 
                when age_in_years <= 19
                  1.year
                when age_in_years <= 39 
                  3.years
                when age_in_years <= 49 
                  2.years
                else 
                  1.year
              end
            else
              age_in_months = Synthea::Modules::Lifecycle.age(time, birthdate, deathdate, :months)
              delta = case 
                when age_in_months <= 1
                  1.months
                when age_in_months <= 5
                  2.months
                when age_in_months <= 17
                  3.months
                else 
                  6.months
              end
            end
            next_date = time + Distribution::Normal.rng(delta*1.0, delta*schedule_variance).call
            entity.events.create(next_date, :encounter, :schedule_encounter)
          end
        end
      end

      rule :encounter, [], [:schedule_encounter,:observations,:lab_results,:diagnoses,:immunizations] do |time, entity|
        if entity[:is_alive]
          unprocessed_events = entity.events.unprocessed_before(time,:encounter)
          unprocessed_events.each do |event|
            entity.events.process(event)
            self.class.encounter(entity, event.time)
            Synthea::Modules::Lifecycle.record_height_weight(entity, event.time)
            Synthea::Modules::Immunizations.perform_encounter(entity, event.time)
            Synthea::Modules::MetabolicSyndrome.perform_encounter(entity, event.time)
            Synthea::Modules::FoodAllergies.record_diagnoses(entity, event.time)  
            Synthea::Modules::CardiovascularDisease.perform_encounter(entity, event.time)
            Synthea::Modules::Generic.perform_wellness_encounter(entity, event.time)
            entity.events.create(event.time, :encounter_ordered, :encounter)
          end
        end
      end

      #processes all emergency events. Implemented as a function instead of a rule because emergency events must be procesed
      #immediately rather than waiting til the next time period. Patient may die, resulting in rule not being called.
      def self.emergency_visit (time, entity)
        unprocessed_events = entity.events.unprocessed_before(time,:emergency_encounter)
        unprocessed_events.each do |event|
          entity.events.process(event)
          emergency_encounter(entity, time)
        end

        unprocessed_events = entity.events.unprocessed.select{|x| [:myocardial_infarction,:cardiac_arrest,:stroke].include?(x.type) && x.time <= time}
        unprocessed_events.each do |event|
          entity.events.process(event)
          Synthea::Modules::CardiovascularDisease.perform_emergency(entity, event)
        end
      end

      #------------------------------------------------------------------------------------------#

      def self.encounter(entity, time)
        age = entity[:age]
        type = case
          when age <= 1  
            :age_lt_1
          when age <= 4  
            :age_lt_4
          when age <= 11 
            :age_lt_11
          when age <= 17
            :age_lt_17 
          when age <= 39 
            :age_lt_39
          when age <= 64 
            :age_lt_64
          else
            :age_senior
        end
        entity.record_synthea.encounter(type, time)
      end

      def self.emergency_encounter(entity,time)
        entity.record_synthea.encounter(:emergency, time)
      end
    end
  end
end
