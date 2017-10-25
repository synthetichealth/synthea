require 'csv'
require 'json'

module Synthea
  class Costs
    # rubocop:disable Style/ClassVars
    cattr_accessor :rvu_data
    cattr_accessor :gpci_data
    cattr_accessor :concepts_hash
    cattr_accessor :cached_row

    def self.load_costs
      @@rvu_data = read_from_addendum(Synthea::Config.costs.rvu_file, true)
      @@gpci_data = read_from_addendum(Synthea::Config.costs.gpci_file, false)
      @@concepts_hash = read_from_concepts
    end

    def self.read_from_concepts
      concept_file = File.join(File.dirname(__FILE__), '..', '..', 'resources', 'concepts_hcpcs.csv')
      loinc = {}
      cvx = {}
      snomed = {}
      icd9 = {}
      icd10 = {}
      rxnorm = {}
      nubc = {}
      CSV.foreach(concept_file) do |row|
        # puts row.to_s.force_encoding('UTF-8')
        if row[0] == 'LOINC'
          loinc[row[1]] = { 'description' => row[2], 'cost' => '100' }
        elsif row[0] == 'CVX'
          cvx[row[1]] = { 'description' => row[2], 'cost' => '100' }
        elsif row[0] == 'SNOMED-CT'
          snomed[row[1]] = { 'hcpc' => row[3],
                             'icd-10' => row[5], 'description' => row[6] }
        elsif row[0] == 'ICD-9-CM'
          icd9[row[1]] = { 'description' => row[2], 'cost' => '100' }
        elsif row[0] == 'ICD-10-CM'
          icd10[row[1]] = { 'description' => row[2], 'cost' => '100' }
        elsif row[0] == 'RxNorm'
          rxnorm[row[1]] = { 'description' => row[2], 'cost' => '100' }
        elsif row[0] == 'NUBC'
          nubc[row[1]] = { 'description' => row[2], 'cost' => '100' }
        end
      end
      records = { :LOINC => loinc,
                  :CVX => cvx,
                  :SNOMED => snomed,
                  :ICD9 => icd9,
                  :ICD10 => icd10,
                  :RxNorm => rxnorm,
                  :NUBC => nubc }

      # turns concepts file into json (costs_output.json)
      File.open(File.join(File.dirname(__FILE__), '..', '..', 'resources', 'concept_mappings.json'), 'w') do |f|
        # TODO: add newline before first hash value
        f.write(JSON.pretty_generate(records).gsub('},', "},\n"))
        records
      end
    end

    def self.read_from_addendum(input_file, is_rvu)
      file = File.join(File.dirname(__FILE__), '..', '..', 'resources', input_file)
      data = CSV.read(file, encoding: 'UTF-8', headers: true, header_converters: :symbol)
      hashed_data = data.map(&:to_hash)

      # turns addendum B file into json (relative_value_units.json)
      # TODO: need newline before first hash entries for gson
      if is_rvu
        formatted_data = {}
        hashed_data.each do |entry|
          formatted_data[entry[:cpt1hcpcs].to_s] = entry
        end
        File.open(File.join(File.dirname(__FILE__), '..', '..', 'resources', 'relative_value_units.json'), 'w') do |f|
          content = JSON.pretty_generate(formatted_data).gsub('},', "},\n")
          f.write(content)
        end
      else
        formatted_data = {}
        hashed_data.each do |entry|
          formatted_data[entry[:locality_name]] = entry
        end
        File.open(File.join(File.dirname(__FILE__), '..', '..', 'resources', 'geographical_practice_cost_index.json'), 'w') do |f|
          content = JSON.pretty_generate(formatted_data).gsub('},', "},\n")
          f.write(content)
        end
      end
      hashed_data
    end

    # all patients generated must use the same location/gpci values
    def self.get_row_by_value(data, value, is_rvu)
      symbol = :locality_name
      symbol = :cpt1hcpcs if is_rvu

      # rvu lookup
      return data.select { |row| row[symbol] == value } if is_rvu
      # gpci lookup
      return @@cached_row if @@cached_row
      # @@cached_row saves gpci row so it doesn't need lookup for every encounter,procedure, etc..
      @@cached_row = data.select { |row| row[symbol] == value } unless is_rvu
    end

    def self.calc_payment(rvu_row, gpci_row, conversion_factor, is_facility)
      work_gpci = gpci_row[0][:"2017_pw_gpci_with_10_floor"]
      pe_gpci = gpci_row[0][:"2017_pe_gpci"]
      mp_gpci = gpci_row[0][:"2017_mp_gpci"]

      symbol = if is_facility
                 :facility_pe_rvus
               else
                 :nonfacility_pe_rvus
               end

      work_rvu = rvu_row_select(rvu_row, :work_rvus)
      pe_rvu = rvu_row_select(rvu_row, symbol)
      mp_rvu = rvu_row_select(rvu_row, :malpractice_rvus)

      # OPPS payment equation
      total_rvu = work_rvu.to_f * work_gpci.to_f + pe_rvu.to_f * pe_gpci.to_f + mp_rvu.to_f * mp_gpci.to_f
      total_rvu * conversion_factor
    end

    def self.rvu_row_select(rvu_row, symbol)
      item = rvu_row.find { |x| x[symbol] && (x[symbol] =~ /[-+]?\d+[.]?\d+\z/) }
      if item
        item[symbol]
      else
        0.1
      end
    end

    # locality is pulled from synthea.yml (not generated patients) because no mapping between zip codes
    # and locations listed in Addendum E exists yet
    # assume hcpc value is a string
    def self.medicare_allowable_payment(hcpc_value, is_facility)
      # hcpc values in file can be either strings or ints
      rvu_row = get_row_by_value(@@rvu_data, hcpc_value, true)
      rvu_row = get_row_by_value(@@rvu_data, hcpc_value.to_i, true) if rvu_row == []
      gpci_row = get_row_by_value(@@gpci_data, Synthea::Config.costs.gpci_locality, false)
      # current conversion factor is $37.89 per 1 RVU
      calc_payment(rvu_row, gpci_row, 37.89, is_facility)
    end

    def self.get_encounter_cost(encounter_code)
      # Encounters billed using avg prices from https://www.ncbi.nlm.nih.gov/pmc/articles/PMC3096340/
      # Adjustments for initial or subsequent hospital visit and for level/complexity/time of encounter
      # not included. Assume initial, low complexity encounter (Tables 4 & 6)
      encounter_cost = 125.00 # Encounter inpatient
      encounter_cost = 75.00 if encounter_code == '183452005' # Outpatient Encounter, Encounter for 'checkup', Encounter for symptom, Encounter for problem,
      encounter_cost
    end

    def self.get_procedure_cost(snomed_code)
      # hcpc lookup assumes all procedures are coded in SYNTHEA using SNOMED-CT
      if @@concepts_hash[:SNOMED] && @@concepts_hash[:SNOMED][snomed_code]
        hcpc_code = @@concepts_hash[:SNOMED][snomed_code]['hcpc']
      end
      # default to code 27130 if no mapping exists
      hcpc_code = '27130' unless hcpc_code
      Synthea::Costs.medicare_allowable_payment(hcpc_code, true)
    end

    def self.get_medication_cost(_rxnorm_code)
      255.00 # currently all medications cost $255.
    end

    def self.get_vaccine_cost(_cvx_code)
      # https://www.nytimes.com/2014/07/03/health/Vaccine-Costs-Soaring-Paying-Till-It-Hurts.html
      # currently all vaccines cost $136.
      136.00
    end

    # begin
    #   # test
    #   rvu_data = read_from_addendum(Synthea::Config.costs.rvu_file, true)
    #   gpci_data = read_from_addendum(Synthea::Config.costs.gpci_file, false)
    #   print medicare_allowable_payment(rvu_data, '89220', gpci_data, 'REST OF MASSACHUSETTS', false)
    # end
  end
end
