require_relative '../test_helper'

class LifecycleTest < Minitest::Test

  def setup
  	@patient = Synthea::Person.new
  	@patient[:name_first] = "Jane"
  	@patient[:name_last] = "Doe"
  	@patient[:gender] = 'F'
  	@patient[:race] = :white
  	@patient[:ethnicity] = :italian
    @time = Synthea::Config.start_date
    Synthea::Modules::Lifecycle::Record.birth(@patient, @time)
  end

  def test_birthFhir
  	
  	person_entry = @patient.fhir_record.entry.find{|e| e.resource.is_a?(FHIR::Patient)}
 	  person = person_entry.resource
 	  hname = person.name[0]
  	assert_equal("Jane",hname.given[0])
  	assert_equal("Doe",hname.family[0])
  	assert_equal("official",hname.use)
  	assert_equal('female',person.gender)
  	assert_equal(Synthea::Rules::BaseRecord.convertFhirDateTime(@time),person.birthDate)
  	assert_match(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/,person.id)
  	race = person.extension[0].valueCodeableConcept.coding[0]
  	assert_equal('White',race.display)
  	assert_equal('2106-3',race.code)
  	ethnicity = person.extension[1].valueCodeableConcept.coding[0]
  	assert_equal('Italian',ethnicity.display)
  	assert_equal('2114-7',ethnicity.code)
  end
  
  def test_birthCCDA
  	person = @patient.record
  	assert_equal("Jane",person.first)
  	assert_equal("Doe",person.last)
  	assert_equal('F',person.gender)
  	assert_equal(@time.to_i,person.birthdate)
  	assert(!person.expired)
  	assert_equal({'name'=>'White','code'=>'2106-3'},person.race)
  	assert_equal({'name'=>'Italian','code'=>'2114-7'},person.ethnicity)
  end

  def test_deathFhir
  	Synthea::Modules::Lifecycle::Record.death(@patient, @time)
  	person_entry = @patient.fhir_record.entry.find{|e| e.resource.is_a?(FHIR::Patient)}
 	person = person_entry.resource
 	assert_equal(Synthea::Rules::BaseRecord.convertFhirDateTime(@time,'time'),person.deceasedDateTime)
  end

  def test_deathCCDA
  	Synthea::Modules::Lifecycle::Record.death(@patient, @time)
  	person= @patient.record
  	assert(person.expired)
  	assert_equal(@time.to_i,person.deathdate)
  end

  def test_height_weightFHIR
  	@patient[:height] = 60
  	@patient[:weight] = 10
  	entry = FHIR::Bundle::Entry.new
  	entry.resource = FHIR::Encounter.new('id'=>'123')
  	@patient.fhir_record.entry << entry
  	Synthea::Modules::Lifecycle::Record.height_weight(@patient, @time)
  	
  	lookup = {'quant_height' => 60, 'quant_weight'=>10,'code_height'=>'8302-2', 'code_weight'=>'29463-7'}
  	
  	['height','weight'].each do |option|
	  	observe_entry = @patient.fhir_record.entry.reverse.find{|e| e.resource.is_a?(FHIR::Observation) && e.resource.code.text=='Body '+option.capitalize}
	  	observe = observe_entry.resource
	  	assert_equal(lookup['quant_'+option] ,observe.valueQuantity.value)
	  	assert_equal('123',observe.encounter.reference)
	  	assert_equal(lookup['code_'+option] , observe.code.coding[0].code)
	  	person_entry = @patient.fhir_record.entry.find{|e| e.resource.is_a?(FHIR::Patient)}
	 	personID = person_entry.resource.id
	 	assert_equal(personID,observe.subject.reference)
	end
	 	
  end
  
  def test_height_weightCCDA
  	@patient[:height] = 60
  	@patient[:weight] = 10
  	entry = FHIR::Bundle::Entry.new
  	entry.resource = FHIR::Encounter.new('id'=>'123')
  	@patient.fhir_record.entry << entry
  	Synthea::Modules::Lifecycle::Record.height_weight(@patient, @time)
  	person = @patient.record
  	lookup = {
      'quant_height' => 60, 
      'quant_weight'=>10,
      'code_height'=>'8302-2', 
      'code_weight'=>'29463-7',
     'vitals_height' => person.vital_signs[-1], 
     'vitals_weight' => person.vital_signs[-2],
     'unit_height'=>'cm',
     'unit_weight'=>'kg'
   }

  	['height','weight'].each do |option|
      vitals = lookup['vitals_'+option]
  		assert_equal(lookup['code_'+option], vitals['codes']['LOINC'][0])
      assert_equal(lookup['quant_'+option], vitals.values[0]['scalar'])
      assert_equal(lookup['unit_'+option], vitals.values[0]['units'])
      assert_equal('Body '+option.capitalize ,vitals['description'])
      assert_equal(@time.to_i,vitals['start_time'])
      assert_equal(@time.to_i,vitals['end_time'])
    end
  end
end
