require_relative '../test_helper'

class SymptomsTest < Minitest::Test

  def setup
    @patient = Synthea::Person.new
  end

  def test_setting_symptom
    # Make sure we can set a symptom value and get the correct result when retrieving it
    @patient.set_symptom_value(:diabetes, :fatigue, 30)
    assert_equal 30, @patient.get_symptom_value(:fatigue)
  end

  def test_setting_symptom_with_multiple_causes
    # Make sure if we set a symptom with multiple causes the retrieved result is the most severe
    @patient.set_symptom_value(:diabetes, :fatigue, 70)
    @patient.set_symptom_value(:anemia, :fatigue, 40)
    assert_equal 70, @patient.get_symptom_value(:fatigue)
  end

  def test_setting_symptom_with_random_value
    # Make sure that this method sets *some* value
    @patient.set_symptom_weighted_random_value(:diabetes, :fatigue, 3)
    assert !@patient.get_symptom_value(:fatigue).nil?, "Expected fatigue symptom to be set"
  end

  def test_getting_symptoms_exceeding_value
    # Make sure that when we set several symptoms, we can retrieve the ones that exceed a value
    @patient.set_symptom_value(:anemia, :fatigue, 30)
    @patient.set_symptom_value(:diabetes, :fatigue, 80)
    @patient.set_symptom_value(:diabetes, :hunger, 50)
    @patient.set_symptom_value(:fractured_ulna, :pain, 85)
    assert_equal [:fatigue, :pain], @patient.get_symptoms_exceeding(75)
  end

end
