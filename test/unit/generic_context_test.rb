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

	def get_config(file_name)
		JSON.parse(File.read(File.join(File.expand_path("../../fixtures/generic", __FILE__), file_name)))
	end
	def get_context(file_name)
		Synthea::Generic::Context.new(get_config(file_name))
	end
end
