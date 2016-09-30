require_relative '../test_helper'

class GraphvizTest < Minitest::Test
  def test_all_modules
    Dir.glob('../synthea/lib/generic/modules/*.json') do |wf_file|
      wf = JSON.parse(File.read(wf_file))

      wf['states'].each do |name, state|
        generic_state_test(state)
      end
    end

    Dir.glob('../synthea/test/fixtures/generic/*.json') do |wf_file|
      if wf_file == '../synthea/test/fixtures/generic/logic.json' # logic.json has only conditions, not real states
        logic = JSON.parse(File.read(wf_file))
        logic.each do |name, condition|
          generic_logic_test(condition)
        end
      else
        wf = JSON.parse(File.read(wf_file))
        wf['states'].each do |name, state|
          generic_state_test(state)
        end
      end
    end
  end

  def generic_state_test(state)
    description = Synthea::Tasks::Graphviz.state_description(state)
    refute(description.empty?, "Description should not be empty for a #{state['type']} state") unless %w(Simple Initial Terminal Death).include?(state['type'])

    transitions = state['conditional_transition'] || state['complex_transition']
    return if transitions.nil?

    transitions.each do |t|
       generic_logic_test(t['condition']) if t.has_key?('condition')
    end
  end

  def generic_logic_test(condition)
    logic = Synthea::Tasks::Graphviz.logicDetails(condition)
    pass # assertion that the logicDetails didn't raise an exception
  end
end