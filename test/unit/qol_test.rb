require_relative '../test_helper'

class QOLTest < Minitest::Test

  def setup
    @time = Time.now
    @patient = Synthea::Person.new
    @patient.events.create(@time - 35.years, :birth, :birth)
    @patient[:age] = 35
    @patient[:age_mos] = 420

    # dw for asthma is 0.015, dw for dabetees is 0.049
    @patient.record_synthea.condition(:asthma, @time - 25.years)
    @patient.record_synthea.end_condition(:asthma, @time - 20.years)
    @patient.record_synthea.condition(:diabetes, @time - 15.years)

    @gbd_calculator = Synthea::Output::QOL.new
  end

  def test_calculate_daly
    daly = @gbd_calculator.calculate_daly(@patient, @time)
    # daly = 0.015 * 5 + 0.049 * 15 = 0.81
    assert(daly.between?(0.81, 0.82))
  end

  def test_calculate_daly_with_end_time
    daly = @gbd_calculator.calculate_daly(@patient, @time - 21.years)
    # daly = 0.015 * 4 = 0.06
    assert(daly.between?(0.06, 0.061))
  end

  def test_calculate_qaly
    daly = @gbd_calculator.calculate_daly(@patient, @time)
    qaly = @gbd_calculator.calculate_qaly(@patient, daly, @time)
    # qaly = 0.566 at age 35
    assert(qaly.between?(0.566, 0.567))
  end

  def test_calculate_qaly_with_end_time
    daly = @gbd_calculator.calculate_daly(@patient, @time - 21.years)
    qaly = @gbd_calculator.calculate_qaly(@patient, daly, @time - 21.years)
    # daly = 0.045 at age 14
    assert(qaly.between?(0.045, 0.046))
  end
end
