module Synthea
  module Modules
    class Immunizations < Synthea::Rules

      # Blank rule to show relationship between vaccinations and age
      rule :vaccinations, [:age], [] do |time, entity|
      end

      # http://www.cdc.gov/vaccines/schedules/downloads/child/0-18yrs-schedule.pdf
      # http://www.cdc.gov/vaccines/schedules/downloads/adult/adult-schedule.pdf
      # http://www2a.cdc.gov/vaccines/iis/iisstandards/vaccines.asp?rpt=cvx
      SCHEDULE = {
        :hepb_child => {
          :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'08','display'=>'Hep B, adolescent or pediatric'},
          :due_at_months => [0, 1, 6]
        },
        :rv_mono => {
          :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'119','display'=>'rotavirus, monovalent'},
          :due_at_months => [2, 4]
        },
        :dtap => {
          :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'20','display'=>'DTaP'},
          :due_at_months => [2, 4, 6, 15, 48]
        },
      }

      class Record < BaseRecord
        def self.perform_encounter(entity, time)
          entity[:vaccs] ||= {}

          birthdate = entity.event(:birth).time
          age_in_months = Synthea::Modules::Lifecycle.age(time, birthdate, nil, :months)
          SCHEDULE.each_key do |vacc|
            if self.vaccination_due(vacc, age_in_months, entity[:vaccs][vacc])
              entity[:vaccs][vacc] ||= []
              entity[:vaccs][vacc] << time
              self.record_vaccination(vacc, entity, time)
            end
          end
        end

        def self.vaccination_due(vacc,age_in_months,history)
          history ||= []
          due_at_months = SCHEDULE[vacc][:due_at_months]
          if history.length < due_at_months.length
            return age_in_months >= due_at_months[history.length]
          end
          return false
        end

        def self.record_vaccination(vacc, entity, time)
          patient = entity.record
          patient.immunizations << Immunization.new({
            "codes" => { "CVX" => [SCHEDULE[vacc][:code]["code"]]},
            "description" => [SCHEDULE[vacc][:code]["display"]],
            "time" => time.to_i
          })

          #last encounter inserted into fhir_record entry is assumed to correspond with what's being recorded
          encounter = entity.fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::Encounter)}
          patient = entity.fhir_record.entry.find {|e| e.resource.is_a?(FHIR::Patient)}

          immunization = FHIR::Immunization.new({
            'status'=>'completed',
            'date' => convertFhirDateTime(time,'time'),
            'vaccineCode'=>{
              'coding'=>[SCHEDULE[vacc][:code]]
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
