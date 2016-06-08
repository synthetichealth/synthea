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

      class Record < BaseRecord
        def self.diagnoses(entity, time)
          patient = entity.record
          food_allergy = entity[:food_allergy]
          if !food_allergy.nil? && food_allergy!=false && !entity.record_conditions.keys.any?{|x|x.to_s.start_with?('food_allergy_')}
            # create the ongoing diagnosis
            food_allergy.each do |allergen|
              key = "food_allergy_#{allergen.to_s}".to_sym
              entity.record_conditions[key] = Condition.new(condition_hash(key, time))
              patient.conditions << entity.record_conditions[key]

              allergy = FHIR::AllergyIntolerance.new
              patient = entity.fhir_record.entry.find{|e| e.resource.is_a?(FHIR::Patient)}
              allergy.patient = FHIR::Reference.new({'reference'=>'Patient/' + patient.fullUrl})
              snomed_code = condition_hash(key, time)['codes']['SNOMED-CT'][0]
              allergyCoding = FHIR::Coding.new({'code'=>snomed_code, 'display'=>allergen.to_s, 'system' => 'http://hl7.org/fhir/ValueSet/daf-substance-sct'})
              allergy.substance = FHIR::CodeableConcept.new({'coding'=>[allergyCoding]})
              
              entry = FHIR::Bundle::Entry.new
              entry.resource = allergy

              entity.fhir_record.entry << entry
            end
          end
        end
      end

    end
  end
end
