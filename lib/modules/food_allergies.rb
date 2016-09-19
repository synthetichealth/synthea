module Synthea
  module Modules
    class FoodAllergies < Synthea::Rules

      # Statistics for these rules came from the National Institute of Allergy and Infectious Diseases
      # See https://web.archive.org/web/20100407195412/http://www.niaid.nih.gov/topics/foodAllergy/understanding/Pages/quickFacts.aspx

      # People can develop food allergies at any age.
      rule :food_allergy?, [:food_allergy], [:food_allergy] do |time, entity|
        food_allergy = entity[:food_allergy]
        if food_allergy.nil?
          if rand <= 0.01 # one percent chance we'll calculate food allergies this time step
            allergens = []
            allergens << :peanuts if (rand <= 0.006)
            allergens << :tree_nuts if (rand <= 0.004)
            allergens << :fish if (rand <= 0.004)
            allergens << :shellfish if (rand <= 0.02)

            if allergens.empty?
              entity[:food_allergy] = false
            else
              entity[:food_allergy] = allergens
              entity.events.create(time, :food_allergy, :food_allergy?, true)
            end
          end
        end
      end

      def self.record_diagnoses(entity, time)
        food_allergy = entity[:food_allergy]
        patient = entity.record_synthea
        if food_allergy && !patient.present.keys.any?{|x|x.to_s.start_with?('food_allergy_')}
          food_allergy.each do |allergen|
            key = "food_allergy_#{allergen.to_s}".to_sym
            patient.condition(key, time, :allergy, :condition)
          end
        end
      end
    end
  end
end
