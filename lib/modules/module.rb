module Synthea
  class Rules

    cattr_accessor :metadata

    def initialize
      @rules ||= methods.grep(/_rule$/).map {|r| method(r)}
    end

    def run(time, entity)
      @rules.each {|r| r.call(time, entity)}
    end

    def pick(array)
      rand(array.first..array.last)
    end

    def self.apply(time,entity)
      get_modules.each {|r| r.run(time, entity)}
    end

    def self.get_modules
      @@modules ||= Synthea::Modules.constants.map {|m| "Synthea::Modules::#{m}".constantize.new}
    end

    def self.rule(name,inputs,outputs,&block)
      @@metadata ||= {}
      @@metadata[name] = {
        inputs: inputs,
        outputs: outputs,
        module_name: self.to_s.split('::').last
      }
      define_method "#{name}_rule".to_sym, block
    end

    class BaseRecord
      def self.lab_hash(type, time, value)
        lookup = {
          height: { description: 'Body Height', code: '8302-2',  unit: 'cm'},
          weight: { description: 'Body Weight', code: '29463-7', unit: 'kg'},
          systolic_blood_pressure: { description: 'Systolic Blood Pressure', code: '8480-6', unit: 'mmHg'},
          diastolic_blood_pressure: { description: 'Diastolic Blood Pressure', code: '8462-4', unit: 'mmHg'},
          ha1c: { description: 'Hemoglobin A1c/Hemoglobin.total in Blood', code: '4548-4', unit: '%'},
       }

        {
          "codes" => {'LOINC' => [lookup[type][:code]]},
          "description" => lookup[type][:description],
          "start_time" => time.to_i,
          "end_time" => time.to_i,
          "oid" => "2.16.840.1.113883.3.560.1.5",
          "values" => [{
            "_type" => "PhysicalQuantityResultValue",
            "scalar" => value,
            "units" => lookup[type][:unit]
          }],
        }
      end

      def self.encounter_hash(time, codes) 
        {
          "codes" => codes,
          "description" => "Outpatient Encounter",
          "start_time" => time.to_i,
          "end_time" => time.to_i + 15.minutes,
          "oid" => "2.16.840.1.113883.3.560.1.79"
        }
      end

      def self.condition_hash(type, time)
        lookup = {
          #http://www.icd9data.com/2012/Volume1/780-799/790-796/790/790.29.htm
          hypertension: { description: 'Hypertension', codes: {'SNOMED-CT' => ['38341003']}},
          prediabetes: { description: 'Prediabetes', 
                         codes: {'ICD-9-CM' => ['790.29'], 
                                 'ICD-10-CM' => ['R73.09'], 
                                 'SNOMED-CT' => ['15777000']}},
          diabetes: { description: 'Diabetes', 
                         codes: {'SNOMED-CT' => ['44054006']}},

          nephropathy: { description: 'Diabetic renal disease (disorder)', codes: {'SNOMED-CT' => ['127013003']}},
          microalbuminuria: { description: 'Microalbuminuria due to type 2 diabetes mellitus (disorder)', codes: {'SNOMED-CT' => ['90781000119102']}},         
          proteinuria: { description: 'Proteinuria due to type 2 diabetes mellitus (disorder)', codes: {'SNOMED-CT' => ['157141000119108']}},         
          end_stage_renal_disease: { description: 'End stage renal disease (disorder)', codes: {'SNOMED-CT' => ['46177005']}},         

          retinopathy: { description: 'Diabetic retinopathy associated with type II diabetes mellitus (disorder)', codes: {'SNOMED-CT' => ['422034002']}},         
          nonproliferative_retinopathy: { description: 'Nonproliferative diabetic retinopathy due to type 2 diabetes mellitus (disorder)', codes: {'SNOMED-CT' => ['1551000119108']}},         
          proliferative_retinopathy: { description: 'Proliferative diabetic retinopathy due to type II diabetes mellitus (disorder)', codes: {'SNOMED-CT' => ['1501000119109']}},         
          macular_edema: { description: 'Macular edema and retinopathy due to type 2 diabetes mellitus (disorder)', codes: {'SNOMED-CT' => ['97331000119101']}},         
          blindness: { description: 'Blindness due to type 2 diabetes mellitus (disorder)', codes: {'SNOMED-CT' => ['60951000119105']}},         

          neuropathy: { description: 'Neuropathy due to type 2 diabetes mellitus (disorder)', codes: {'SNOMED-CT' => ['368581000119106']}},         
          amputation: { description: 'History of limb amputation (situation)', codes: {'SNOMED-CT' => ['271396005']}},

          food_allergy_peanuts: { description: 'Food Allergy: Peanuts', codes: {'SNOMED-CT' => ['91935009']}},
          food_allergy_tree_nuts: { description: 'Food Allergy: Tree Nuts', codes: {'SNOMED-CT' => ['91934008']}},
          food_allergy_fish: { description: 'Food Allergy: Fish', codes: {'SNOMED-CT' => ['417532002']}},
          food_allergy_shellfish: { description: 'Food Allergy: Shellfish', codes: {'SNOMED-CT' => ['300913006']}}
        }
        {
          "codes" => lookup[type][:codes],
          "description" => lookup[type][:description],
          "start_time" => time.to_i
          # "oid" => "2.16.840.1.113883.3.560.1.2"
        }
      end

      def self.convertFhirDateTime(date, option = nil)
        if option == 'time'
          x = date.to_s.sub(' ', 'T')
          x = x.sub(' ', '')
          x = x.insert(-3, ":")
          return Regexp.new(FHIR::PRIMITIVES['dateTime']['regex']).match(x.to_s).to_s
        else
          return Regexp.new(FHIR::PRIMITIVES['date']['regex']).match(date.to_s).to_s
        end
      end
    end

  end
end