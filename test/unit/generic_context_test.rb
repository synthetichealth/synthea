require_relative '../test_helper'

class GenericContextTest < Minitest::Test

  def setup
		@time = Time.now
  	@patient = Synthea::Person.new
		@patient[:gender] = 'F'
    @patient.events.create(@time - 35.years, :birth, :birth)
		@patient[:age] = 35
		@patient[:is_alive] = true
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
		assert_equal(6, ctx.history.length)
		assert_equal("Initial", ctx.history[0].name)
		assert_equal("Guard1", ctx.history[1].name)
		assert_equal("Guard2", ctx.history[2].name)
		assert_equal("Guard2", ctx.history[3].name)
		assert_equal("Guard1", ctx.history[4].name)
		assert_equal("Guard2", ctx.history[5].name)
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
	end

	def get_config(file_name)
		JSON.parse(File.read(File.join(File.expand_path("../../fixtures/generic", __FILE__), file_name)))
	end
	def get_context(file_name)
		Synthea::Generic::Context.new(get_config(file_name))
	end
end
