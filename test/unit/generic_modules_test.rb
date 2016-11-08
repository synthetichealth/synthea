require_relative '../test_helper'

class GenericModulesTest < Minitest::Test
  def test_all_modules
    # loop over all the modules, verify all states and all transitions are valid
    # future considerations: verify all logic

    Dir.glob('./lib/generic/modules/*.json') do |file|
      check_file(file)
    end

    Dir.glob('./test/fixtures/generic/*.json') do |file|
      if file == './test/fixtures/generic/logic.json' # logic.json has only conditions, not real states
        check_logic(file)
      else
        check_file(file)
      end
    end
  end

  def check_file(file)
    wf = JSON.parse(File.read(file))
    context = Synthea::Generic::Context.new(wf)

    errors = context.validate

    unless errors.empty?
      puts "#{file} failed to validate.\nError List:"
      errors.each { |e| puts e }
      flunk
    end

    pass
  end

  def check_logic(file)
    tests = JSON.parse(File.read(file))
    context = Synthea::Generic::Context.new({
                    "name" => "Logic",
                    "states" => {
                      "Initial" => { "type" => "Initial" },
                      "DoctorVisit" => { "type" => "Simple" } # needed for the PriorState test
                    }
                  })
    tests.each do |name, logic|
      condition = Object.const_get("Synthea::Generic::Logic::#{logic['condition_type'].gsub(/\s+/, '_').camelize}").new(logic)
      errors = condition.validate(context, [])

      unless errors.empty?
        puts "#{file} / Test #{name} failed to validate.\nError List:"
        errors.each { |e| puts e }
        flunk
      end

      pass
    end
  end
end