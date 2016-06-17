module Synthea
  module Modules
    class Immunizations < Synthea::Rules

      # Blank rule to show relationship between vaccinations and age
      rule :vaccinations, [:age], [] do |time, entity|
      end

      # http://www2a.cdc.gov/vaccines/iis/iisstandards/vaccines.asp?rpt=cvx
      VACCINATION_HASH = {
        :hepb_child => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'08','display'=>'Hep B, adolescent or pediatric'},
        :rv => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'119','display'=>'rotavirus, monovalent'},
      }

      class Record < BaseRecord
        def self.perform_encounter(entity, time)
          if entity[:vaccs].nil?
            entity[:vaccs] = {}
          end

          VACCINATION_HASH.each_key do |vacc|
            if self.vaccination_due(vacc, time, entity)
              if entity[:vaccs][vacc].nil?
                entity[:vaccs][vacc] = {
                  :admins => []
                }
              end
              entity[:vaccs][vacc][:admins] << time
              self.record_vaccination(vacc, entity, time)
            end
          end
        end

        def self.num_admins(vacc,entity)
          if (entity[:vaccs].nil? || entity[:vaccs][vacc].nil?)
            0
          else
            entity[:vaccs][vacc][:admins].length
          end
        end

        # http://www.cdc.gov/vaccines/schedules/downloads/child/0-18yrs-schedule.pdf
        # http://www.cdc.gov/vaccines/schedules/downloads/adult/adult-schedule.pdf
        def self.vaccination_due(vacc,time,entity)
          age_in_years = entity[:age]
          birthdate = entity.event(:birth).time
          age_in_months = Synthea::Modules::Lifecycle.age(time, birthdate, nil, :months)
          case vacc
            when :hepb_child
              admins = self.num_admins(vacc,entity)
              return case
                when admins == 0 then true
                when admins == 1 then age_in_months >= 1
                when admins == 2 then age_in_months >= 6
                else false
              end
            when :rv
              admins = self.num_admins(vacc,entity)
              return case
                when admins == 0 then age_in_months >= 2
                when admins == 1 then age_in_months >= 4
              end               
          end
        end

        def self.record_vaccination(vacc, entity, time)
          patient = entity.record
          patient.immunizations << Immunization.new({
            "codes" => { "CVX" => [VACCINATION_HASH[vacc]["code"]]},
            "description" => [VACCINATION_HASH[vacc]["display"]],
            "time" => time.to_i
          })

          #last encounter inserted into fhir_record entry is assumed to correspond with what's being recorded
          encounter = entity.fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::Encounter)}
          patient = entity.fhir_record.entry.find {|e| e.resource.is_a?(FHIR::Patient)}

          immunization = FHIR::Immunization.new({
            'status'=>'completed',
            'date' => convertFhirDateTime(time,'time'),
            'vaccineCode'=>{
              'coding'=>[VACCINATION_HASH[vacc]]
            },
            'patient'=> { 'reference'=> "Patient/#{patient.fullUrl}"},
            'wasNotGiven' => false,
            'reported' => false,
            'encounter'=> { 'reference'=> "Encounter/#{encounter.fullUrl}"}
          })
          entry = FHIR::Bundle::Entry.new
          entry.resource = immunization
          entity.fhir_record.entry << entry
        end
      end
    end
  end
end
