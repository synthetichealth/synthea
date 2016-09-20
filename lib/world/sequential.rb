module Synthea
  module World
    class Sequential

      attr_reader :stats
      attr_accessor :population_count

      def initialize(datafile=nil)
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

        Synthea::Rules.get_modules # trigger the loading of modules here, to ensure they are set before all threads start
      end

      def run
        puts "Generating #{@population_count} patients..."

        if Synthea::Config.sequential.multithreading
          pool_size = Synthea::Config.sequential.thread_pool_size
          @city_workers = Concurrent::FixedThreadPool.new(pool_size.city_workers)
          @generate_workers = Concurrent::ThreadPoolExecutor.new(
                                                                   min_threads: pool_size.generate_workers,
                                                                   max_threads: pool_size.generate_workers,
                                                                   max_queue: pool_size.generate_workers * 2,
                                                                   fallback_policy: :caller_runs
                                                                )
          @export_workers = Concurrent::FixedThreadPool.new(pool_size.export_workers)
        end

        if @city_populations
          @city_populations.each do |city_name,city_stats|
            if Synthea::Config.sequential.multithreading
              @city_workers.post { process_city(city_name, city_stats) }
            else
              process_city(city_name, city_stats)
            end
          end
        else
          run_random
        end


        if Synthea::Config.sequential.multithreading
          @city_workers.shutdown # Tasks already in the queue will be executed, but no new tasks will be accepted.
          @city_workers.wait_for_termination
          puts "All cities (#{@city_workers.scheduled_task_count}) have been processed, waiting for generation..."

          @generate_workers.shutdown
          @generate_workers.wait_for_termination
          puts "Generation completed (#{@generate_workers.scheduled_task_count} population), waiting for files to finish exporting..."

          @export_workers.shutdown
          @export_workers.wait_for_termination
        end

        puts "Generated Demographics:"
        puts JSON.pretty_unparse(@stats)
      end

      def run_random
        @population_count.times do |i|
            person = build_person

            if @export_workers
              @export_workers.post { Synthea::Output::Exporter.export(person) }
            else
              Synthea::Output::Exporter.export(person)
            end

            record_stats(person)
            dead = person.had_event?(:death)
            conditions = track_conditions(person)

            puts "##{i+1}#{'(d)' if dead}:  #{person[:name_last]}, #{person[:name_first]}. #{person[:race].to_s.capitalize} #{person[:ethnicity].to_s.gsub('_',' ').capitalize}. #{person[:age]} y/o #{person[:gender]} -- #{conditions.join(', ')}"
        end
      end

      def process_city(city_name, city_stats)
        population = (city_stats['population'] * @scaling_factor).ceil

        demographics = build_demographics(city_stats, population)

        puts "Generating #{population} patients within #{city_name}"
        population.times do |i|
          if @generate_workers
            @generate_workers.post { process_person(city_name, population, demographics, i) }
          else
            process_person(city_name, population, demographics, i)
          end
        end
      end

      def process_person(city_name, population, demographics, i)
        target_gender = demographics[:gender][i]
        target_race = demographics[:race][i]
        target_ethnicity = Synthea::World::Demographics::ETHNICITY[target_race].pick
        target_age = demographics[:age][i]
        target_income = demographics[:income][i]
        target_education = demographics[:education][i]
        try_number = 1
        loop do
          person = build_person(city: city_name, age: target_age, gender: target_gender,
                                race: target_race, ethnicity: target_ethnicity,
                                income: target_income, education: target_education)

          if @export_workers
            @export_workers.post { Synthea::Output::Exporter.export(person) }
          else
            Synthea::Output::Exporter.export(person)
          end

          record_stats(person)
          dead = person.had_event?(:death)

          puts "#{city_name} ##{i+1}/#{population}#{'(d)' if dead}:  #{person[:name_last]}, #{person[:name_first]}. #{person[:race].to_s.capitalize} #{person[:ethnicity].to_s.gsub('_',' ').capitalize}. #{person[:age]} y/o #{person[:gender]}."

          break unless dead
          break if try_number >= Synthea::Config.sequential.max_tries

          try_number += 1
          if try_number > (Synthea::Config.sequential.max_tries / 2) && target_age > 90
            target_age = rand(85..90)
            # demographics count ages up to 110, which our people never hit
          end
        end
      end

      def build_demographics(stats, population)
        gender_ratio = Pickup.new(stats['gender']) { |v| v*100 }
        race_ratio = Pickup.new(stats['race']) { |v| v*100 }
        age_ratio = Pickup.new(stats['ages']) { |v| v*100 }
        education_ratio = Pickup.new(stats['education']) { |v| v*100 }
        income_stats = stats['income']
        income_stats.delete('median')
        income_stats.delete('mean')
        income_ratio = Pickup.new(income_stats) { |v| v*100 }

        demographics = Hash.new() { |hsh, key| hsh[key] = Array.new(population) }

        population.times do |i|
          demographics[:gender][i] = gender_ratio.pick == 'male' ? 'M' : 'F'
          demographics[:race][i] = race_ratio.pick.to_sym
          age_group = age_ratio.pick # gives us a string, we need a range
          demographics[:age][i] = rand( Range.new(*age_group.split('..').map(&:to_i)) )
          demographics[:education][i] = education_ratio.pick
          demographics[:income][i] = rand( Range.new(*age_group.split('..').map(&:to_i)) ) * 1000
        end

        demographics
      end

      def build_person(options={})
        target_age = options[:age] || rand(0..100)
        options.delete('age')

        earliest_birthdate = @end_date - (target_age+1).years + 1.day
        latest_birthdate = @end_date - target_age.years

        date = rand(earliest_birthdate..latest_birthdate)

        person = Synthea::Person.new
        options.each { |k,v| person[k] = v }
        while !person.had_event?(:death) && date<=@end_date
          date += @time_step.days
          Synthea::Rules.apply(date,person)
        end

        person
      end

      def track_conditions(patient)
        conditions = []
        addict = patient[:generic]["Opioid Addiction"].history.find{|x|x.name=='Active_Addiction'} rescue nil
        conditions << "Opioid Addict" if addict
        conditions << "Diabetic" if patient[:diabetes]
        conditions << "Heart Disease" if patient[:coronary_heart_disease]
        conditions
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

        conditions = track_conditions(patient)
        conditions.each do |condition|
          @stats[condition] += 1
        end
      end
    end
  end
end
