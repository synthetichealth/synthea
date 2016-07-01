require_relative '../test_helper'

class ImmunizationsTest < Minitest::Test

  def setup
  	@patient = Synthea::Person.new
    @patient[:age] = 0
    @time = Synthea::Config.start_date
	  @patient.events.create(@time, :birth, :birth, true)
  end

  def test_birth_immunizations
  	Synthea::Modules::Immunizations.perform_encounter(@patient, @time)
  	assert_equal(1, @patient[:immunizations].length)
  	assert_equal(1, @patient[:immunizations][:hepb].length)
  	assert_equal(@time, @patient[:immunizations][:hepb][0])
  end

  def test_two_month_immunizations
  	# birth immunizations
    Synthea::Modules::Immunizations.perform_encounter(@patient, @time)
  	# 1-month immunizations
  	time_1m = @time.advance(:months => 1)
  	Synthea::Modules::Immunizations.perform_encounter(@patient, time_1m)
  	# 2-month immunizations
  	time_2m = @time.advance(:months => 2)
  	Synthea::Modules::Immunizations.perform_encounter(@patient, time_2m)

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
