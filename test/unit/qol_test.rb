require_relative '../test_helper'

class QOLTest < Minitest::Test

  def setup
    @time = Time.now
    Synthea::Config.end_time = @time
    @patient = Synthea::Person.new
    @patient.events.create(@time - 35.years, :birth, :birth)
    @patient[:age] = 35
    @patient[:age_mos] = 420

    # dw ADD = 0.045; asthma = 0.015, diabetes = 0.049
    #                   asthma
    #             |-----------------|
    #               ADD            diabetes
    #             |-----|     |-----------------|
    # |-----|-----|-----|-----|-----|-----|-----|
    # 0     5     10    15    20    25    30    35    
    @patient.record_synthea.condition(:child_attention_deficit_disorder, @time - 25.years)
    @patient.record_synthea.condition(:asthma, @time - 25.years)
    @patient.record_synthea.end_condition(:child_attention_deficit_disorder, @time - 20.years)
    @patient.record_synthea.condition(:diabetes, @time - 15.years)
    @patient.record_synthea.end_condition(:asthma, @time - 10.years)

    @qol_calculator = Synthea::Output::QOL.new
  end

  def test_calculate
    # living patient
    @qol_calculator.calculate(@patient)
    daly_living = @patient.record_synthea.observations[0]['value']
    qaly_living = @patient.record_synthea.observations[1]['value']
    # daly (without age weight) = 5*0.045 + 15*0.015 + 15*0.049 = 1.185
    assert(daly_living.between?(1.7, 1.8))
    assert(qaly_living.between?(33, 34))

    # dead patient
    @patient.events.create(@time, :death, :death)
    @qol_calculator.calculate(@patient)
    daly_dead = @patient.record_synthea.observations[2]['value']
    qaly_dead = @patient.record_synthea.observations[3]['value']
    # yll = 52.3605; daly (without age wieght) = 52.3605 + 1.185 = 53.5455
    assert(daly_dead.between?(54, 55))
    assert(qaly_dead.between?(33, 34))
  end

  def test_calculate_with_age
    @qol_calculator.calculate(@patient, @time - 20.years)
    daly = @patient.record_synthea.observations[0]['value']
    qaly = @patient.record_synthea.observations[1]['value']
    # daly (without age weight) = 5*0.045 + 10*0.015 = 0.375
    assert(daly.between?(0.38, 0.39))
    assert(qaly.between?(14, 15))
  end
  
  def test_conditions_in_year
    conditions = @patient.record_synthea.conditions
    # conditions in year 5
    conditions_year_5 = @qol_calculator.conditions_in_year(conditions, @time - 30.years, @time - 29.years)
    assert_equal([], conditions_year_5)
    # conditions in year 10
    conditions_year_10 = @qol_calculator.conditions_in_year(conditions, @time - 25.years, @time - 24.years)
    assert_equal(2, conditions_year_10.length)
    assert_equal(:child_attention_deficit_disorder, conditions_year_10[0]['type'])
    assert_equal(:asthma, conditions_year_10[1]['type'])
  end
  
  def test_weight
    # age 15 with disability weight of 0.45
    weight = @qol_calculator.weight(0.45, 15)
    assert(weight.between?(0.614, 0.615))
  end
end
