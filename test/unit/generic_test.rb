require_relative '../test_helper'

class GenericTest < Minitest::Test

  def setup
		@time = Time.now
  	@patient = Synthea::Person.new
		@patient[:gender] = 'F'
    @patient.events.create(@time, :birth, :birth)
		@patient[:is_alive] = true
  end

  def test_guard_passes
		ctx = get_context('guard.json')
		ctx.run(@time, @patient)

		# Should have passed all the way through
		assert(ctx.current_state.is_a? Synthea::Generic::States::Terminal)
		
		guard = ctx.most_recent_by_name('Test_Guard')
		# Should have entered and exited at @time
		assert_equal(@time, guard.entered)
		assert_equal(@time, guard.exited)
  end

	def test_guard_blocks
		ctx = get_context('guard.json')
		@patient[:gender] = 'M'
		ctx.run(@time, @patient)
		
		# Should have blocked on Test_Guard
		assert(ctx.current_state.is_a? Synthea::Generic::States::Guard)
		assert_equal("Test_Guard", ctx.current_state.name)
		
		# Should have entered at @time and not exited
		assert_equal(@time, ctx.current_state.entered)
		assert_nil(ctx.current_state.exited)
	end

	def get_context(file_name)
		cfg = JSON.parse(File.read(File.join(File.expand_path("../../fixtures", __FILE__), file_name)))
		Synthea::Generic::Context.new(cfg)
	end
end
