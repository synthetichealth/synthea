require_relative '../test_helper'

class GenericModulesTest < Minitest::Test

  def teardown
    Synthea::MODULES.clear
  end

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
    context = get_context(file)

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
    Synthea::MODULES['logic'] = {
      "name" => "Logic",
      "states" => {
        "Initial" => { "type" => "Initial" },
        "DoctorVisit" => { "type" => "Simple" } # needed for the PriorState test
      }
    }
    context = Synthea::Generic::Context.new('logic')
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

  def get_context(file)
    key = load_module(file)
    Synthea::Generic::Context.new(key)
  end

  def load_module(file)
    module_dir = File.expand_path('../../../', __FILE__)
    # loads a module into the global MODULES hash given an absolute path
    key = module_key(file)
    Synthea::MODULES[key] = JSON.parse(File.read(File.join(module_dir, file)))
    key
  end

  def module_key(file)
    file.sub('.json', '')
  end
end
