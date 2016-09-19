require_relative '../test_helper'

class RecordTest < Minitest::Test
  def setup
    @time = Time.now
    @record = Synthea::Output::Record.new
    @record.medication_start(:warfarin, @time, [:prediabetes, :coronary_heart_disease])
    @record.medication_start(:sglt2i, @time, [:diabetes, :coronary_heart_disease])
    @record.careplan_start(:diabetes, [:exercise, :diabetic_diet], @time, [:diabetes])
    @record.careplan_start(:cardiovascular_disease, [:stress_management, :stop_smoking], @time, [:coronary_heart_disease])
  end

  def test_medication_stop
    @record.medication_stop(:sglt2i, @time + 15.minutes, :diabetes_well_controlled)
    assert_equal(@time + 15.minutes,@record.medications[1]['stop'])
    assert_equal(:diabetes_well_controlled, @record.medications[1]['stop_reason'])
    #test the first one is passed over and not modified
    @record.medication_start(:sglt2i, @time + 20.minutes, [:diabetes, :coronary_heart_disease])
    @record.medication_stop(:sglt2i, @time + 30.minutes, :diabetes_well_controlled)
    assert_equal(@time + 15.minutes,@record.medications[1]['stop'])
    assert_equal(:diabetes_well_controlled, @record.medications[1]['stop_reason'])
  end

  def test_medication_active?
    assert(@record.medication_active?(:sglt2i))
    @record.medication_stop(:sglt2i, @time + 15.minutes, :diabetes_well_controlled)
    assert(!@record.medication_active?(:sglt2i))
  end

  def test_update_med_reasons
    assert_equal([:prediabetes, :coronary_heart_disease], @record.medications[0]['reasons'])
    @record.update_med_reasons(:warfarin, [:prediabetes, :coronary_heart_disease, :diabetes], @time+15.minutes)
    assert_equal(@time+15.minutes, @record.medications[0]['time'])
    assert_equal([:prediabetes, :coronary_heart_disease, :diabetes], @record.medications[0]['reasons'])
    #test it was passed over when stopped.
    @record.medication_stop(:warfarin, @time + 30.minutes, :diabetes_well_controlled)
    @record.update_med_reasons(:warfarin, [:cardiac_arrest], @time+25.minutes)
    assert_equal(@time+15.minutes, @record.medications[0]['time'])
    assert_equal([:prediabetes, :coronary_heart_disease, :diabetes], @record.medications[0]['reasons'])
  end

  def test_careplan_stop
    @record.careplan_stop(:diabetes, @time + 5.minutes)
    assert_equal(@time+5.minutes, @record.careplans[0]['stop'])
    assert_equal(nil, @record.careplans[1]['stop'])
    @record.careplan_start(:diabetes, [:exercise, :diabetic_diet], @time+10.minutes, :diabetes)
    @record.careplan_stop(:diabetes, @time + 15.minutes)
    assert_equal(@time+5.minutes, @record.careplans[0]['stop'])
  end

  def test_careplan_active
    assert(@record.careplan_active?(:cardiovascular_disease))
    @record.careplan_stop(:cardiovascular_disease,  @time + 15.minutes)
    assert(!@record.careplan_active?(:cardiovascular_disease))
  end

  def test_update_careplan_reasons
    assert_equal([:diabetes], @record.careplans[0]['reasons'])
    @record.update_careplan_reasons(:diabetes, [:diabetes, :prediabetes, :cardiac_arrest], @time+5.minutes)
    assert_equal([:diabetes, :prediabetes, :cardiac_arrest], @record.careplans[0]['reasons'])
    @record.careplan_stop(:diabetes, @time + 15.minutes)
    @record.update_careplan_reasons(:diabetes, [:myocardial_infarction], @time+5.minutes)
    assert_equal([:diabetes, :prediabetes, :cardiac_arrest], @record.careplans[0]['reasons'])
  end
end
