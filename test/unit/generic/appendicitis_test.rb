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

    Synthea::MODULES['appendicitis'] = JSON.parse(File.read(File.join(File.expand_path("../../../../lib/generic/modules", __FILE__), 'appendicitis.json')))
    @context = Synthea::Generic::Context.new('appendicitis')
  end

  def teardown
    Synthea::MODULES.clear
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
    @time = (18.years + 32397368.seconds).since(@time) # seed 9 gives us this delay.
    # DST means that giving a cleaner number in terms of years/months/days may not always match this # of seconds

    @patient.record_synthea.expect(:condition, nil, [:appendicitis, @time])

    @patient.record_synthea.expect(:procedure, nil, [:appendectomy, @time, { 'reason' => :appendicitis }])
    @patient.record_synthea.expect(:condition, nil, [:history_of_appendectomy, @time])

    @patient.record_synthea.expect(:encounter, nil, [:emergency_room_admission, @time, { 'reason' => :appendicitis }])
    @patient.record_synthea.expect(:encounter_end, nil, [:emergency_room_admission, @time])
    @patient.record_synthea.expect(:encounter, nil, [:encounter_inpatient, @time, { 'reason' => :appendicitis }])

    @context.run(@time, @patient)

    assert @patient.record_synthea.verify
  end

  def test_patient_with_rupture
    srand 8765

    @context.run(@time, @patient)
    @time = (45.years + 553275440.seconds).since(@time) # seed 8765 gives us this delay.
    # DST means that giving a cleaner number in terms of years/months/days may not always match this # of seconds

    @patient.record_synthea.expect(:condition, nil, [:appendicitis, @time])
    @patient.record_synthea.expect(:condition, nil, [:rupture_of_appendix, @time])

    @patient.record_synthea.expect(:procedure, nil, [:appendectomy, @time, { 'reason' => :appendicitis }])
    @patient.record_synthea.expect(:condition, nil, [:history_of_appendectomy, @time])

    @patient.record_synthea.expect(:encounter, nil, [:emergency_room_admission, @time, { 'reason' => :appendicitis }])
    @patient.record_synthea.expect(:encounter_end, nil, [:emergency_room_admission, @time])
    @patient.record_synthea.expect(:encounter, nil, [:encounter_inpatient, @time, { 'reason' => :appendicitis }])

    @context.run(@time, @patient)

    assert @patient.record_synthea.verify
  end
end
