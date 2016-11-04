require_relative '../test_helper'

class MetadataTest < Minitest::Test

  def test_validate
    cfg = JSON.parse(File.read(File.join(File.expand_path("../../fixtures/generic/validation/", __FILE__), 'field_errors.json')))
    context = Synthea::Generic::Context.new(cfg)

    errors = validation_errors(context, 'Missing_Transition')
    assert_equal 1, errors.count, "Expected 1 error, got #{errors}"
    assert_starts_with('Required \'transition\' is missing', errors[0])

    errors = validation_errors(context, 'Guard_Missing_Allow')
    assert_equal 1, errors.count, "Expected 1 error, got #{errors}"
    assert_starts_with('Required \'allow\' is missing', errors[0])

    errors = validation_errors(context, 'Delay_Missing_Amount')
    assert_equal 1, errors.count, "Expected 1 error, got #{errors}"
    assert_starts_with('At least one of (range or exact) is required on', errors[0])

    errors = validation_errors(context, 'Encounter_Empty')
    assert_equal 1, errors.count, "Expected 1 error, got #{errors}"
    assert_starts_with('At least one of (wellness or (codes and encounter_class)) is required on', errors[0])

    errors = validation_errors(context, 'Encounter_With_Class_Missing_Codes')
    assert_equal 1, errors.count, "Expected 1 error, got #{errors}"
    assert_starts_with('At least one of (wellness or (codes and encounter_class)) is required on', errors[0])

    errors = validation_errors(context, 'Encounter_With_Code_Missing_System')
    assert_equal 1, errors.count, "Expected 1 error, got #{errors}"
    assert_starts_with('All of (code and system and display) are required on', errors[0])

    errors = validation_errors(context, 'Procedure_Missing_Target_Encounter')
    assert_equal 1, errors.count, "Expected 1 error, got #{errors}"
    assert_starts_with('All of (target_encounter and codes) are required on', errors[0])

    errors = validation_errors(context, 'Conditional_Transition_Missing_Transition')
    assert_equal 1, errors.count, "Expected 1 error, got #{errors}"
    assert_starts_with('Required \'transition\' is missing', errors[0])

    errors = validation_errors(context, 'Distributed_Transition_Missing_Pieces')
    assert_equal 2, errors.count, "Expected 2 errors, got #{errors}"
    assert_starts_with('All of (transition and distribution) are required on', errors[0])
    assert_starts_with('All of (transition and distribution) are required on', errors[1])

    errors = validation_errors(context, 'Complex_Transition_Missing_Pieces')
    assert_equal 1, errors.count, "Expected 1 error, got #{errors}"
    assert_starts_with('At least one of (transition or distributions) is required on', errors[0])

    errors = validation_errors(context, 'Date_Condition_Missing_Operator')
    assert_equal 1, errors.count, "Expected 1 error, got #{errors}"
    assert_starts_with('All of (year and operator) are required on', errors[0])

  end

  def validation_errors(context, state_name)
    context.create_state(state_name).validate
  end

  def assert_starts_with(prefix, obj, msg = nil)
    msg ||= "Expected '#{obj}' to start with '#{prefix}'"
    assert(obj.start_with?(prefix), msg)
  end

  def test_to_string
    obj = Class.new.extend(Synthea::Generic::Metadata)

    assert_equal 'symbol', obj.to_string(:symbol)
    assert_equal '(this or that)', obj.to_string(or: [:this, :that])
    assert_equal '(gold and silver)', obj.to_string(and: [:gold, :silver])
    assert_equal '((salt and pepper) or (sugar and spice))', obj.to_string( or: [{ and: [:salt, :pepper] }, { and: [:sugar, :spice] }])

    assert_raises { obj.to_string([:this, :that]) }
    assert_raises { obj.to_string('string') }
    assert_raises { obj.to_string({ or: [:this, :that], and: [:something_else] }) } # note this is 2 entries in 1 hash
    assert_raises { obj.to_string({}) }
      
      

  end
end