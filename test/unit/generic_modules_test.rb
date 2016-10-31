require_relative '../test_helper'

class GenericModulesTest < Minitest::Test
  def test_all_modules
    # loop over all the modules, verify all states and all transitions are valid
    # future considerations: verify all logic

    Dir.glob('./lib/generic/modules/*.json') do |file|
      check_file(file)
    end

    Dir.glob('./test/fixtures/generic/*.json') do |file|
      next if file == './test/fixtures/generic/logic.json' # logic.json has only conditions, not real states
      check_file(file)
    end
  end

  def check_file(file)
    wf = JSON.parse(File.read(file))
    context = Synthea::Generic::Context.new(wf)

    all_states = wf['states'].keys
    reachable = ['Initial']

    all_states.each do |state_name|
      state = context.create_state(state_name)
      assert_empty state.validate, "#{file}: State #{state_name} failed to validate"
      check_transitions(state_name, state, all_states, file, reachable)
    end

    unreachable = all_states - reachable
    assert_empty(unreachable, "#{file}: Unreachable states detected")
  end

  def check_transitions(state_name, state, all_states, file, reachable)
    if state.is_a?(Synthea::Generic::States::Terminal)
      assert_equal(nil, state.transition, "#{file}: Terminal state #{state_name} has a transition defined")
    else
      msg = "#{file}: State #{state_name} has no transitions defined"
      refute_equal(nil, state.transition, msg)
      refute_empty(state.transition.all_transitions, msg)
      all_transitions = state.transition.all_transitions
      all_transitions.each do |transition|
        check_transition_possible(state_name, all_states, transition, file)
        reachable << transition
      end
    end
  end

  def check_transition_possible(state_name, all_states, transition, file)
    assert_includes(all_states, transition,
                    "#{file}: State '#{state_name}' transitions to '#{transition}' but this state does not exist")
  end
end