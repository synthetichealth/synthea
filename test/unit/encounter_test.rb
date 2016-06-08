require_relative '../test_helper'

class EncounterTest < Minitest::Test

  def setup
  	@patient = Synthea::Person.new
  	@patient[:age] = 10
    entry = FHIR::Bundle::Entry.new({'fullUrl' => '123'})
    entry.resource = FHIR::Patient.new
  	@patient.fhir_record.entry << entry
    @time = Synthea::Config.start_date
    Synthea::Modules::Encounters::Record.encounter(@patient, time)
  end

  def test_encounterFhir
    
    encounter_entry = @patient.fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::Encounter)}
  	encounter = encounter_entry.resource
    assert_equal('99393',encounter.type[0].coding[0].code,'Encounter code incorrect')
    assert_equal('Patient/123',encounter.patient.reference)
    assert_equal('finished',encounter.status)
    startTime = Synthea::Rules::BaseRecord.convertFhirDateTime(@time,'time')
    endTime = Synthea::Rules::BaseRecord.convertFhirDateTime(@time+15.minutes, 'time')
    period = FHIR::Period.new({'start'=>startTime, 'end' => endTime})
    assert_equal(period.start,encounter.period.start)
    assert_equal(period.end, encounter.period.end)
  end
  
  def test_encounterCCDA
    encounter = @patient.record.encounters.last
    assert_equal('2.16.840.1.113883.3.560.1.79',encounter['oid'])
    assert_equal('Outpatient Encounter', encounter['description'])
    assert_equal(@time.to_i,encounter['start_time'])
    assert_equal(@time.to_i+15.minutes,encounter['end_time'])
    assert_equal({"CPT" => ["99393"], "ICD-9-CM" => ['V20.2'], "ICD-10-CM" => ['Z00.129'], 'SNOMED-CT' => ['170258001']},encounter['codes'])
  end
end