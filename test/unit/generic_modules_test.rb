require_relative '../test_helper'

class GenericModulesTest < Minitest::Test
  def setup

  end

  def test_all_modules
    # loop over all the states, verify all logic is semantically valid, and all transitions go to valid states
    # (not checking for impossible conditions, like AND(gender=M, gender=F) )

    Dir.glob('./lib/generic/modules/*.json') do |wf_file|
      wf = JSON.parse(File.read(wf_file))

      context = Synthea::Generic::Context.new(wf)

      wf['states'].each do |state_name, state|
        generic_state_test(state_name, context, wf_file)
        generic_transition_test(state_name, state, wf['states'].keys, wf_file)
      end
    end

    Dir.glob('./test/fixtures/generic/*.json') do |wf_file|
      if wf_file == './test/fixtures/generic/logic.json' # logic.json has only conditions, not real states
        # logic = JSON.parse(File.read(wf_file))
        # logic.each do |name, condition|
        #   generic_logic_test(name, condition)
        # end
      else
        wf = JSON.parse(File.read(wf_file))
        context = Synthea::Generic::Context.new(wf)
        wf['states'].each do |state_name, state|
          generic_state_test(state_name, context, wf_file)
          generic_transition_test(state_name, state, wf['states'].keys, wf_file)
        end
      end
    end
  end


  def generic_state_test(state_name, context, file)
    state = context.create_state(state_name)
    assert_empty state.validate, "#{file}: State #{state_name} failed to validate"
  end

  def generic_logic_test(state_name, condition)
    #Synthea::Generic::Logic.test(condition, @context, @time, @patient)
    pass # it's good as long as it doesn't raise an exception
  end

  def generic_transition_test(state_name, state, all_states, file)
    if state['direct_transition']
      check_transition_possible(state_name, all_states, state['direct_transition'], file)
    elsif state['conditional_transition']
      state['conditional_transition'].each do |ct|
        check_transition_possible(state_name, all_states, ct['transition'], file)
      end
    elsif state['distributed_transition']
      state['distributed_transition'].each do |dt|
        check_transition_possible(state_name, all_states, dt['transition'], file)
      end
    elsif state['complex_transition']
      state['complex_transition'].each do |ct|
        if ct['transition']
          check_transition_possible(state_name, all_states, ct['transition'], file)
        elsif ct['distributions']
          ct['distributions'].each do |dt|
            check_transition_possible(state_name, all_states, dt['transition'], file)
          end
        end
      end
    else
      flunk("#{file}: State #{state_name} has no transitions defined") unless state['type'] == 'Terminal'
    end
  end

  def check_transition_possible(state_name, all_states, transition, file)
    assert_includes(all_states, transition,
                    "#{file}: State '#{state_name}' transitions to '#{transition}' but this state does not exist")
  end
end