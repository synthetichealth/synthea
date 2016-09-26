require_relative '../test_helper'
class ModuleTest < Minitest::Test
  include Synthea
  def setup
    @person = Synthea::Person.new
    @time = Time.now
    @rule = Synthea::Rules.new
    @changes = []
  end

  def test_prescribe_medication
    @rule.prescribe_medication(:glp1ra, :coronary_heart_disease, @time, @person, @changes)
    assert_equal({"time" => @time, 'reasons' => [:coronary_heart_disease]}, @person[:medications][:glp1ra])
    assert_equal([:glp1ra], @changes)
    @rule.prescribe_medication(:glp1ra, :coronary_heart_disease, @time, @person, @changes)
    assert_equal([:glp1ra], @changes)
    assert_equal({"time" => @time, 'reasons' => [:coronary_heart_disease]}, @person[:medications][:glp1ra])
    @rule.prescribe_medication(:glp1ra, :diabetes, @time, @person, @changes)
    assert_equal([:glp1ra], @changes)
    assert_equal({"time" => @time, 'reasons' => [:coronary_heart_disease, :diabetes]}, @person[:medications][:glp1ra])

    @rule.prescribe_medication(:sglt2i, :coronary_heart_disease, @time, @person, @changes)
    assert_equal([:glp1ra, :sglt2i], @changes)

  end

  def test_stop_medication
    @rule.prescribe_medication(:prandial_insulin, :coronary_heart_disease, @time, @person, @changes)
    assert_equal([:prandial_insulin], @changes)
    @rule.prescribe_medication(:prandial_insulin, :diabetes, @time, @person, @changes)
    assert_equal([:prandial_insulin], @changes)
    @rule.stop_medication(:prandial_insulin, :random_reason, @time, @person, @changes)
    assert_equal([:prandial_insulin], @changes)
    assert_equal({"time" => @time, 'reasons' => [:coronary_heart_disease, :diabetes]}, @person[:medications][:prandial_insulin])
    @rule.stop_medication(:prandial_insulin, :coronary_heart_disease, @time, @person, @changes)
    assert_equal([:prandial_insulin], @changes)
    assert_equal({"time" => @time, 'reasons' => [:diabetes]}, @person[:medications][:prandial_insulin])
    @rule.stop_medication(:prandial_insulin, :diabetes, @time, @person, @changes)
    assert_equal([:prandial_insulin], @changes)
    assert(@person[:medications][:prandial_insulin].nil?)

    @rule.stop_medication(:glp1ra, :coronary_heart_disease, @time, @person, @changes)
    assert_equal([:prandial_insulin], @changes)
    @rule.prescribe_medication(:glp1ra, :coronary_heart_disease, @time, @person, @changes)
    @changes = []
    @rule.stop_medication(:glp1ra, :coronary_heart_disease, @time, @person, @changes)
    assert_equal([:glp1ra], @changes)
  end
end
