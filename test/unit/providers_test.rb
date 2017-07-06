require_relative '../test_helper'

class ProviderTest < Minitest::Test

  def setup
    # import hospitals
    p_file = File.join(File.dirname(__FILE__), '..', 'fixtures', 'test_healthcare_facilities.json')
    Synthea::Hospital.load(p_file)    

    @time = Time.now
    @patient = Synthea::Person.new
    @patient[:coordinates_address] = GeoRuby::SimpleFeatures::Point.from_x_y(-71.16614917449775,42.32102909461221)
    @patient.assign_ambulatory_provider
    @patient.assign_inpatient_provider
    @patient.assign_emergency_provider
    @patient.events.create(@time - 25.years, :birth, :birth)
    @patient[:age] = 25
    
  end

  def teardown
    Synthea::MODULES.clear
    Synthea::Hospital.clear
  end

  def test_import
    # Test individual service provider was imported
    assert_equal([ -70.890916, 42.814401 ], Synthea::Hospital.hospital_list[0].attributes[:coordinates])
    assert_equal([:emergency, :inpatient, :ambulatory], Synthea::Hospital.hospital_list[0].services_provided)

    # Test all service providers were imported
    assert(Synthea::Hospital.hospital_list)
    assert_equal([:emergency, :inpatient, :ambulatory], Synthea::Hospital.services.keys)
  end

  def test_clear
    Synthea::Hospital.clear
    assert([], Synthea::Hospital.provider_list)
    assert({}, Synthea::Hospital.services)
    assert([], Synthea::Hospital.hospital_list)
  end

  def test_closest_service_provider
    # Closest default provider is hospital_list[3] which only supports ambulatory services
    assert_equal(Synthea::Hospital.hospital_list[3], @patient.hospital[:ambulatory])
    closest_service_ambulatory = Synthea::Hospital.find_closest_service(@patient, "ambulatory")
    assert_equal(Synthea::Hospital.hospital_list[3], closest_service_ambulatory)

    # Test for correct providers given services not provided by default provider
    closest_service_emergency = Synthea::Hospital.find_closest_service(@patient, "emergency")
    assert_equal(Synthea::Hospital.hospital_list[2], closest_service_emergency)
    closest_service_inpatient = Synthea::Hospital.find_closest_service(@patient, "inpatient")
    assert_equal(Synthea::Hospital.hospital_list[1], closest_service_inpatient)
  end

  def test_utilization_encounter
    @patient.record_synthea = MiniTest::Mock.new

    # hospitals begin with blank state
    assert_equal(0, @patient.hospital[:ambulatory].utilization[:encounters])

    # Encounter - patient sees default provider for ambulatory services 
    ctx = get_context('example_module.json')
    encounter = Synthea::Generic::States::Encounter.new(ctx, 'Examplotomy_Encounter')
    @patient.record_synthea.expect(:encounter, nil, [:examplotomy_encounter, @time, {provider: @patient.hospital[:ambulatory]}])
    encounter.perform_encounter(@time, @patient)

    assert_equal(1, @patient.hospital[:ambulatory].utilization[:encounters])
    @patient.record_synthea.verify
  end

  def test_utilization_procedure
    @patient.record_synthea = MiniTest::Mock.new

    # hospitals begin with blank state
    assert_equal(0, Synthea::Hospital.hospital_list[0].utilization[:encounters])
    assert_equal(0, Synthea::Hospital.hospital_list[0].utilization[:procedures])

    # Procedure - patient sees hospital_list[0] for inpatient service
    # from generic_states_test.rb test_procedure_during_encounter
    ctx = get_context('procedure.json')
    encounter = Synthea::Generic::States::Encounter.new(ctx, "Inpatient_Encounter")
    @patient.record_synthea.expect(:encounter, nil, [:hospital_admission, @time, {provider: Synthea::Hospital.hospital_list[1]}])
    encounter.perform_encounter(@time, @patient)
    ctx.history << encounter

    # patient has appendectomy procedure
    appendectomy = Synthea::Generic::States::Procedure.new(ctx, "Appendectomy")
    appendectomy.start_time = @time
    @patient.record_synthea.expect(:procedure, nil, [:laparoscopic_appendectomy, @time, { 'duration' => 45.minutes }])
    appendectomy.run(@time, @patient)
    appendectomy.process(@time, @patient)
    ctx.history << appendectomy

    # both an encounter and procedure occured
    assert_equal(1, Synthea::Hospital.hospital_list[1].utilization[:encounters])
    assert_equal(1, Synthea::Hospital.hospital_list[1].utilization[:procedures])
    @patient.record_synthea.verify
  end

    
  def test_utilization_prescriptions 
    @patient.record_synthea = MiniTest::Mock.new

    # hospitals begin with blank state
    assert_equal(0, @patient.hospital[:ambulatory].utilization[:prescriptions])

    # Prescriptions - patient sees hospital[3] for prescriptions
    # from generic_states_test.rb test_medication_order_during_wellness_encounter
    ctx = get_context('medication_order.json')
    # Diabetes onset
    diabetes = Synthea::Generic::States::ConditionOnset.new(ctx, 'Diabetes')
    diabetes.process(@time, @patient)
    ctx.history << diabetes
    # Process the wellness encounter state, which will wait for a wellness encounter
    encounter = Synthea::Generic::States::Encounter.new(ctx, 'Wellness_Encounter')
    @time = @time + 6.months
    # Simulate the wellness encounter by calling perform_encounter
    @patient.record_synthea.expect(:condition, nil, [:diabetes_mellitus, @time])
    encounter.perform_encounter(@time, @patient, false)
    ctx.history << encounter

    # Process prescription 
    med = Synthea::Generic::States::MedicationOrder.new(ctx, 'Metformin')
    @patient.record_synthea.expect(:medication_start, nil, [
      '24_hr_metformin_hydrochloride_500_mg_extended_release_oral_tablet'.to_sym,
      @time,
      [:diabetes_mellitus],
      {}
    ])
    med.run(@time, @patient)

    assert_equal(1, @patient.hospital[:ambulatory].utilization[:prescriptions])
    @patient.record_synthea.verify
  end
  
  def get_context(file)
    key = load_module(file)
    Synthea::Generic::Context.new(key)
  end

  def load_module(file)
    module_dir = File.expand_path('../../../test/fixtures/generic', __FILE__)
    # loads a module into the global MODULES hash given an absolute path
    key = module_key(file)
    Synthea::MODULES[key] = JSON.parse(File.read(File.join(module_dir, file)))
    key
  end

  def module_key(file)
    file.sub('.json', '')
  end
end
