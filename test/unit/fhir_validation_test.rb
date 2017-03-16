require_relative '../test_helper'

class FhirValidationTest < Minitest::Test
  def setup
    Synthea::Config.exporter.fhir.use_shr_extensions = true
    Synthea::COND_LOOKUP['1234'] = { description: '1234', codes: {'SNOMED-CT' => ['1234']}}
  end

  def teardown
    Synthea::MODULES.clear
    Synthea::COND_LOOKUP.delete('1234')
  end

  def test_execution_and_fhir_validation
    world = Synthea::World::Sequential.new
    world.population_count = 0
    (1..10).each do |i|
      # generate a patient with synthea
      record = world.build_person
      print '.'
      # convert to FHIR
      bundle = Synthea::Output::FhirRecord.convert_to_fhir(record)
      print '.'
      # validate all the resources
      assert_empty bundle.validate
      print '.'
    end
    puts
  end


  def test_shr_validation
    skip "FHIR Profile validation not yet ready"

    @profiles = {}

    Dir.glob('./resources/shr_profiles/StructureDefinition-shr-*.json').each do |file|
      json = JSON.parse(File.read(file))
      @profiles[json['url']] = FHIR::StructureDefinition.new(json)
    end

    # for every type, ensure the resulting object is valid per the profile it defines
    # :basic_info, :encounters, :conditions, :allergies, :observations,
    # :procedures, :immunizations, :careplans, :medications

    @patient = Synthea::Person.new
    @patient[:name_first] = "foo123"
    @patient[:name_last] = "bar456"
    @patient[:gender] = 'F'
    @patient[:address] = {
      'line' => ["4655 Emmerich Springs"],
      'city' => "Bedford",
      'state' => "MA",
      'postalCode' => "01730"
    }
    @patient[:telephone] = '999-999-9999'
    @patient[:birth_place] = { 'city' => 'Bedford','state' => 'MA', }
    @patient[:race] = :white
    @patient[:ethnicity] = :italian
    @patient[:coordinates_address] = GeoRuby::SimpleFeatures::Point.from_x_y(10,15)
    @fhir_record = FHIR::Bundle.new
    @fhir_record.type = 'collection'
    @time = Time.now
    @patient.events.create(@time, :birth, :birth)
    @patient_entry = Synthea::Output::FhirRecord.basic_info(@patient, @fhir_record)

    validate_by_profile(@patient_entry.resource)

    @encounter = {'type' => :age_lt_11, 'time' => @time, 'end_time' => @time + 1.hour }
    @encounter_entry = Synthea::Output::FhirRecord.encounter(@encounter, @fhir_record, @patient_entry)
    @patientID = @fhir_record.entry[0].fullUrl
    @encounterID = @fhir_record.entry[1].fullUrl

    validate_by_profile(@encounter_entry.resource)

    condition = {'type' => :end_stage_renal_disease, 'time' => @time}
    Synthea::Output::FhirRecord.condition(condition, @fhir_record, @patient_entry, @encounter_entry)
    disease_entry = @fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::Condition)}
    validate_by_profile(disease_entry.resource)

    condition = {'type' => :food_allergy_peanuts, 'time' => @time}
    Synthea::Output::FhirRecord.allergy(condition, @fhir_record, @patient_entry, @encounter_entry)
    allergy_entry = @fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::AllergyIntolerance)}
    validate_by_profile(allergy_entry.resource)

    proc_hash = { 'type' => :amputation_left_arm , 'time' => @time, 'reason' => :nephropathy}
    Synthea::Output::FhirRecord.procedure(proc_hash, @fhir_record, @patient_entry, @encounter_entry)
    proc_entry = @fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::Procedure)}
    validate_by_profile(proc_entry.resource)

    imm_hash = { 'type' => :hepb, 'time' => @time}
    Synthea::Output::FhirRecord.immunization(imm_hash, @fhir_record, @patient_entry, @encounter_entry)
    imm = @fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::Immunization)}
    validate_by_profile(imm.resource)

    med_hash = { 'type' => :amiodarone, 'time' =>  @time, 'start_time' => @time, 'reasons' => [],
                 'stop' => @time + 15.minutes, 'rx_info' => {}}
    Synthea::Output::FhirRecord.medications(med_hash, @fhir_record, @patient_entry, @encounter_entry)
    med = @fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::MedicationRequest)}
    validate_by_profile(med.resource)

    plan_hash = { 'type' => :cardiovascular_disease, 'activities' => [:exercise, :healthy_diet], 'start_time'=>@time, 'time' => @time,
                  'reasons' => [], 'stop' => @time + 15.minutes}
    Synthea::Output::FhirRecord.careplans(plan_hash, @fhir_record, @patient_entry, @encounter_entry)
    plan = @fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::CarePlan)}
    validate_by_profile(plan.resource)

    # for observations, there are specific profiles per observation (ex, blood pressure has its own profile)
    # so loop over all observation codes we know about

    obs_types = Synthea::OBS_LOOKUP.keys

    Dir.glob('./lib/generic/modules/*.json') do |file|
      json = JSON.parse(File.read(file))

      json['states'].each do |state_name, state|
        if state['type'] == 'Observation' && state['codes']

          code = state['codes'][0]

          symbol = code['display'].gsub(/\s+/, '_').downcase.to_sym

          Synthea::OBS_LOOKUP[symbol] ||= { description: code['display'], code: code['code'], unit: state['unit'] }
        end
      end
    end

    obs_types.each do |obs|
      observation = {'type' => obs, 'time' => Time.now, 'value' => "1234" }
      Synthea::Output::FhirRecord.observation(observation, @fhir_record, @patient_entry, @encounter_entry)
      obs_entry = @fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::Observation)}
      validate_by_profile(obs_entry.resource)
    end

  end

  def validate_by_profile(resource)
    return unless resource.meta && resource.meta.profile

    resource.meta.profile.each do |profile_uri|
      structure_definition = @profiles[ profile_uri ]
      errors = structure_definition.validate_resource(resource)
      assert_empty errors
      # TODO: do we have to remove reference errors?
    end
  end
end
