require_relative '../test_helper'
class ModuleTest < Minitest::Test
	include Synthea
	def setup
		@person = Synthea::Person.new
		@time = Time.now
		@rule = Synthea::Rules.new
		@changes = []
	end

	def test_prescribeMedication
		@rule.prescribeMedication(:glp1ra, :coronary_heart_disease, @time, @person, @changes)
		assert_equal({"time" => @time, 'reasons' => [:coronary_heart_disease]}, @person[:medications][:glp1ra])
		assert_equal([:glp1ra], @changes)
		@rule.prescribeMedication(:glp1ra, :coronary_heart_disease, @time, @person, @changes)
		assert_equal([:glp1ra], @changes)
		assert_equal({"time" => @time, 'reasons' => [:coronary_heart_disease]}, @person[:medications][:glp1ra])
		@rule.prescribeMedication(:glp1ra, :diabetes, @time, @person, @changes)
		assert_equal([:glp1ra], @changes)
		assert_equal({"time" => @time, 'reasons' => [:coronary_heart_disease, :diabetes]}, @person[:medications][:glp1ra])
		
		@rule.prescribeMedication(:sglt2i, :coronary_heart_disease, @time, @person, @changes)
		assert_equal([:glp1ra, :sglt2i], @changes)

	end

	def test_stopMedication
		@rule.prescribeMedication(:prandial_insulin, :coronary_heart_disease, @time, @person, @changes)
		assert_equal([:prandial_insulin], @changes)
		@rule.prescribeMedication(:prandial_insulin, :diabetes, @time, @person, @changes)
		assert_equal([:prandial_insulin], @changes)
		@rule.stopMedication(:prandial_insulin, :random_reason, @time, @person, @changes)
		assert_equal([:prandial_insulin], @changes)
		assert_equal({"time" => @time, 'reasons' => [:coronary_heart_disease, :diabetes]}, @person[:medications][:prandial_insulin])
		@rule.stopMedication(:prandial_insulin, :coronary_heart_disease, @time, @person, @changes)
		assert_equal([:prandial_insulin], @changes)
		assert_equal({"time" => @time, 'reasons' => [:diabetes]}, @person[:medications][:prandial_insulin])
		@rule.stopMedication(:prandial_insulin, :diabetes, @time, @person, @changes)
		assert_equal([:prandial_insulin], @changes)
		assert(@person[:medications][:prandial_insulin].nil?)

		@rule.stopMedication(:glp1ra, :coronary_heart_disease, @time, @person, @changes)
		assert_equal([:prandial_insulin], @changes)
		@rule.prescribeMedication(:glp1ra, :coronary_heart_disease, @time, @person, @changes)
		@changes = []
		@rule.stopMedication(:glp1ra, :coronary_heart_disease, @time, @person, @changes)
		assert_equal([:glp1ra], @changes)
	end
end