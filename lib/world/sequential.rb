module Synthea
  module World
    class Sequential
      attr_reader :stats
      attr_accessor :population_count

      def initialize(datafile = nil)
        @start_date = Synthea::Config.start_date
        @end_date = Synthea::Config.end_date
        @time_step = Synthea::Config.time_step

        @stats = Hash.new(0)
        @stats[:age] = Hash.new(0)
        @stats[:gender] = Hash.new(0)
        @stats[:race] = Hash.new(0)
        @stats[:ethnicity] = Hash.new(0)
        @stats[:blood_type] = Hash.new(0)
        @stats[:dead_occurrences] = {
          :active_conditions => Hash.new(0),
          :total_conditions => Hash.new(0),
          :people_afflicted => Hash.new(0),
          :active_medications => Hash.new(0),
          :total_medications => Hash.new(0),
          :people_prescribed => Hash.new(0)
        }
        @stats[:living_occurrences] = {
          :active_conditions => Hash.new(0),
          :total_conditions => Hash.new(0),
          :people_afflicted => Hash.new(0),
          :active_medications => Hash.new(0),
          :total_medications => Hash.new(0),
          :people_prescribed => Hash.new(0)
        }
        @stats[:occurrences] = {
          :active_conditions => Hash.new(0),
          :total_conditions => Hash.new(0),
          :people_afflicted => Hash.new(0),
          :active_medications => Hash.new(0),
          :total_medications => Hash.new(0),
          :people_prescribed => Hash.new(0)
        }

        @population_count = Synthea::Config.sequential.population
        @generate_count = Concurrent::AtomicFixnum.new(0)
        @export_count = Concurrent::AtomicFixnum.new(0)
        @generate_log_interval = Synthea::Config.sequential.debug_log_interval.generate
        @export_log_interval = Synthea::Config.sequential.debug_log_interval.export
        @enable_debug_logging = Synthea::Config.sequential.enable_debug_logging

        @scaling_factor = @population_count.to_f / Synthea::Config.sequential.real_world_population.to_f
        # if you want to generate a population smaller than 7M but still with accurate ratios,
        #  you can scale the populations of individual cities down by this amount.

        @city_populations = JSON.parse(datafile) if datafile

        Synthea::Rules.modules # trigger the loading of modules here, to ensure they are set before all threads start
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
          @city_populations.each do |city_name, city_stats|
            run_task(@city_workers) do
              process_city(city_name, city_stats)
            end
          end
        else
          run_random
        end

        if Synthea::Config.sequential.multithreading
          @city_workers.shutdown # Tasks already in the queue will be executed, but no new tasks will be accepted.
          @city_workers.wait_for_termination
          puts "#{timestamp} All cities have been started, waiting for generation to finish..."

          @generate_workers.shutdown
          @generate_workers.wait_for_termination
          puts "#{timestamp} Generation completed (#{@generate_count.value} population), waiting for files to finish exporting..."

          @export_workers.shutdown
          @export_workers.wait_for_termination
        end

        puts 'Generated Demographics:'
        puts JSON.pretty_unparse(@stats)

        if Synthea::Config.generic.log_state_statistics
          puts 'State Statistics:'
          puts JSON.pretty_unparse(Synthea::Generic::Context.counter)
        end

        puts 'Outputting Metabolic Syndrome Results to output/prevalences.csv'
        @prevalence_file = File.open('output/prevalences.csv', 'w:UTF-8')
        @prevalence_file.write("ITEM,POPULATION TYPE,OCCURRENCES,POPULATION COUNT,PREVALENCE RATE,PREVALENCE PERCENTAGE\n")
        metabolic_conditions = [
          'Hypertension',
          'Prediabetes',
          'Diabetes',
          'Diabetic renal disease (disorder)',
          'Microalbuminuria due to type 2 diabetes mellitus (disorder)',
          'Proteinuria due to type 2 diabetes mellitus (disorder)',
          'End stage renal disease (disorder)',
          'Diabetic retinopathy associated with type II diabetes mellitus (disorder)',
          'Nonproliferative diabetic retinopathy due to type 2 diabetes mellitus (disorder)',
          'Proliferative diabetic retinopathy due to type II diabetes mellitus (disorder)',
          'Macular edema and retinopathy due to type 2 diabetes mellitus (disorder)',
          'Blindness due to type 2 diabetes mellitus (disorder)',
          'Neuropathy due to type 2 diabetes mellitus (disorder)',
          'History of lower limb amputation (situation)',
          'History of amputation of foot (situation)',
          'History of upper limb amputation (situation)',
          'History of disarticulation at wrist (situation)'
        ]
        metabolic_conditions.each do |condition|
          all_prevalences(condition, :people_afflicted, condition.downcase.tr(' ', '_').to_sym)
        end

        metabolic_medications = [
          '24 HR Metformin hydrochloride 500 MG Extended Release Oral Tablet',
          '3 ML liraglutide 6 MG/ML Pen Injector',
          'canagliflozin 100 MG Oral Tablet',
          'insulin human, isophane 70 UNT/ML / Regular Insulin, Human 30 UNT/ML Injectable Suspension [Humulin]',
          'Insulin Lispro 100 UNT/ML Injectable Solution [Humalog]'
        ]
        metabolic_medications.each do |medication|
          all_prevalences(medication, :people_prescribed, medication.downcase.tr(' ', '_').to_sym)
        end
        @prevalence_file.close
      end

      def all_prevalences(description, category, type)
        prevalence("#{description.tr(',', '')},TOTAL", @stats[:occurrences][category][type], @stats[:population_count])
        prevalence("#{description.tr(',', '')},LIVING", @stats[:living_occurrences][category][type], @stats[:living])
        prevalence("#{description.tr(',', '')},DEAD", @stats[:dead_occurrences][category][type], @stats[:dead])
      end

      def prevalence(description, numerator, denominator)
        numerator = 0 if numerator.nil?
        result = numerator.to_f / denominator.to_f
        @prevalence_file.write("#{description},#{numerator},#{denominator},#{result},#{(100 * result).round(2)}\n")
      end

      def run_random
        i = 0
        while @stats[:living] < @population_count
          person = build_person
          run_task(@export_workers) do
            @export_count.increment
            log_thread_pool(@export_workers, 'Export Workers') if @enable_debug_logging && (@export_count.value % @export_log_interval).zero?
            Synthea::Output::Exporter.export(person)
          end

          occurrences = record_stats(person)
          i += 1
          occurrences[:number] = i
          occurrences[:is_dead] = person.had_event?(:death)
          log_patient(person, occurrences)
        end
      end

      def process_city(city_name, city_stats)
        population = (city_stats['population'] * @scaling_factor).ceil

        demographics = build_demographics(city_stats, population)

        puts "Generating #{population} patients within #{city_name}"
        population.times do |i|
          run_task(@generate_workers) do
            @generate_count.increment
            log_thread_pool(@generate_workers, 'Generate Workers') if @enable_debug_logging && (@generate_count.value % @generate_log_interval).zero?
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

          run_task(@export_workers) do
            @export_count.increment
            log_thread_pool(@export_workers, 'Export Workers') if @enable_debug_logging && (@export_count.value % @export_log_interval).zero?
            Synthea::Output::Exporter.export(person)
          end

          occurrences = record_stats(person)
          occurrences[:number] = i + 1
          occurrences[:is_dead] = person.had_event?(:death)
          occurrences[:city_name] = city_name
          occurrences[:city_pop] = population
          log_patient(person, occurrences)

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
        gender_ratio = Pickup.new(stats['gender']) { |v| v * 100 }
        race_ratio = Pickup.new(stats['race']) { |v| v * 100 }
        age_ratio = Pickup.new(stats['ages']) { |v| v * 100 }
        education_ratio = Pickup.new(stats['education']) { |v| v * 100 }
        income_stats = stats['income']
        income_stats.delete('median')
        income_stats.delete('mean')

        demographics = Hash.new { |hsh, key| hsh[key] = Array.new(population) }

        population.times do |i|
          demographics[:gender][i] = gender_ratio.pick == 'male' ? 'M' : 'F'
          demographics[:race][i] = race_ratio.pick.to_sym
          age_group = age_ratio.pick # gives us a string, we need a range
          demographics[:age][i] = rand(Range.new(*age_group.split('..').map(&:to_i)))
          demographics[:education][i] = education_ratio.pick
          demographics[:income][i] = rand(Range.new(*age_group.split('..').map(&:to_i))) * 1000
        end

        demographics
      end

      def build_person(options = {})
        target_age = options[:age] || rand(0..100)
        options.delete('age')

        earliest_birthdate = @end_date - (target_age + 1).years + 1.day
        latest_birthdate = @end_date - target_age.years

        date = rand(earliest_birthdate..latest_birthdate)

        person = Synthea::Person.new
        options.each { |k, v| person[k] = v }
        while !person.had_event?(:death, date) && date <= @end_date
          date += @time_step.days
          Synthea::Rules.apply(date, person)
        end
        Synthea::Modules::Generic.log_modules(person)
        person
      end

      def track_occurrences(patient)
        # Track Diagnosed Conditions
        active_occurrences = Hash.new(0)
        patient.record_synthea.conditions.select { |c| c['end_time'].nil? }.each { |c| active_occurrences[c['type']] += 1 }
        total_occurrences = Hash.new(0)
        patient.record_synthea.conditions.each { |c| total_occurrences[c['type']] += 1 }
        unique_conditions = Hash.new(0)
        patient.record_synthea.conditions.map { |c| c['type'] }.uniq.each { |c| unique_conditions[c] = 1 }

        # Track Medications
        active_medications = Hash.new(0)
        patient.record_synthea.medications.select { |c| c['stop'].nil? }.each { |c| active_medications[c['type']] += 1 }
        total_medications = Hash.new(0)
        patient.record_synthea.medications.each { |c| total_medications[c['type']] += 1 }
        unique_medications = Hash.new(0)
        patient.record_synthea.medications.map { |c| c['type'] }.uniq.each { |c| unique_medications[c] = 1 }

        {
          :active_conditions => active_occurrences,
          :total_conditions => total_occurrences,
          :people_afflicted => unique_conditions,
          :active_medications => active_medications,
          :total_medications => total_medications,
          :people_prescribed => unique_medications
        }
      end

      def log_patient(person, options = {})
        str = ''
        str << timestamp << ' '
        str << options[:city_name] << ' ' if options[:city_name]
        str << options[:number].to_s if options[:number]
        str << '/' << options[:city_pop].to_s if options[:city_pop]
        str << (person[:cause_of_death] ? "(d: #{person[:cause_of_death]})" : '(d)') if options[:is_dead]
        str << ': '
        str << "#{person[:name_last]}, #{person[:name_first]}. #{person[:race].to_s.capitalize} #{person[:ethnicity].to_s.tr('_', ' ').capitalize}. #{person[:age]} y/o #{person[:gender]}"

        weight = (person.get_vital_sign_value(:weight) * 2.20462).to_i
        str << " #{weight} lbs. -- #{options[:active_conditions].keys.map(&:to_s).join(', ')}"

        puts str
      end

      def run_task(pool)
        if pool
          pool.post do
            begin
              yield
            rescue => e
              handle_exception(e)
            end
          end
        else
          begin
            yield
          rescue => e
            handle_exception(e)
          end
        end
      end

      def handle_exception(e)
        puts e
        puts e.backtrace
        exit! if Synthea::Config.sequential.abort_on_exception
      end

      def log_thread_pool(pool, name)
        return unless pool
        puts "#{timestamp} #{name} -- Queue Length: #{pool.queue_length}, Workers (Active/Max): #{pool.length}/#{pool.max_length}, Total Completed: #{pool.completed_task_count}"
      end

      def record_stats(patient)
        @stats[:population_count] += 1
        if patient.had_event?(:death)
          @stats[:dead] += 1
        else
          @stats[:living] += 1
        end
        @stats[:age_sum] += patient[:age] # useful for tracking the total # of person-years simulated vs real-world clock time
        @stats[:age][(patient[:age] / 10) * 10] += 1
        @stats[:gender][patient[:gender]] += 1
        @stats[:race][patient[:race]] += 1
        @stats[:ethnicity][patient[:ethnicity]] += 1
        @stats[:blood_type][patient[:blood_type]] += 1

        occurrences = track_occurrences(patient)
        add_occurrences(@stats[:occurrences], occurrences)
        if patient.had_event?(:death)
          add_occurrences(@stats[:dead_occurrences], occurrences)
        else
          add_occurrences(@stats[:living_occurrences], occurrences)
        end
        occurrences
      end

      def add_occurrences(total, occurrences)
        occurrences.each do |category, hash|
          hash.each do |key, value|
            total[category][key] += value
          end
        end
      end

      def timestamp
        Time.now.strftime('[%F %T]')
      end
    end
  end
end
