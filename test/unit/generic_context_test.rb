require_relative '../test_helper'

class GenericContextTest < Minitest::Test

  def setup
    @time = Time.now
    @patient = Synthea::Person.new
    @patient[:gender] = 'F'
    @patient.events.create(@time - 35.years, :birth, :birth)
    @patient[:age] = 35
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
    cfg = get_config('distributed_transition.json')

    # Over the course of 100 runs, we should get about the expected distribution
    100.times do
      ctx = Synthea::Generic::Context.new(cfg)
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
    cfg = get_config('conditional_transition.json')

    # First run as a male
    @patient[:gender] = 'M'
    ctx = Synthea::Generic::Context.new(cfg)
    assert_equal("Initial", ctx.current_state.name)
    ctx.run(@time, @patient)
    assert_equal("Terminal1", ctx.current_state.name)

    # Then run as a female
    @patient[:gender] = 'F'
    ctx = Synthea::Generic::Context.new(cfg)
    assert_equal("Initial", ctx.current_state.name)
    ctx.run(@time, @patient)
    assert_equal("Terminal2", ctx.current_state.name)

    # Then run as unknown
    @patient[:gender] = 'U'
    ctx = Synthea::Generic::Context.new(cfg)
    assert_equal("Initial", ctx.current_state.name)
    ctx.run(@time, @patient)
    assert_equal("Terminal3", ctx.current_state.name)
  end

  def test_incomplete_conditional_transition
    cfg = get_config('incomplete_conditional_transition.json')

    # First run as a male
    @patient[:gender] = 'M'
    ctx = Synthea::Generic::Context.new(cfg)
    assert_equal("Initial", ctx.current_state.name)
    ctx.run(@time, @patient)
    assert_equal("Terminal1", ctx.current_state.name)

    # Then run as a female (which shouldn't be caught by any transition)
    @patient[:gender] = 'F'
    ctx = Synthea::Generic::Context.new(cfg)
    assert_equal("Initial", ctx.current_state.name)
    ctx.run(@time, @patient)
    assert_equal("Terminal", ctx.current_state.name)
  end

  def test_complex_transition
    cfg = get_config('complex_transition.json')

      # First run as a male
    @patient[:gender] = 'M'
    ctx = Synthea::Generic::Context.new(cfg)
    assert_equal("Initial", ctx.current_state.name)
    ctx.run(@time, @patient)
    assert(ctx.current_state.name.start_with?("TerminalM"))

    # Then run as a female
    @patient[:gender] = 'F'
    ctx = Synthea::Generic::Context.new(cfg)
    assert_equal("Initial", ctx.current_state.name)
    ctx.run(@time, @patient)
    assert(ctx.current_state.name.start_with?("TerminalF"))

  end

  def test_no_transition
    ctx = get_context('no_transition.json')
    assert_equal("Initial", ctx.current_state.name)
    ctx.run(@time, @patient)
    # If there is no transition, it should go to a default Terminal state
    assert_equal("Terminal", ctx.current_state.name)
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
    @patient.record_synthea.expect(:encounter, nil, [:emergency_room_admission, @time.advance(:days => 2)])
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

  def test_logging
    # Can't really test the quality of the logs, but at least ensure it logs when it should and that nothing crashes

    old_log_value = Synthea::Config.generic.log

    # First check it doesn't log when it shouldn't
    Synthea::Config.generic.log = false
    ctx = get_context('direct_transition.json')
    assert_equal("Initial", ctx.current_state.name)
    ctx.run(@time, @patient)
    assert_equal("Terminal", ctx.current_state.name)
    refute(ctx.logged)

    # Then check it does log when it should
    Synthea::Config.generic.log = true
    ctx = get_context('direct_transition.json')
    assert_equal("Initial", ctx.current_state.name)
    ctx.run(@time, @patient)
    assert_equal("Terminal", ctx.current_state.name)
    assert(ctx.logged)

    # Set the log value back
    Synthea::Config.generic.log = old_log_value
  end

  def get_config(file_name)
    JSON.parse(File.read(File.join(File.expand_path("../../fixtures/generic", __FILE__), file_name)))
  end
  def get_context(file_name)
    Synthea::Generic::Context.new(get_config(file_name))
  end
end
