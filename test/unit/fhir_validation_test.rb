require_relative '../test_helper'

class FhirValidationTest < Minitest::Test

  def test_execution_and_fhir_validation
    world = Synthea::World::Sequential.new
    world.population_count = 0
    (1..10).each do |i|
      # generate a patient with synthea
      record = world.build_person
      print '.'
      # convert to FHIR
      bundle = Synthea::Output::FhirRecord.convert_to_fhir(record)
      print '.'
      # validate all the resources
      assert_empty bundle.validate
      print '.'
    end
    puts
  end

end
