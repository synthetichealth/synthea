require_relative '../test_helper'

class ImmunizationsTest < Minitest::Test

  def setup
    # We set up two patients, one born in 1947, before many vaccinations were available, and one born in 2012,
    # after all vaccinations are available
    @time_1947 = Time.parse('1947-05-05')
    @patient_1947 = Synthea::Person.new
    @patient_1947.events.create(@time_1947, :birth, :birth, true)
    @patient_1947[:age] = 0
    @time_2012 = Time.parse('2012-05-05')
    @patient_2012 = Synthea::Person.new
    @patient_2012.events.create(@time_2012, :birth, :birth, true)
    @patient_2012[:age] = 0
  end

  def test_birth_immunizations
    Synthea::Modules::Immunizations.perform_encounter(@patient_2012, @time_2012)
    assert_equal(1, @patient_2012[:immunizations].length)
    assert_equal(1, @patient_2012[:immunizations][:hepb].length)
    assert_equal(@time_2012, @patient_2012[:immunizations][:hepb][0])
  end

  def test_two_month_immunizations
    # birth immunizations
    Synthea::Modules::Immunizations.perform_encounter(@patient_2012, @time_2012)
    # 1-month immunizations
    time_1m = @time_2012.advance(:months => 1)
    Synthea::Modules::Immunizations.perform_encounter(@patient_2012, time_1m)
    # 2-month immunizations
    time_2m = @time_2012.advance(:months => 2)
    Synthea::Modules::Immunizations.perform_encounter(@patient_2012, time_2m)

    assert_equal(6, @patient_2012[:immunizations].length)
    assert_equal(2, @patient_2012[:immunizations][:hepb].length)
    assert_equal(@time_2012, @patient_2012[:immunizations][:hepb][0])
    assert_equal(time_1m, @patient_2012[:immunizations][:hepb][1])
    assert_equal(1, @patient_2012[:immunizations][:rv_mono].length)
    assert_equal(time_2m, @patient_2012[:immunizations][:rv_mono][0])
    assert_equal(1, @patient_2012[:immunizations][:dtap].length)
    assert_equal(time_2m, @patient_2012[:immunizations][:dtap][0])
    assert_equal(1, @patient_2012[:immunizations][:hib].length)
    assert_equal(time_2m, @patient_2012[:immunizations][:hib][0])
    assert_equal(1, @patient_2012[:immunizations][:pcv13].length)
    assert_equal(time_2m, @patient_2012[:immunizations][:pcv13][0])
    assert_equal(1, @patient_2012[:immunizations][:ipv].length)
    assert_equal(time_2m, @patient_2012[:immunizations][:ipv][0])
  end

  def test_hpv_available
    # A patient born in 2012 should recieve 3 HPV vaccinations before age of 16
    (1..16).each do |years|
      Synthea::Modules::Immunizations.perform_encounter(@patient_2012, @time_2012.advance(:years => years))
    end
    assert_equal(3, @patient_2012[:immunizations][:hpv].length)
  end

  def test_hpv_not_available
    # A patient born in 1947 should never receive the HPV vaccination, even after it's available in 2006
    (1..90).each do |years|
      Synthea::Modules::Immunizations.perform_encounter(@patient_1947, @time_1947.advance(:years => years))
    end
    assert_equal(0, (@patient_1947[:immunizations][:hpv] || []).length) # Robust against nil or [] to represent none
  end

  def test_pcv13_adult
    # A patient born in 1947 should receive one PCV13 vaccination after it's available 2010
    (1..90).each do |years|
      Synthea::Modules::Immunizations.perform_encounter(@patient_1947, @time_1947.advance(:years => years))
    end
    assert_equal(1, @patient_1947[:immunizations][:pcv13].length)
  end

  def test_td_after_available
    # A patient born in 2012 should receive one Td vaccination at age 21 and every 10 years thereafter
    (1..45).each do |years|
      Synthea::Modules::Immunizations.perform_encounter(@patient_2012, @time_2012.advance(:years => years))
    end
    assert_equal(3, @patient_2012[:immunizations][:td].length) # Age 21, 31, and 41
  end

  def test_td_before_available
    # A patient born in 1947 should receive one Td vaccination on their first scheduled date after 1992 (which
    # is in 1998) and every 10 years thereafter
    (1..75).each do |years|
      Synthea::Modules::Immunizations.perform_encounter(@patient_1947, @time_1947.advance(:years => years))
    end
    assert_equal(3, @patient_1947[:immunizations][:td].length) # Year 1998, 2008, 2018
  end

end
