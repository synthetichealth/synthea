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

    errors = context.validate

    unless errors.empty?
      puts "#{file} failed to validate.\nError List:"
      errors.each { |e| puts e }
      flunk
    end

    pass
  end
end