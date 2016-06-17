require_relative '../test_helper'

class ImmunizationsTest < Minitest::Test

  def setup
  	@patient = Synthea::Person.new
    @patient[:age] = 0
    @time = Synthea::Config.start_date
	@patient.events.create(@time, :birth, :birth, true)
    entry = FHIR::Bundle::Entry.new({'fullUrl'=>'123'})
    entry.resource = FHIR::Patient.new
  	@patient.fhir_record.entry << entry
  	entry = FHIR::Bundle::Entry.new('fullUrl'=>'789')
  	entry.resource = FHIR::Encounter.new
  	@patient.fhir_record.entry << entry
  end

  def test_birth_immunizations
  	Synthea::Modules::Immunizations::Record.perform_encounter(@patient, @time)
	assert_equal(1, @patient[:immunizations].length)
	assert_equal(1, @patient[:immunizations][:hepb].length)
	assert_equal(@time, @patient[:immunizations][:hepb][0])
  	immunization_entry = @patient.fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::Immunization)}
  	immunization = immunization_entry.resource
  	assert_equal('Patient/123',immunization.patient.reference)
	assert_equal('Encounter/789',immunization.encounter.reference)
	assert_equal("http://hl7.org/fhir/sid/cvx", immunization.vaccineCode.coding[0].system)
	assert_equal("08", immunization.vaccineCode.coding[0].code)
	assert_equal("Hep B, adolescent or pediatric", immunization.vaccineCode.coding[0].display)
    assert_equal(Synthea::Rules::BaseRecord.convertFhirDateTime(@time,'time'),immunization.date)
    assert_equal('completed', immunization.status)
	assert_equal(false, immunization.wasNotGiven)
	assert_equal(false, immunization.reported)
  end

  def test_two_month_immunizations
	# birth immunizations
  	Synthea::Modules::Immunizations::Record.perform_encounter(@patient, @time)
	# 1-month immunizations
	time_1m = @time.advance(:months => 1)
	Synthea::Modules::Immunizations::Record.perform_encounter(@patient, time_1m)
	# 2-month immunizations
	time_2m = @time.advance(:months => 2)
	Synthea::Modules::Immunizations::Record.perform_encounter(@patient, time_2m)

	assert_equal(6, @patient[:immunizations].length)
	assert_equal(2, @patient[:immunizations][:hepb].length)
	assert_equal(@time, @patient[:immunizations][:hepb][0])
	assert_equal(time_1m, @patient[:immunizations][:hepb][1])
	assert_equal(1, @patient[:immunizations][:rv_mono].length)
	assert_equal(time_2m, @patient[:immunizations][:rv_mono][0])
	assert_equal(1, @patient[:immunizations][:dtap].length)
	assert_equal(time_2m, @patient[:immunizations][:dtap][0])
	assert_equal(1, @patient[:immunizations][:hib].length)
	assert_equal(time_2m, @patient[:immunizations][:hib][0])
	assert_equal(1, @patient[:immunizations][:pcv13].length)
	assert_equal(time_2m, @patient[:immunizations][:pcv13][0])
	assert_equal(1, @patient[:immunizations][:ipv].length)
	assert_equal(time_2m, @patient[:immunizations][:ipv][0])
  end

end
