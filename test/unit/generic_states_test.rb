require_relative '../test_helper'

class GenericStatesTest < Minitest::Test

  def setup
    @time = Time.now
    @patient = Synthea::Person.new
    @patient[:gender] = 'F'
    @patient.events.create(@time - 35.years, :birth, :birth)
    @patient[:age] = 35
  end

  def test_initial_always_passes
    ctx = get_context('initial_to_terminal.json')
    initial = Synthea::Generic::States::Initial.new(ctx, "Initial")
    assert(initial.process(@time, @patient))
  end

  def test_terminal_never_passes
    ctx = get_context('initial_to_terminal.json')
    terminal = Synthea::Generic::States::Terminal.new(ctx, "Terminal")
    refute(terminal.process(@time, @patient))
    refute(terminal.process(@time + 7.days, @patient))
  end

  def test_guard_passes_when_condition_is_met
    ctx = get_context('guard.json')
    guard = Synthea::Generic::States::Guard.new(ctx, "Gender_Guard")
    assert(guard.process(@time, @patient))
  end

  def test_guard_blocks_when_condition_isnt_met
    ctx = get_context('guard.json')
    guard = Synthea::Generic::States::Guard.new(ctx, "Gender_Guard")
    @patient[:gender] = 'M'
    refute(guard.process(@time, @patient))
  end

  def test_delay_passes_after_exact_time
    ctx = get_context('delay.json')

    # Seconds
    # Purposefully avoiding ActiveSupport helpers (e.g., 2.seconds) to increase difference
    # between the test logic and the real logic that it is testing
    delay = Synthea::Generic::States::Delay.new(ctx, "2_Second_Delay")
    delay.start_time = @time
    refute(delay.process(@time, @patient))
    refute(delay.process(@time + 1, @patient))
    assert(delay.process(@time + 2, @patient))
    assert(delay.process(@time + 3, @patient))

    # Minutes
    delay = Synthea::Generic::States::Delay.new(ctx, "2_Minute_Delay")
    delay.start_time = @time
    refute(delay.process(@time, @patient))
    refute(delay.process(@time + 1*60, @patient))
    assert(delay.process(@time + 2*60, @patient))
    assert(delay.process(@time + 3*60, @patient))

    # Hours
    delay = Synthea::Generic::States::Delay.new(ctx, "2_Hour_Delay")
    delay.start_time = @time
    refute(delay.process(@time, @patient))
    refute(delay.process(@time + 1*60*60, @patient))
    assert(delay.process(@time + 2*60*60, @patient))
    assert(delay.process(@time + 3*60*60, @patient))

    # Days
    # Have to use some ActiveSupport to add days since Ruby doesn't provide an accurate way
    # but still do it a little different than the logic we're testing
    delay = Synthea::Generic::States::Delay.new(ctx, "2_Day_Delay")
    delay.start_time = @time
    refute(delay.process(@time, @patient))
    refute(delay.process(@time.advance(:days => 1), @patient))
    assert(delay.process(@time.advance(:days => 2), @patient))
    assert(delay.process(@time.advance(:days => 3), @patient))

    # Weeks
    delay = Synthea::Generic::States::Delay.new(ctx, "2_Week_Delay")
    delay.start_time = @time
    refute(delay.process(@time, @patient))
    refute(delay.process(@time.advance(:weeks => 1), @patient))
    assert(delay.process(@time.advance(:weeks => 2), @patient))
    assert(delay.process(@time.advance(:weeks => 3), @patient))

    # Months
    delay = Synthea::Generic::States::Delay.new(ctx, "2_Month_Delay")
    delay.start_time = @time
    refute(delay.process(@time, @patient))
    refute(delay.process(@time.advance(:months => 1), @patient))
    assert(delay.process(@time.advance(:months => 2), @patient))
    assert(delay.process(@time.advance(:months => 3), @patient))

    # Years
    delay = Synthea::Generic::States::Delay.new(ctx, "2_Year_Delay")
    delay.start_time = @time
    refute(delay.process(@time, @patient))
    refute(delay.process(@time.advance(:years => 1), @patient))
    assert(delay.process(@time.advance(:years => 2), @patient))
    assert(delay.process(@time.advance(:years => 3), @patient))
  end

  def test_delay_passes_after_time_range
    ctx = get_context('delay.json')

    # Re-seed the random generator so we have deterministic outcomes
    srand 12345

    # Seconds (rand(2..10) = 4)
    delay = Synthea::Generic::States::Delay.new(ctx, "2_To_10_Second_Delay")
    delay.start_time = @time
    refute(delay.process(@time, @patient))
    refute(delay.process(@time + 3, @patient))
    assert(delay.process(@time + 4, @patient))
    assert(delay.process(@time + 5, @patient))

    # Minutes (rand(2..10) = 7)
    delay = Synthea::Generic::States::Delay.new(ctx, "2_To_10_Minute_Delay")
    delay.start_time = @time
    refute(delay.process(@time, @patient))
    refute(delay.process(@time + 6*60, @patient))
    assert(delay.process(@time + 7*60, @patient))
    assert(delay.process(@time + 8*60, @patient))

    # Hours (rand(2..10) = 3)
    delay = Synthea::Generic::States::Delay.new(ctx, "2_To_10_Hour_Delay")
    delay.start_time = @time
    refute(delay.process(@time, @patient))
    refute(delay.process(@time + 2*60*60, @patient))
    assert(delay.process(@time + 3*60*60, @patient))
    assert(delay.process(@time + 4*60*60, @patient))

    # Days (rand(2..10) = 6)
    delay = Synthea::Generic::States::Delay.new(ctx, "2_To_10_Day_Delay")
    delay.start_time = @time
    refute(delay.process(@time, @patient))
    refute(delay.process(@time.advance(:days => 5), @patient))
    assert(delay.process(@time.advance(:days => 6), @patient))
    assert(delay.process(@time.advance(:days => 7), @patient))

    # Weeks (rand(2..10) = 7)
    delay = Synthea::Generic::States::Delay.new(ctx, "2_To_10_Week_Delay")
    delay.start_time = @time
    refute(delay.process(@time, @patient))
    refute(delay.process(@time.advance(:weeks => 6), @patient))
    assert(delay.process(@time.advance(:weeks => 7), @patient))
    assert(delay.process(@time.advance(:weeks => 8), @patient))

    # Months (rand(2..10) = 4)
    delay = Synthea::Generic::States::Delay.new(ctx, "2_To_10_Month_Delay")
    delay.start_time = @time
    refute(delay.process(@time, @patient))
    refute(delay.process(@time.advance(:months => 3), @patient))
    assert(delay.process(@time.advance(:months => 4), @patient))
    assert(delay.process(@time.advance(:months => 5), @patient))

    # Years (rand(2..10) = 3)
    delay = Synthea::Generic::States::Delay.new(ctx, "2_To_10_Year_Delay")
    delay.start_time = @time
    refute(delay.process(@time, @patient))
    refute(delay.process(@time.advance(:years => 2), @patient))
    assert(delay.process(@time.advance(:years => 3), @patient))
    assert(delay.process(@time.advance(:years => 4), @patient))

    # Re-seed the random generator with a new (random) seed
    srand
  end

  def test_wellness_encounter
    # Setup a mock to track calls to the patient record
    # In this case, the record shouldn't be called at all
    @patient.record_synthea = MiniTest::Mock.new

    ctx = get_context('encounter.json')
    encounter = Synthea::Generic::States::Encounter.new(ctx, "Annual_Physical")

    # Shouldn't pass through this state until a wellness encounter happens externally
    refute(encounter.process(@time, @patient))
    @time = @time + 6.months
    refute(encounter.process(@time, @patient))
    @time = @time + 2.weeks
    # Simulate the wellness encounter by calling perform_encounter
    encounter.perform_encounter(@time, @patient, false)
    # Now we should pass through
    assert(encounter.process(@time, @patient))
  end

  def test_wellness_encounter_diagnoses_condition
    # Setup a mock to track calls to the patient record
    @patient.record_synthea = MiniTest::Mock.new

    ctx = get_context('encounter.json')
    # First, onset the Diabetes!
    diabetes = Synthea::Generic::States::ConditionOnset.new(ctx, "Diabetes")
    assert(diabetes.process(@time, @patient))
    ctx.history << diabetes

    # Now process the encounter, waiting until it actually happens
    encounter = Synthea::Generic::States::Encounter.new(ctx, "Annual_Physical_2")
    refute(encounter.process(@time, @patient))
    @time = @time + 6.months
    # Simulate the wellness encounter by calling perform_encounter, which should dx Diabetes
    @patient.record_synthea.expect(:condition, nil, [:diabetes_mellitus, @time])
    encounter.perform_encounter(@time, @patient, false)
    assert(encounter.process(@time, @patient))
    # Verify that Diabetes was added to the record
    @patient.record_synthea.verify
  end

  def test_ed_visit_encounter
    # Setup a mock to track calls to the patient record
    @patient.record_synthea = MiniTest::Mock.new

    ctx = get_context('encounter.json')
    # Non-wellness encounters happen immediately
    encounter = Synthea::Generic::States::Encounter.new(ctx, "ED_Visit")
    @patient.record_synthea.expect(:encounter, nil, [:emergency_room_admission, @time])
    assert(encounter.process(@time, @patient))
    # Verify that the Encounter was added to the record
    @patient.record_synthea.verify
  end

  def test_condition_onset
    # Setup a mock to track calls to the patient record
    # In this case, the record shouldn't be called at all
    @patient.record_synthea = MiniTest::Mock.new

    ctx = get_context('condition_onset.json')
    condition = Synthea::Generic::States::ConditionOnset.new(ctx, "Diabetes")
    # Should pass through this state immediately without calling the record
    assert(condition.process(@time, @patient))
  end

  def test_condition_assigns_entity_attribute
    @patient['Most Recent ED Visit'] = nil
    ctx = get_context('condition_onset.json')
    appendicitis = Synthea::Generic::States::ConditionOnset.new(ctx, "Appendicitis")
    appendicitis.run(@time, @patient)

    assert_equal('rupture_of_appendix', @patient['Most Recent ED Visit'])
  end

  def test_condition_onset_during_encounter
    # Setup a mock to track calls to the patient record
    @patient.record_synthea = MiniTest::Mock.new

    ctx = get_context('condition_onset.json')
    # The encounter comes first (and add it to history)
    encounter = Synthea::Generic::States::Encounter.new(ctx, "ED_Visit")
    @patient.record_synthea.expect(:encounter, nil, [:emergency_room_admission, @time])
    assert(encounter.process(@time, @patient))
    ctx.history << encounter

    # Then appendicitis is diagnosed
    appendicitis = Synthea::Generic::States::ConditionOnset.new(ctx, "Appendicitis")
    @patient.record_synthea.expect(:condition, nil, [:rupture_of_appendix, @time])
    assert(appendicitis.process(@time, @patient))

    # Verify that the Encounter and Condition was added to the record
    @patient.record_synthea.verify
  end

  def test_medication_order_during_wellness_encounter
    # Setup a mock to track calls to the patient record
    @patient.record_synthea = MiniTest::Mock.new

    ctx = get_context('medication_order.json')

    # First, onset the Diabetes!
    diabetes = Synthea::Generic::States::ConditionOnset.new(ctx, "Diabetes")
    assert(diabetes.process(@time, @patient))
    ctx.history << diabetes

    # Process the wellness encounter state, which will wait for a wellness encounter
    encounter = Synthea::Generic::States::Encounter.new(ctx, "Wellness_Encounter")
    refute(encounter.process(@time, @patient))
    @time = @time + 6.months
    # Simulate the wellness encounter by calling perform_encounter
    @patient.record_synthea.expect(:condition, nil, [:diabetes_mellitus, @time])
    encounter.perform_encounter(@time, @patient, false)
    assert(encounter.process(@time, @patient))
    ctx.history << encounter

    # Now process the prescription
    med = Synthea::Generic::States::MedicationOrder.new(ctx, "Metformin")
    @patient.record_synthea.expect(:medication_start, nil, ["24_hr_metformin_hydrochloride_500_mg_extended_release_oral_tablet".to_sym, @time, [:diabetes_mellitus]])
    assert(med.process(@time, @patient))

    # Verify that Metformin was added to the record
    @patient.record_synthea.verify
  end

  def test_medication_order_assigns_entity_attribute
    @patient['Diabetes Medication'] = nil
    ctx = get_context('medication_order.json')
    med = Synthea::Generic::States::MedicationOrder.new(ctx, "Metformin")
    med.run(@time, @patient)

    assert_equal("24_hr_metformin_hydrochloride_500_mg_extended_release_oral_tablet", @patient['Diabetes Medication'])
  end

  def test_medication_end_by_entity_attribute
    # Setup a mock to track calls to the patient record
    @patient.record_synthea = MiniTest::Mock.new

    ctx = get_context('medication_end.json')

    # First, onset the Diabetes!
    diabetes = Synthea::Generic::States::ConditionOnset.new(ctx, "Diabetes")
    assert(diabetes.process(@time, @patient))
    ctx.history << diabetes

    # Process the wellness encounter state, which will wait for a wellness encounter
    encounter = Synthea::Generic::States::Encounter.new(ctx, "Wellness_Encounter")
    refute(encounter.process(@time, @patient))
    @time = @time + 6.months
    # Simulate the wellness encounter by calling perform_encounter
    @patient.record_synthea.expect(:condition, nil, [:diabetes_mellitus, @time])
    encounter.perform_encounter(@time, @patient, false)
    assert(encounter.process(@time, @patient))
    ctx.history << encounter

    medication_id = "insulin,_aspart,_human_100_unt/ml_[novolog]".to_sym

    # Now process the prescription
    med = Synthea::Generic::States::MedicationOrder.new(ctx, "Insulin_Start")
    @patient.record_synthea.expect(:medication_start, nil, [medication_id, @time, [:diabetes_mellitus]])
    assert(med.run(@time, @patient)) # have to use run not process here because the entity attribute stuff happens in run

    ctx.history << med

    # Now process the end of the prescription
    med_end = Synthea::Generic::States::MedicationEnd.new(ctx, "Insulin_End")
    @patient.record_synthea.expect(:medication_stop, nil, [medication_id, @time, [:prescription_expired]])
    assert(med_end.process(@time, @patient))

    @patient.record_synthea.verify
  end

  def test_medication_end_by_medication_order
    # Setup a mock to track calls to the patient record
    @patient.record_synthea = MiniTest::Mock.new

    ctx = get_context('medication_end.json')

    # First, onset the Diabetes!
    diabetes = Synthea::Generic::States::ConditionOnset.new(ctx, "Diabetes")
    assert(diabetes.process(@time, @patient))
    ctx.history << diabetes

    # Process the wellness encounter state, which will wait for a wellness encounter
    encounter = Synthea::Generic::States::Encounter.new(ctx, "Wellness_Encounter")
    refute(encounter.process(@time, @patient))
    @time = @time + 6.months
    # Simulate the wellness encounter by calling perform_encounter
    @patient.record_synthea.expect(:condition, nil, [:diabetes_mellitus, @time])
    encounter.perform_encounter(@time, @patient, false)
    assert(encounter.process(@time, @patient))
    ctx.history << encounter

    medication_id = "bromocriptine_5_mg_[parlodel]".to_sym

    # Now process the prescription
    med = Synthea::Generic::States::MedicationOrder.new(ctx, "Bromocriptine_Start")
    @patient.record_synthea.expect(:medication_start, nil, [medication_id, @time, [:diabetes_mellitus]])
    assert(med.process(@time, @patient))

    ctx.history << med

    # Now process the end of the prescription
    med_end = Synthea::Generic::States::MedicationEnd.new(ctx, "Bromocriptine_End")
    @patient.record_synthea.expect(:medication_stop, nil, [medication_id, @time, [:prescription_expired]])
    assert(med_end.process(@time, @patient))

    @patient.record_synthea.verify
  end

  def test_medication_end_by_code
    # Setup a mock to track calls to the patient record
    @patient.record_synthea = MiniTest::Mock.new

    ctx = get_context('medication_end.json')

    # First, onset the Diabetes!
    diabetes = Synthea::Generic::States::ConditionOnset.new(ctx, "Diabetes")
    assert(diabetes.process(@time, @patient))
    ctx.history << diabetes

    # Process the wellness encounter state, which will wait for a wellness encounter
    encounter = Synthea::Generic::States::Encounter.new(ctx, "Wellness_Encounter")
    refute(encounter.process(@time, @patient))
    @time = @time + 6.months
    # Simulate the wellness encounter by calling perform_encounter
    @patient.record_synthea.expect(:condition, nil, [:diabetes_mellitus, @time])
    encounter.perform_encounter(@time, @patient, false)
    assert(encounter.process(@time, @patient))
    ctx.history << encounter

    medication_id = "24_hr_metformin_hydrochloride_500_mg_extended_release_oral_tablet".to_sym

    # Now process the prescription
    med = Synthea::Generic::States::MedicationOrder.new(ctx, "Metformin_Start")
    @patient.record_synthea.expect(:medication_start, nil, [medication_id, @time, [:diabetes_mellitus]])
    assert(med.process(@time, @patient))

    ctx.history << med

    # Now process the end of the prescription
    med_end = Synthea::Generic::States::MedicationEnd.new(ctx, "Metformin_End")
    @patient.record_synthea.expect(:medication_stop, nil, [medication_id, @time, [:prescription_expired]])
    assert(med_end.process(@time, @patient))

    @patient.record_synthea.verify
  end


  def test_condition_end_by_entity_attribute
    # Setup a mock to track calls to the patient record
    @patient.record_synthea = MiniTest::Mock.new

    ctx = get_context('condition_end.json')

    # First, onset the condition
    condition1 = Synthea::Generic::States::ConditionOnset.new(ctx, "Condition1_Start")
    assert(condition1.run(@time, @patient))
    ctx.history << condition1

    condition_id = 'chases_the_dragon_(finding)'.to_sym

    # Process the wellness encounter state, which will wait for a wellness encounter
    encounter = Synthea::Generic::States::Encounter.new(ctx, "DiagnosisEncounter")
    refute(encounter.process(@time, @patient))
    @time = @time + 6.months
    # Simulate the wellness encounter by calling perform_encounter
    @patient.record_synthea.expect(:condition, nil, [condition_id, @time])
    encounter.perform_encounter(@time, @patient, false)
    assert(encounter.process(@time, @patient))
    ctx.history << encounter

    assert_equal(condition_id, @patient['Drug Use Behavior'].to_sym)

    # Now process the end of the condition
    con_end = Synthea::Generic::States::ConditionEnd.new(ctx, "Condition1_End")
    @patient.record_synthea.expect(:end_condition, nil, [condition_id, @time])
    assert(con_end.process(@time, @patient))

    @patient.record_synthea.verify
  end

  def test_condition_end_by_medication_order
    # Setup a mock to track calls to the patient record
    @patient.record_synthea = MiniTest::Mock.new

    ctx = get_context('condition_end.json')

    # First, onset the condition
    condition2 = Synthea::Generic::States::ConditionOnset.new(ctx, "Condition2_Start")
    assert(condition2.run(@time, @patient))
    ctx.history << condition2

    condition_id = 'influenza'.to_sym

    # Process the wellness encounter state, which will wait for a wellness encounter
    encounter = Synthea::Generic::States::Encounter.new(ctx, "DiagnosisEncounter")
    refute(encounter.process(@time, @patient))
    @time = @time + 6.months
    # Simulate the wellness encounter by calling perform_encounter
    @patient.record_synthea.expect(:condition, nil, [condition_id, @time])
    encounter.perform_encounter(@time, @patient, false)
    assert(encounter.process(@time, @patient))
    ctx.history << encounter

    # Now process the end of the condition
    con_end = Synthea::Generic::States::ConditionEnd.new(ctx, "Condition2_End")
    @patient.record_synthea.expect(:end_condition, nil, [condition_id, @time])
    assert(con_end.process(@time, @patient))

    @patient.record_synthea.verify
  end

  def test_condition_end_by_code
    # Setup a mock to track calls to the patient record
    @patient.record_synthea = MiniTest::Mock.new

    ctx = get_context('condition_end.json')

    # First, onset the Diabetes!
    condition3 = Synthea::Generic::States::ConditionOnset.new(ctx, "Condition3_Start")
    assert(condition3.run(@time, @patient))
    ctx.history << condition3

    condition_id = 'diabetes_mellitus'.to_sym

    # Process the wellness encounter state, which will wait for a wellness encounter
    encounter = Synthea::Generic::States::Encounter.new(ctx, "DiagnosisEncounter")
    refute(encounter.process(@time, @patient))
    @time = @time + 6.months
    # Simulate the wellness encounter by calling perform_encounter
    @patient.record_synthea.expect(:condition, nil, [condition_id, @time])
    encounter.perform_encounter(@time, @patient, false)
    assert(encounter.process(@time, @patient))
    ctx.history << encounter

    # Now process the end of the condition
    con_end = Synthea::Generic::States::ConditionEnd.new(ctx, "Condition3_End")
    @patient.record_synthea.expect(:end_condition, nil, [condition_id, @time])
    assert(con_end.process(@time, @patient))

    @patient.record_synthea.verify
  end

  def test_setAttribute_with_value
    ctx = get_context('set_attribute.json')

    @patient['Current Opioid Prescription'] = nil
    set1 = Synthea::Generic::States::SetAttribute.new(ctx, "Set_Attribute_1")
    assert(set1.process(@time, @patient))

    assert_equal('Vicodin', @patient['Current Opioid Prescription'])
  end

  def test_setAttribute_without_value
    ctx = get_context('set_attribute.json')

    @patient['Current Opioid Prescription'] = 'Vicodin'
    set2 = Synthea::Generic::States::SetAttribute.new(ctx, "Set_Attribute_2")
    assert(set2.process(@time, @patient))

    assert_equal(nil, @patient['Current Opioid Prescription'])
  end

  def test_procedure_assigns_entity_attribute
    @patient['Most Recent Surgery'] = 'nil'
    ctx = get_context('procedure.json')
    appendectomy = Synthea::Generic::States::Procedure.new(ctx, "Appendectomy")
    appendectomy.run(@time, @patient)

    assert_equal("laparoscopic_appendectomy", @patient['Most Recent Surgery'])
  end

  def test_procedure_during_encounter
    # Setup a mock to track calls to the patient record
    @patient.record_synthea = MiniTest::Mock.new

    ctx = get_context('procedure.json')

    # The encounter comes first (and add it to history)
    encounter = Synthea::Generic::States::Encounter.new(ctx, "Inpatient_Encounter")
    @patient.record_synthea.expect(:encounter, nil, [:hospital_admission, @time])
    assert(encounter.process(@time, @patient))
    ctx.history << encounter

    # Then have the appendectomy
    appendectomy = Synthea::Generic::States::Procedure.new(ctx, "Appendectomy")
    @patient.record_synthea.expect(:procedure, nil, [:laparoscopic_appendectomy, @time, nil])
    assert(appendectomy.process(@time, @patient))

    # Verify that the procedure was added to the record
    @patient.record_synthea.verify
  end

  def test_observation
    # Setup a mock to track calls to the patient record
    @patient.record_synthea = MiniTest::Mock.new

    ctx = get_context('observation.json')

    # The encounter comes first (and add it to history)
    encounter = Synthea::Generic::States::Encounter.new(ctx, "SomeEncounter")
    @patient.record_synthea.expect(:encounter, nil, [:hospital_admission, @time])
    assert(encounter.process(@time, @patient))
    ctx.history << encounter

    obs = Synthea::Generic::States::Observation.new(ctx, "SomeObservation")
    @patient.record_synthea.expect(:observation, nil, [:blood_pressure, @time, "120/80"])
    assert(obs.process(@time, @patient))

    # Verify that the procedure was added to the record
    @patient.record_synthea.verify
  end

  def test_symptoms
    ctx = get_context('symptom.json')

    symptom1 = Synthea::Generic::States::Symptom.new(ctx, "SymptomOnset")
    assert(symptom1.process(@time, @patient))
    assert_includes(1..10, @patient.get_symptom_value('Chest Pain'))

    symptom2 = Synthea::Generic::States::Symptom.new(ctx, "SymptomWorsen")
    assert(symptom2.process(@time, @patient))
    assert_equal(96, @patient.get_symptom_value('Chest Pain'))
  end

  def test_death
    # Setup a mock to track calls to the patient record
    @patient.record_synthea = MiniTest::Mock.new

    ctx = get_context('death.json')
    death = Synthea::Generic::States::Death.new(ctx, "Death")
    @patient.record_synthea.expect(:death, nil, [@time])
    assert(@patient.alive?)
    assert(death.process(@time, @patient))

    # Patient shouldn't be alive anymore
    refute(@patient.alive?)

    # Verify that death was added to the record
    @patient.record_synthea.verify
  end

  def test_future_death
    ctx = get_context('death_life_expectancy.json')
    ctx.run(@time, @patient)
    ctx.run(@time.advance(days: 7), @patient)

    assert(@patient.alive?(@time.advance(days: 7)))
    assert(@patient['processing'])
    refute(@patient['still_processing'])

    ctx.run(@time.advance(days: 14), @patient)
    assert(@patient['still_processing'])

    ctx.run(@time.advance(months: 6), @patient)
    refute(@patient.alive?(@time.advance(months: 6)))
  end

  def get_context(file_name)
    cfg = JSON.parse(File.read(File.join(File.expand_path("../../fixtures/generic", __FILE__), file_name)))
    Synthea::Generic::Context.new(cfg)
  end
end
