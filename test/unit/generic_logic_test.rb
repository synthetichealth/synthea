require_relative '../test_helper'

class GenericLogicTest < Minitest::Test

  def setup
		@time = Time.now
  	@patient = Synthea::Person.new
		@patient[:gender] = 'F'
		@patient[:is_alive] = true
		@context = Synthea::Generic::Context.new({
			"name" => "Logic",
			"states" => {
				"Initial" => {
          "type" => "Initial"
				}
			}
		})
		@logic = JSON.parse(File.read(File.expand_path("../../fixtures/generic/logic.json", __FILE__)))
  end

	def setPatientAge(ageInYears)
		@patient.events.create(@time - (ageInYears * 1.years), :birth, :birth)
		@patient[:age] = ageInYears
	end

	def do_test(name)
		Synthea::Generic::Logic::test(@logic[name], @context, @time, @patient)
	end

	def test_true
		assert(do_test('trueTest'))
	end

	def test_false
		refute(do_test('falseTest'))
	end

	def test_gender_condition
		refute(do_test('genderIsMaleTest'))
		@patient[:gender] = 'M'
		assert(do_test('genderIsMaleTest'))
  end

	def test_age_conditions_on_age_35
		setPatientAge(35)
		assert(do_test('ageLt40Test'))
		assert(do_test('ageLte40Test'))
		refute(do_test('ageEq40Test'))
		refute(do_test('ageGte40Test'))
		refute(do_test('ageGt40Test'))
		assert(do_test('ageNe40Test'))
  end

	def test_age_conditions_on_age_40
		setPatientAge(40)
		refute(do_test('ageLt40Test'))
		assert(do_test('ageLte40Test'))
		assert(do_test('ageEq40Test'))
		assert(do_test('ageGte40Test'))
		refute(do_test('ageGt40Test'))
		refute(do_test('ageNe40Test'))
  end

	def test_age_conditions_on_age_45
		setPatientAge(45)
		refute(do_test('ageLt40Test'))
		refute(do_test('ageLte40Test'))
		refute(do_test('ageEq40Test'))
		assert(do_test('ageGte40Test'))
		assert(do_test('ageGt40Test'))
		assert(do_test('ageNe40Test'))
  end

  def set_ses_config_settings
  	Synthea::Config.socioeconomic_status.weighting.income = 0.3
  	Synthea::Config.socioeconomic_status.weighting.occupation = 0.2
  	Synthea::Config.socioeconomic_status.weighting.education = 0.5
  	
  	Synthea::Config.socioeconomic_status.categories.low = [0, 0.333]
  	Synthea::Config.socioeconomic_status.categories.middle = [0.333, 0.667]
  	Synthea::Config.socioeconomic_status.categories.high = [0.667, 1.0]
  end

  def test_ses_category_high
  	set_ses_config_settings

  	@patient[:ses] = { education: 0.75, income: 1, occupation: 0.7 }

  	assert(do_test('sesHighTest'))
  	refute(do_test('sesMiddleTest'))
  	refute(do_test('sesLowTest'))
  end

  def test_ses_category_middle
  	set_ses_config_settings

  	@patient[:ses] = { education: 0.5, income: 0.5, occupation: 0.5 }

  	refute(do_test('sesHighTest'))
  	assert(do_test('sesMiddleTest'))
  	refute(do_test('sesLowTest'))
  end

  def test_ses_category_low
  	set_ses_config_settings

  	@patient[:ses] = { education: 0.1, income: 0.2, occupation: 0.3 }

  	refute(do_test('sesHighTest'))
  	refute(do_test('sesMiddleTest'))
  	assert(do_test('sesLowTest'))
  end

	def test_and_conditions
		assert(do_test('andAllTrueTest'))
		refute(do_test('andOneFalseTest'))
		refute(do_test('andAllFalseTest'))
	end

	def test_or_conditions
		assert(do_test('orAllTrueTest'))
		assert(do_test('orOneTrueTest'))
		refute(do_test('orAllFalseTest'))
	end

	def test_not_conditions
		refute(do_test('notTrueTest'))
		assert(do_test('notFalseTest'))
	end
end
