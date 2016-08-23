module Synthea
  module World
    class Sequential

      attr_reader :stats
        
      def initialize(datafile)
        @start_date = Synthea::Config.start_date
        @end_date = Synthea::Config.end_date
        @time_step = Synthea::Config.time_step

        @stats = Hash.new(0)
        @stats[:age] = Hash.new(0)
        @stats[:gender] = Hash.new(0)
        @stats[:race] = Hash.new(0)
        @stats[:ethnicity] = Hash.new(0)
        @stats[:blood_type] = Hash.new(0)

        @population_count = Synthea::Config.sequential.population

        @scaling_factor = @population_count.to_f / Synthea::Config.sequential.real_world_population.to_f
        # if you want to generate a population smaller than 7M but still with accurate ratios,
        #  you can scale the populations of individual cities down by this amount. 

        @city_populations = JSON.parse(datafile) if datafile

        ['html','fhir','CCDA'].each do |type|
          out_dir = File.join('output',type)
          FileUtils.rm_r out_dir if File.exists? out_dir
          FileUtils.mkdir_p out_dir
        end
        Mongoid.configure { |config| config.connect_to("synthea_test") }
      end

      def run
        puts "Generating #{@population_count} patients..."
        @threads = []

        # using a cachedthreadpool has no upper bound on the # and if it gets too high then everything grinds to a halt
        @pool = Concurrent::FixedThreadPool.new(Synthea::Config.sequential.thread_pool_size) if Synthea::Config.sequential.multithreading

        if @city_populations
          run_with_target_data
        else
          run_random
        end

        @threads.each(&:join)

        if @pool
          puts 'Generation completed, waiting for files to finish exporting...'
          @pool.shutdown # Tasks already in the queue will be executed, but no new tasks will be accepted.
          @pool.wait_for_termination
        end

        puts "Generated Demographics:"
        puts JSON.pretty_unparse(@stats)
      end

      def run_with_target_data
        @city_populations.each do |city_name,city_stats|
          if Synthea::Config.sequential.multithreading
            @threads << Thread.new do
              process_city(city_name, city_stats)
            end
          else
            process_city(city_name, city_stats)
          end
        end
      end

      def run_random
        @population_count.times do |i|
            person = build_person(nil, rand(0..100), nil, nil, nil)

            if @pool
              @pool.post { export (person) }
            else
              export(person)
            end

            record_stats(person)
            dead = person.had_event?(:death)
            
            puts "##{i+1}#{'(d)' if dead}:  #{person[:name_last]}, #{person[:name_first]}. #{person[:race].to_s.capitalize} #{person[:ethnicity].to_s.gsub('_',' ').capitalize}. #{person[:age]} y/o #{person[:gender]}."
        end
      end

      def process_city(city_name, city_stats)
        population = (city_stats['population'] * @scaling_factor).ceil

        demographics = build_demographics(city_stats, population)            

        puts "Generating #{population} patients within #{city_name}"
        population.times do |i|
          target_gender = demographics[:gender][i]
          target_race = demographics[:race][i]
          target_ethnicity = Synthea::World::Demographics::ETHNICITY[target_race].pick
          target_age = demographics[:age][i]
          try_number = 1
          loop do
            person = build_person(city_name, target_age, target_gender, target_race, target_ethnicity)

            if @pool
              @pool.post { export (person) }
            else
              export(person)
            end

            record_stats(person)
            dead = person.had_event?(:death)
            
            puts "#{city_name} ##{i+1}#{'(d)' if dead}:  #{person[:name_last]}, #{person[:name_first]}. #{person[:race].to_s.capitalize} #{person[:ethnicity].to_s.gsub('_',' ').capitalize}. #{person[:age]} y/o #{person[:gender]}."

            break unless dead
            break if try_number >= Synthea::Config.sequential.max_tries

            try_number += 1
            if try_number > (Synthea::Config.sequential.max_tries / 2) && target_age > 90
              target_age = rand(85..90)
              # demographics count ages up to 110, which our people never hit
            end
          end
        end
      end

      def build_demographics(stats, population)
        gender_ratio = Pickup.new(stats['gender']) { |v| v*100 }
        race_ratio = Pickup.new(stats['race']) { |v| v*100 }
        age_ratio = Pickup.new(stats['ages']) { |v| v*100 }

        demographics = Hash.new() { |hsh, key| hsh[key] = Array.new(population) }

        population.times do |i|
          demographics[:gender][i] = gender_ratio.pick == 'male' ? 'M' : 'F'
          demographics[:race][i] = race_ratio.pick.to_sym
          age_group = age_ratio.pick # gives us a string, we need a range
          demographics[:age][i] = rand( Range.new(*age_group.split('..').map(&:to_i)) )
        end

        demographics.each_value(&:shuffle)

        demographics
      end

      def build_person(city, age, gender, race, ethnicity)
        date = @end_date - age.years
        person = Synthea::Person.new
        person[:gender] = gender
        person[:race] = race
        person[:ethnicity] = ethnicity
        person[:city] = city
        while !person.had_event?(:death) && date<=@end_date
          date += @time_step.days
          Synthea::Rules.apply(date,person)
        end

        person
      end

      def record_stats(patient)
        @stats[:population_count] += 1
        if patient.had_event?(:death)
          @stats[:dead] += 1
        else
          @stats[:living] += 1
        end
        @stats[:age_sum] += patient[:age] # useful for tracking the total # of person-years simulated vs real-world clock time
        @stats[:age][ (patient[:age]/10)*10 ] += 1
        @stats[:gender][ patient[:gender] ] += 1
        @stats[:race][ patient[:race] ] += 1
        @stats[:ethnicity][ patient[:ethnicity] ] += 1
        @stats[:blood_type][ patient[:blood_type] ] += 1
      end

      def export(patient)
        ccda_record = Synthea::Output::CcdaRecord.convert_to_ccda(patient)
        fhir_record = Synthea::Output::FhirRecord.convert_to_fhir(patient)

        out_dir = File.join('output','html')
        html = HealthDataStandards::Export::HTML.new.export(ccda_record)
        File.open(File.join(out_dir, "#{patient.record_synthea.patient_info[:uuid]}.html"), 'w') { |file| file.write(html) }
        
        out_dir = File.join('output','fhir')
        data = fhir_record.to_json
        File.open(File.join(out_dir, "#{patient.record_synthea.patient_info[:uuid]}.json"), 'w') { |file| file.write(data) }

        out_dir = File.join('output','CCDA')
        xml = HealthDataStandards::Export::CCDA.new.export(ccda_record)
        File.open(File.join(out_dir, "#{patient.record_synthea.patient_info[:uuid]}.xml"), 'w') { |file| file.write(xml) }
      end

    end
  end
end