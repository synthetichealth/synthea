require 'csv'
require 'json'

module Synthea
  class Costs
    def self.load_costs
      # TODO: stub. fill this out once we have real data to load
    end

    def self.get_encounter_cost(encounter_code)
      # Encounters billed using avg prices from https://www.ncbi.nlm.nih.gov/pmc/articles/PMC3096340/
      # Adjustments for initial or subsequent hospital visit and for level/complexity/time of encounter
      # not included. Assume initial, low complexity encounter (Tables 4 & 6)
      encounter_cost = 125.00 # Encounter inpatient
      encounter_cost = 75.00 if encounter_code == '183452005' # Outpatient Encounter, Encounter for 'checkup', Encounter for symptom, Encounter for problem,
      encounter_cost
    end

    def self.get_procedure_cost(_snomed_code)
      500.0 # TODO: find better procedure cost
    end

    def self.get_medication_cost(_rxnorm_code)
      255.00 # currently all medications cost $255.
    end

    def self.get_vaccine_cost(_cvx_code)
      # https://www.nytimes.com/2014/07/03/health/Vaccine-Costs-Soaring-Paying-Till-It-Hurts.html
      # currently all vaccines cost $136.
      136.00
    end
  end
end
