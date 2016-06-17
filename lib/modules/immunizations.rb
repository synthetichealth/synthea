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

      # Blank rule to show relationship between vaccinations and age
      rule :vaccinations, [:age], [] do |time, entity|
      end

      # http://www.cdc.gov/vaccines/schedules/downloads/child/0-18yrs-schedule.pdf
      # http://www.cdc.gov/vaccines/schedules/downloads/adult/adult-schedule.pdf
      # http://www2a.cdc.gov/vaccines/iis/iisstandards/vaccines.asp?rpt=cvx
      # https://www2a.cdc.gov/vaccines/iis/iisstandards/vaccines.asp?rpt=tradename
      SCHEDULE = {
        :hepb_child => {
          :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'08','display'=>'Hep B, adolescent or pediatric'},
          :at_months => [0, 1, 6]
        },
        :rv_mono => {
          :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'119','display'=>'rotavirus, monovalent'},
          #  Monovalent (Rotarix) is a 2-dose series, as opposed to pentavalent (RotaTeq) which is a 3-dose series 
          :at_months => [2, 4]
        },
        :dtap => {
          :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'20','display'=>'DTaP'},
          :at_months => [2, 4, 6, 15, 48]
        },
        :hib => {
          :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'49','display'=>'Hib (PRP-OMP)'},
          # PRP-OMP (PedvaxHib or COMVAX) is a 2-dose series with a booster at 12-15 months, as opposed to PRP-T
          # (AC-THIB) which is a 3-dose series with a booster at 12-15 months
          :at_months => [2, 4, 12]
        },
        :pcv13 => {
          :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'133','display'=>'Pneumococcal conjugate PCV 13'},
          :at_months => [2, 4, 6, 12, 780]
        },
        :ipv => {
          :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'10','display'=>'IPV'},
          :at_months => [2, 4, 6, 48]
        },
        :flu => {
          :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'140','display'=>'Influenza, seasonal, injectable, preservative free'},
          # This should really only happen Aug - Feb (preferring earlier).  That may take some trickery.
          # Since this is annual administration just populate the array with every 12 months, starting at 6 months.
          :at_months => (0..100).map {|year| year * 12 + 6 }
        },
        :mmr => {
          :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'03','display'=>'MMR'},
          :at_months => [12, 48]
        },
        :var => {
          :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'21','display'=>'varicella'},
          :at_months => [12, 48]
        },
        :hepa => {
          :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'83','display'=>'Hep A, ped/adol, 2 dose'},
          # First dose should be 12-23 months, second dose 6-18 months after.  Choosing to do 12 months after.
          :at_months => [12, 24]
        },
        :men => {
          # MenACWY can be Menactra (114) or Menveo (136).  Arbitrarily chose Menactra.
          :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'114','display'=>'meningococcal MCV4P'},
          :at_months => [132, 192]
        },
        :tdap => {
          :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'115','display'=>'Tdap'},
          :at_months => [132]
        },
        :hpv => {
          :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'62','display'=>'HPV, quadrivalent'},
          # [11 years, boosters 2 months and 6 months later] -- but since we only have encounters scheduled yearly
          # at this age, the boosters will be late.  To be revisited later.
          :at_months => [132, 134, 138]
        },
        :td => {
          :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'113','display'=>'Td (adult) preservative free'},
          # 21 years and every 10 years after
          :at_months => [21, 31, 41, 51, 61, 71, 81, 91].map {|year| year * 12 }
        },
        :zoster => {
          :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'121','display'=>'zoster'},
          :at_months => [720]
        },
        :ppsv23 => {
          :code => {'system'=>'http://hl7.org/fhir/sid/cvx','code'=>'33','display'=>'pneumococcal polysaccharide vaccine, 23 valent'},
          :at_months => [792]
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
          at_months = SCHEDULE[vacc][:at_months]
          if history.length < at_months.length
            return age_in_months >= at_months[history.length]
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
