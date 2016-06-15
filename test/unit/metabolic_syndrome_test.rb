require_relative '../test_helper'

class MetabolicSyndromeTest < Minitest::Test

  def setup
  	@patient = Synthea::Person.new
    @patient[:age] = 65
    @time = Synthea::Config.start_date
    entry = FHIR::Bundle::Entry.new({'fullUrl'=>'123'})
    entry.resource = FHIR::Patient.new
  	@patient.fhir_record.entry << entry
  	entry = FHIR::Bundle::Entry.new('fullUrl'=>'789')
  	entry.resource = FHIR::Encounter.new
  	@patient.fhir_record.entry << entry
    
  end

  def test_prediabetesFHIR
  	@patient[:prediabetes] = {}
  	Synthea::Modules::MetabolicSyndrome::Record.perform_encounter(@patient, @time)
  	prediabetes_entry = @patient.fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::Condition)}
  	prediabetes = prediabetes_entry.resource
  	assert_equal('Patient/123',prediabetes.patient.reference)
  	assert_equal("15777000", prediabetes.code.coding[0].code)
  	assert_equal('Prediabetes', prediabetes.code.coding[0].display)
    assert_equal('confirmed',prediabetes.verificationStatus)
    assert_equal(Synthea::Rules::BaseRecord.convertFhirDateTime(@time,'time'),prediabetes.onsetDateTime)
    assert_equal('Encounter/789',prediabetes.encounter.reference)
  end

  def test_prediabetesCCDA
  	@patient[:prediabetes] = {}
  	Synthea::Modules::MetabolicSyndrome::Record.perform_encounter(@patient, @time)
  	prediabetes = @patient.record.conditions[-1]
  	assert_equal('15777000',prediabetes['codes']['SNOMED-CT'][0])
  	assert_equal('Prediabetes', prediabetes['description'])
  	assert_equal(@time.to_i, prediabetes['start_time'])
  end

  def test_diabetes_end_renal_diseaseFHIR
  	@patient[:diabetes] = {:end_stage_renal_disease => true}
  	Synthea::Modules::MetabolicSyndrome::Record.perform_encounter(@patient, @time)
  	disease_entry = @patient.fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::Condition)}
  	disease = disease_entry.resource
  	assert_equal('Patient/123',disease.patient.reference)
  	assert_equal("46177005", disease.code.coding[0].code)
  	assert_equal('End stage renal disease (disorder)', disease.code.coding[0].display)
  	assert_equal('confirmed',disease.verificationStatus)
  	assert_equal(Synthea::Rules::BaseRecord.convertFhirDateTime(@time,'time'),disease.onsetDateTime)
  	assert_equal('Encounter/789',disease.encounter.reference)
  	assert(!@patient[:is_alive]) #end stage renal disease kills the patient
  end

  def test_diabetes_end_renal_diseaseCCDA
	 @patient[:diabetes] = {:end_stage_renal_disease => true}
  	Synthea::Modules::MetabolicSyndrome::Record.perform_encounter(@patient, @time)
  	disease = @patient.record.conditions[-1]
  	assert_equal('46177005',disease['codes']['SNOMED-CT'][0])
  	assert_equal('End stage renal disease (disorder)', disease['description'])
  	assert_equal(@time.to_i, disease['start_time'])
  end
  
  def test_disease_abatementFHIR
  	@patient[:diabetes] = {:blindness => true}
  	Synthea::Modules::MetabolicSyndrome::Record.perform_encounter(@patient, @time)
  	@patient[:diabetes] = {}
  	Synthea::Modules::MetabolicSyndrome::Record.perform_encounter(@patient, @time + 15.minutes)
  	#blindness should be the last condition entered
  	disease_entry = @patient.fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::Condition)}
  	disease = disease_entry.resource
  	assert_equal(Synthea::Rules::BaseRecord.convertFhirDateTime(@time + 15.minutes,'time'), disease.abatementDateTime)
  end

  def test_disease_abatementCCDA
  	@patient[:diabetes] = {:blindness => true}
  	Synthea::Modules::MetabolicSyndrome::Record.perform_encounter(@patient, @time)
  	@patient[:diabetes] = {}
  	Synthea::Modules::MetabolicSyndrome::Record.perform_encounter(@patient, @time + 15.minutes)
  	disease = @patient.record.conditions[-1]
  	assert_equal((@time + 15.minutes).to_i,disease['end_time'])
  end

end
