require_relative '../test_helper'

class GenericLogicTest < Minitest::Test

  def setup
    @time = Time.now
    @patient = Synthea::Person.new
    @patient[:gender] = 'F'
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

  def test_date
    @time = Time.new(2016, 9, 21)
    refute(do_test('before2016Test'))
    assert(do_test('after2000Test'))

    @time = Time.new(1981, 4, 28)
    assert(do_test('before2016Test'))
    refute(do_test('after2000Test'))

    @time = Time.new(2002, 2, 22)
    assert(do_test('before2016Test'))
    assert(do_test('after2000Test'))
  end

  def test_attribute
    attribute = 'Test_Attribute_Key'

    @patient[attribute] = nil
    refute(do_test('attributeEqualTo_TestValue_Test'))
    assert(do_test('attributeNilTest'))
    refute(do_test('attributeNotNilTest'))

    @patient[attribute] = "Wrong Value"
    refute(do_test('attributeEqualTo_TestValue_Test'))
    refute(do_test('attributeNilTest'))
    assert(do_test('attributeNotNilTest'))

    @patient[attribute] = "TestValue"
    assert(do_test('attributeEqualTo_TestValue_Test'))
    refute(do_test('attributeNilTest'))
    assert(do_test('attributeNotNilTest'))

    @patient[attribute] = 120
    refute(do_test('attributeEqualTo_TestValue_Test'))
    assert(do_test('attributeGt100Test'))
    refute(do_test('attributeNilTest'))
    assert(do_test('attributeNotNilTest'))
  end

  def test_symptoms
    @patient.set_symptom_value('Appendicitis', 'PainLevel', 60)
    assert(do_test('symptomPainLevelGt50'))
    assert(do_test('symptomPainLevelLte80'))

    @patient.set_symptom_value('Appendicitis', 'LackOfAppetite', 100) # painlevel still 60 here
    assert(do_test('symptomPainLevelGt50'))
    assert(do_test('symptomPainLevelLte80'))

    @patient.set_symptom_value('Appendicitis', 'PainLevel', 10)
    refute(do_test('symptomPainLevelGt50'))
    assert(do_test('symptomPainLevelLte80'))

    @patient.set_symptom_value('Appendicitis', 'PainLevel', 100)
    assert(do_test('symptomPainLevelGt50'))
    refute(do_test('symptomPainLevelLte80'))
  end

  def test_prior_state
    # @context.history = [] # can't actually set this here, but we know it's true
    refute(do_test('priorStateDoctorVisitTest'))

    @context.history << Synthea::Generic::States::Simple.new(@context, "SomeOtherState")
    refute(do_test('priorStateDoctorVisitTest'))

    @context.history << Synthea::Generic::States::Simple.new(@context, "DoctorVisit")
    assert(do_test('priorStateDoctorVisitTest'))
  end

  def test_observations
    @patient.record_synthea.observations = []
    assert_raises { do_test('mmseObservationGt22') }

    @patient.record_synthea.observation(:mini_mental_state_examination, @time, 12)
    refute(do_test('mmseObservationGt22'))

    @patient.record_synthea.observation(:mini_mental_state_examination, @time, 29)
    assert(do_test('mmseObservationGt22'))


    @patient.record_synthea.observations = []
    refute(do_test('hasDiabetesObservation'))

    @patient.record_synthea.observation(:blood_panel, @time, 'blah blah')
    @patient['Blood Test Performed'] = :blood_panel
    refute(do_test('hasDiabetesObservation'))

    @patient.record_synthea.observation(:glucose_panel, @time, '12345')
    @patient['Diabetes Test Performed'] = :glucose_panel
    assert(do_test('hasDiabetesObservation'))
  end

  def test_condition_condition
    @patient.record_synthea.conditions = []
    @patient.record_synthea.present = {}

    refute(do_test('diabetesConditionTest'))
    refute(do_test('alzheimersConditionTest'))

    @patient.record_synthea.condition(:diabetes_mellitus, @time)
    assert(do_test('diabetesConditionTest'))
    refute(do_test('alzheimersConditionTest'))

    @time += 10.years

    @patient.record_synthea.end_condition(:diabetes_mellitus, @time)
    refute(do_test('diabetesConditionTest'))


    @patient.record_synthea.condition(:early_onset_alzheimers, @time)
    @patient['Alzheimer\'s Variant'] = :early_onset_alzheimers

    assert(do_test('alzheimersConditionTest'))
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
