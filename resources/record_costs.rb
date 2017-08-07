require 'csv'
require 'byebug'
require 'json'

class RecordCosts
  def self.read_from_concepts
    concept_file = File.join(File.dirname(__FILE__), '..', 'resources', 'concepts.csv')
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
        snomed[row[1]] = { 'description' => row[2], 'cost' => '100' }
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

    File.open(File.join(File.dirname(__FILE__), '..', 'resources', 'costs_output.json'), 'w') do |f|
      f.write(JSON.pretty_generate(records))
    end
  end

  read_from_concepts
end
