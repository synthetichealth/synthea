require_relative '../../test_helper'

class AppendicitisTest < Minitest::Test
  def setup
    @time = Time.now
    @patient = Synthea::Person.new
    @patient[:gender] = 'F'
    @patient.events.create(@time - 35.years, :birth, :birth)
    @patient[:age] = 35
    # Setup a mock to track calls to the patient record
    @patient.record_synthea = MiniTest::Mock.new

    cfg = JSON.parse(File.read(File.join(File.expand_path("../../../../lib/generic/modules", __FILE__), 'appendicitis.json')))
    @context = Synthea::Generic::Context.new(cfg)
  end

  def test_patient_without_appendicitis
    srand 123

    @context.run(@time, @patient)

    @context.run(@time.advance(years: 65), @patient)

    assert @patient.record_synthea.verify
  end

  def test_patient_with_appendicitis
    srand 9

    @context.run(@time, @patient)
    @time = @time.advance(years: 40)

    @patient.record_synthea.expect(:condition, nil, [:appendicitis, @time])

    @patient.record_synthea.expect(:procedure, nil, [:appendectomy, @time, :appendicitis])
    @patient.record_synthea.expect(:condition, nil, [:history_of_appendectomy, @time])

    @patient.record_synthea.expect(:encounter, nil, [:emergency_room_admission, @time])
    @patient.record_synthea.expect(:encounter, nil, [:encounter_inpatient, @time])

    @context.run(@time, @patient)

    assert @patient.record_synthea.verify
  end

  def test_patient_with_rupture
    srand 8765

    @context.run(@time, @patient)
    @time = @time.advance(years: 61)

    @patient.record_synthea.expect(:condition, nil, [:appendicitis, @time])
    @patient.record_synthea.expect(:condition, nil, [:rupture_of_appendix, @time])

    @patient.record_synthea.expect(:procedure, nil, [:appendectomy, @time, :appendicitis])
    @patient.record_synthea.expect(:condition, nil, [:history_of_appendectomy, @time])

    @patient.record_synthea.expect(:encounter, nil, [:emergency_room_admission, @time])
    @patient.record_synthea.expect(:encounter, nil, [:encounter_inpatient, @time])
    @context.run(@time, @patient)

    assert @patient.record_synthea.verify
  end
end
