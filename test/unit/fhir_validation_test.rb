require_relative '../test_helper'

class FhirValidationTest < Minitest::Test
  def teardown
    Synthea::MODULES.clear
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

  def test_execution_and_shr_validation
    profiles = {}

    Dir.glob('./resources/shr_profiles/StructureDefinition-shr-*.json').each do |file|
      json = JSON.parse(File.read(file))
      profiles[json['url']] = FHIR::StructureDefinition.new(json)
    end
    
    # iterate over all the modules

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
    @encounter = {'type' => :age_lt_11, 'time' => @time, 'end_time' => @time + 1.hour }
    @encounter_entry = Synthea::Output::FhirRecord.encounter(@encounter, @fhir_record, @patient_entry)
    @patientID = @fhir_record.entry[0].fullUrl
    @encounterID = @fhir_record.entry[1].fullUrl

    Dir.glob('./lib/generic/modules/*.json') do |file|
      context = get_context(file)

      context.all_states.each do |state_name|
        if context.state_config(state_name)['type'] == 'Observation'

          observation = {'type' => :height, 'time' => Time.now, 'value' => "60" }
          Synthea::Output::FhirRecord.observation(observation, @fhir_record, @patient_entry, @encounter_entry)
          obs_entry = @fhir_record.entry.reverse.find {|e| e.resource.is_a?(FHIR::Observation)}
          obs = obs_entry.resource

          if obs.meta && obs.meta.profile
            profile = profiles[ obs.meta.profile[0] ]
            assert_empty profile.validate_resource(obs)
          end

        end
      end

    end

    binding.pry
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
