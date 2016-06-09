require_relative '../test_helper'

class FoodAllergiesTest < Minitest::Test
	def setup
  	@patient = Synthea::Person.new
    @time = Synthea::Config.start_date
    @patient[:food_allergy] = [:peanuts]
    entry = FHIR::Bundle::Entry.new({'fullUrl'=>'123'})
    entry.resource = FHIR::Patient.new
  	@patient.fhir_record.entry << entry
    Synthea::Modules::FoodAllergies::Record.diagnoses(@patient, @time)
  end

  def test_diagnoseFhir
  	allergy_entry = @patient.fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::AllergyIntolerance)}
  	allergy = allergy_entry.resource
  	assert_equal('Patient/123',allergy.patient.reference)
  	assert_equal('91935009', allergy.substance.coding[0].code)
  	assert_equal('peanuts', allergy.substance.coding[0].display)
  end

  def test_diagnoseCCDA
  	allergy = @patient.record.conditions[-1]
  	assert_equal('91935009',allergy['codes']['SNOMED-CT'][0])
  	assert_equal('Food Allergy: Peanuts', allergy['description'])
  	assert_equal(@time.to_i, allergy['start_time'])
  end
end
