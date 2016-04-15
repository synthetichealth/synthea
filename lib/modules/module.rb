module Synthea
  class Rules

    cattr_accessor :metadata

    def initialize
      @rules ||= methods.grep(/_rule$/).map {|r| method(r)}
    end

    def run(time, entity)
      @rules.each {|r| r.call(time, entity)}
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
        outputs: outputs
      }
      define_method "#{name}_rule".to_sym, block
    end

    class BaseRecord
      def self.lab_hash(type, time, value)
        lookup = {
          height: { description: 'Body Height', code: '8302-2',  unit: 'cm'},
          weight: { description: 'Body Weight', code: '29463-7', unit: 'kg'}
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
          prediabetes: { description: 'Prediabetes', 
                         codes: {'ICD-9-CM' => ['790.29'], 
                                 'ICD-10-CM' => ['R73.09'], 
                                 'SNOMED-CT' => ['15777000']}},
          diabetes: { description: 'Diabetes', 
                         codes: {'SNOMED-CT' => ['44054006']}}
        }
        {
          "codes" => lookup[type][:codes],
          "description" => lookup[type][:description],
          "start_time" => time.to_i,
          "oid" => "2.16.840.1.113883.3.560.1.2"
        }
      end
    end

  end
end