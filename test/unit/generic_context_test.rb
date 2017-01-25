require_relative '../test_helper'

class GenericContextTest < Minitest::Test

  def setup
    @time = Time.now
    @patient = Synthea::Person.new
    @patient[:gender] = 'F'
    @patient.events.create(@time - 35.years, :birth, :birth)
    @patient[:age] = 35
  end

  def teardown
    Synthea::MODULES.clear
  end

  def test_new_context
    ctx = get_context('example_module.json')
    assert_equal("Examplitis", ctx.name)
    assert_equal("example_module", ctx.current_module)
    assert_equal("Initial", ctx.current_state.name)
    assert_equal(nil, ctx.current_encounter)
    assert_equal([], ctx.history)
    assert(ctx.active?)
    refute(ctx.active_submodule?)
  end

  def test_direct_transition
    ctx = get_context('direct_transition.json')
    assert_equal("Initial", ctx.current_state.name)
    ctx.run(@time, @patient)
    assert_equal("Terminal", ctx.current_state.name)
  end

  def test_distributed_transition
    # this is built on rand, so we must seed it to get predictable results
    srand 190

    results = {
      "Terminal1" => 0,
      "Terminal2" => 0,
      "Terminal3" => 0
    }

    # Over the course of 100 runs, we should get about the expected distribution
    100.times do
      ctx = get_context('distributed_transition.json')
      ctx.run(@time, @patient)
      results[ctx.current_state.name] += 1
    end
    assert_equal(15, results["Terminal1"])
    assert_equal(55, results["Terminal2"])
    assert_equal(30, results["Terminal3"])

    # Re-seed the random generator with a new (random) seed
    srand
  end

  def test_conditional_transition
    # First run as a male
    @patient[:gender] = 'M'
    ctx = get_context('conditional_transition.json')
    ctx.run(@time, @patient)
    assert_equal("Terminal1", ctx.current_state.name)

    # Then run as a female
    @patient[:gender] = 'F'
    ctx = get_context('conditional_transition.json')
    assert_equal("Initial", ctx.current_state.name)
    ctx.run(@time, @patient)
    assert_equal("Terminal2", ctx.current_state.name)

    # Then run as unknown
    @patient[:gender] = 'U'
    ctx = get_context('conditional_transition.json')
    assert_equal("Initial", ctx.current_state.name)
    ctx.run(@time, @patient)
    assert_equal("Terminal3", ctx.current_state.name)
  end

  def test_incomplete_conditional_transition
    # First run as a male
    @patient[:gender] = 'M'
    ctx = get_context('incomplete_conditional_transition.json')
    assert_equal("Initial", ctx.current_state.name)
    ctx.run(@time, @patient)
    assert_equal("Terminal1", ctx.current_state.name)

    # Then run as a female (which shouldn't be caught by any transition)
    @patient[:gender] = 'F'
    ctx = get_context('incomplete_conditional_transition.json')
    assert_equal("Initial", ctx.current_state.name)
    ctx.run(@time, @patient)
    assert_equal("Terminal", ctx.current_state.name)
  end

  def test_complex_transition
      # First run as a male
    @patient[:gender] = 'M'
    ctx = get_context('complex_transition.json')
    assert_equal("Initial", ctx.current_state.name)
    ctx.run(@time, @patient)
    assert(ctx.current_state.name.start_with?("TerminalM"))

    # Then run as a female
    @patient[:gender] = 'F'
    ctx = get_context('complex_transition.json')
    assert_equal("Initial", ctx.current_state.name)
    ctx.run(@time, @patient)
    assert(ctx.current_state.name.start_with?("TerminalF"))

  end

  def test_history
    # seed rand so we have deterministic results
    srand 3

    ctx = get_context('history.json')
    assert_equal("Initial", ctx.current_state.name)
    ctx.run(@time, @patient)
    assert_equal("Terminal", ctx.current_state.name)
    assert_equal(5, ctx.history.length)
    assert_equal("Initial", ctx.history[0].name)
    assert_equal("Guard1", ctx.history[1].name)
    assert_equal("Guard2", ctx.history[2].name)
    # Transition from Guard2 -> Guard2 is condensed into a single entry in history
    assert_equal("Guard1", ctx.history[3].name)
    assert_equal("Guard2", ctx.history[4].name)
  end

  def test_delay_time_accuracy
    # Synthea is currently run in 7-day increments.  If a delay falls between increments, then the delay and subsequent
    # states must be run at the delay expiration time -- not at the current cycle time.  It is the job of the context
    # runner to ensure this is satisfied.

    # Setup a mock to track calls to the patient record
    @patient.record_synthea = MiniTest::Mock.new

    # Setup the context
    ctx = get_context('delay_time_travel.json')
    assert_equal("Initial", ctx.current_state.name)

    # Run number one should stop at the delay
    ctx.run(@time, @patient)
    assert_equal("2_Day_Delay", ctx.current_state.name)

    # Run number two should go all the way to Terminal, but should process Encounter and Death along the way
    # Ensure that the encounter really happens 2 days after the initial run
    @patient.record_synthea.expect(:encounter, nil, [:emergency_room_admission, @time.advance(:days => 2), {}])
    # Ensure that death really happens 2 + 3 days after the initial run
    @patient.record_synthea.expect(:death, nil, [@time.advance(:days => 5)])
    # Run number 2: 7 days after run number 1
    ctx.run(@time.advance(:days => 7), @patient)
    assert_equal("Terminal", ctx.current_state.name)

    assert_equal(5, ctx.history.length)
    assert_equal("Initial", ctx.history[0].name)
    assert_equal(@time, ctx.history[0].entered)
    assert_equal(@time, ctx.history[0].exited)

    assert_equal("2_Day_Delay", ctx.history[1].name)
    assert_equal(@time, ctx.history[1].entered)
    assert_equal(@time.advance(days: 2), ctx.history[1].exited)

    assert_equal("ED_Visit", ctx.history[2].name)
    assert_equal(@time.advance(days: 2), ctx.history[2].entered)
    assert_equal(@time.advance(days: 2), ctx.history[2].exited)

    assert_equal("3_Day_Delay", ctx.history[3].name)
    assert_equal(@time.advance(days: 2), ctx.history[3].entered)
    assert_equal(@time.advance(days: 5), ctx.history[3].exited)

    assert_equal("Death", ctx.history[4].name)
    assert_equal(@time.advance(days: 5), ctx.history[4].entered)
    assert_equal(@time.advance(days: 5), ctx.history[4].exited)

    assert_equal("Terminal", ctx.current_state.name)
    assert_equal(@time.advance(days: 5), ctx.current_state.entered)
    assert_equal(nil, ctx.current_state.exited)
  end

  def test_call_submodule
    ctx = get_context('calls_submodule.json')
    load_module(File.join('submodules', 'basic_submodule.json'))

    assert_equal("Initial", ctx.current_state.name)
    assert(ctx.active?)
    refute(ctx.active_submodule?)

    # Eventually blocks at a guard state in the submodule
    ctx.run(@time, @patient)
    assert(ctx.active?)
    assert(ctx.active_submodule?)
    assert_equal('Encounter', ctx.current_encounter)
    assert_equal(6, ctx.history.length)
    assert_equal("MedicationOrder", ctx.history.last.name)
    # The wellness state hasn't been processed yet
    assert_equal("Gender_Guard", ctx.current_state.name)

    # change gender to satisfy the condition and resume execution
    @patient[:gender] = 'M'
    ctx.run(@time, @patient)

    # The module should have finished executing. The last state should be
    # the Terminal state of the submodule.
    refute(ctx.active?)
    assert_equal("Sub_Terminal", ctx.history.last.name)
  end

  def test_recursive_call_submodule
    ctx = get_context('recursively_calls_submodules.json')
    load_module(File.join('submodules', 'encounter_submodule.json'))
    load_module(File.join('submodules', 'medication_submodule.json'))

    assert_equal("Initial", ctx.current_state.name)
    assert(ctx.active?)
    refute(ctx.active_submodule?)

    # Should block in the encounter submodule, before the encounter
    ctx.run(@time, @patient)
    assert(ctx.active_submodule?)
    assert_equal(nil, ctx.current_encounter)
    assert_equal("Delay", ctx.current_state.name)
    assert_equal("Initial", ctx.history.last.name) # the submodule's initial state

    # Should block in the sub-submodule, after the MedicationOrder
    @time += 1.years
    med_stop_time = @time + 2.weeks
    ctx.run(@time, @patient)
    assert(ctx.active_submodule?)
    assert_equal("Encounter_In_Submodule", ctx.current_encounter)
    assert_equal("Delay_Yet_Again", ctx.current_state.name)
    assert_equal("Examplitis_Medication", ctx.history.last.name)

    # Should run back to the encounter submodule and block before its terminal state
    @time += 2.weeks
    ctx.run(@time, @patient)
    assert(ctx.active_submodule?)
    assert_equal("Delay_Some_More", ctx.current_state.name)
    assert_equal("Med_Terminal", ctx.history.last.name)

    # Should run to completion after this last Delay, ending the condition
    # and the medication.
    @time += 4.weeks
    ctx.run(@time, @patient)
    refute(ctx.active?)
    refute(ctx.active_submodule?)
    assert_equal("End_Condition", ctx.history.last.name)

    # Check that the patient's record was updated correctly
    cond = @patient.record_synthea.conditions.last
    assert_equal(:examplitis, cond['type'])
    assert_equal(@time, cond['end_time'])

    enc = @patient.record_synthea.encounters.last
    assert_equal(:examplitis, enc['reason'])

    med = @patient.record_synthea.medications.last
    assert_equal(:examplitis, med['reasons'][0])
    assert_equal(med_stop_time, med['stop'])

    # All should have started concurrently
    assert_equal(cond['time'], enc['time'])
    assert_equal(enc['time'], med['time'])
  end

  def test_logging
    # Can't really test the quality of the logs, but at least ensure it logs when it should and that nothing crashes
    old_log_value = Synthea::Config.generic.log

    # First check it doesn't log when it shouldn't
    Synthea::Config.generic.log = false
    ctx = get_context('allergies.json')
    ctx.run(@time, @patient)
    assert_equal("Allergy_Ends", ctx.history.last.name)
    refute(ctx.logged)

    # Then check it does log when it should
    Synthea::Config.generic.log = true
    ctx = get_context('allergies.json')
    ctx.run(@time, @patient)
    assert_equal("Allergy_Ends", ctx.history.last.name)
    assert(ctx.logged)

    # Set the log value back
    Synthea::Config.generic.log = old_log_value
  end

  def get_context(file)
    key = load_module(file)
    Synthea::Generic::Context.new(key)
  end

  def load_module(file)
    module_dir = File.expand_path('../../fixtures/generic/', __FILE__)
    # loads a module into the global MODULES hash given an absolute path
    key = module_key(file)
    Synthea::MODULES[key] = JSON.parse(File.read(File.join(module_dir, file)))
    key
  end

  def module_key(file)
    file.sub('.json', '')
  end
end
