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
        @stats[:language] = Hash.new(0)
        @stats[:ethnicity] = Hash.new(0)
        @stats[:blood_type] = Hash.new(0)
        @stats[:living_adults_by_race] = Hash.new(0)
        @stats[:occurrences] = nested_hash(7) { Concurrent::AtomicFixnum.new(0) }

        @stats[:all_occurrences] = {
          :active_conditions => Hash.new(0),
          :total_conditions => Hash.new(0),
          :people_afflicted => Hash.new(0),
          :active_medications => Hash.new(0),
          :total_medications => Hash.new(0),
          :people_prescribed => Hash.new(0)
        }
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

        @population_count = Synthea::Config.sequential.population
        @only_dead_patients = Synthea::Config.sequential.only_dead_patients
        @generate_count = Concurrent::AtomicFixnum.new(0)
        @export_count = Concurrent::AtomicFixnum.new(0)
        @generate_log_interval = Synthea::Config.sequential.debug_log_interval.generate
        @export_log_interval = Synthea::Config.sequential.debug_log_interval.export
        @enable_debug_logging = Synthea::Config.sequential.enable_debug_logging

        # "top-level" conditions just means that we care about prevalence of some condition GIVEN the top-level condition
        # for example, we want to track % of patients that have hypertension given diabetes,
        # so diabetes is the top-level condition
        @top_level_conditions = []
        if Synthea::Config.sequential.track_detailed_prevalence
          template_file = File.join('.', 'resources', 'prevalence_template.csv')
          if File.exist?(template_file)
            template = CSV.table(template_file)
            # pull out the conditions we care about
            conds = template.collect { |r| r[:given_condition] }.uniq.compact - ['*']
            @top_level_conditions += conds.map { |c| c.downcase.tr(' ', '_').to_sym }
          end
        end

        @scaling_factor = @population_count.to_f / Synthea::Config.sequential.real_world_population.to_f
        # if you want to generate a population smaller than 7M but still with accurate ratios,
        #  you can scale the populations of individual cities down by this amount.

        @city_populations = JSON.parse(datafile) if datafile

        # import hospitals
        @geom = GeoRuby::SimpleFeatures::Geometry.from_geojson(Synthea::HEALTHCARE_FACILITIES)
        @geom.features.each do |h|
          Synthea::Hospital.new(h.properties, h.geometry.to_coordinates)
        end

        Synthea::Rules.modules # trigger the loading of modules here, to ensure they are set before all threads start
      end

      def nested_hash(depth, &default_value)
        return yield if depth < 1
        Hash.new { |h, k| h[k] = nested_hash(depth - 1, &default_value) }
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
        puts JSON.pretty_unparse(@stats.except(:occurrences, :all_occurrences, :living_occurrences, :dead_occurrences)) # occurrences is way too unwieldy to print out

        if Synthea::Config.generic.log_state_statistics
          puts 'State Statistics:'
          puts JSON.pretty_unparse(Synthea::Generic::Context.counter)
        end

        if Synthea::Config.sequential.track_detailed_prevalence
          folder = Synthea::Config.exporter.location
          FileUtils.mkdir_p folder unless File.exist? folder
          puts "Outputting All Prevalence Data to #{folder}/all_prevalences.csv"

          all_prevalence = File.open(File.join(folder, 'all_prevalences.csv'), 'w:UTF-8')
          all_prevalence.write("ITEM,POPULATION TYPE,OCCURRENCES,POPULATION COUNT,PREVALENCE RATE,PREVALENCE PERCENTAGE\n")
          conditions = @stats[:occurrences][:unique_conditions]['*']['*']['*']['*']['*'].keys
          conditions.each do |condition|
            write_prevalences(all_prevalence, condition.to_s.titleize, :unique_conditions, condition)
          end

          medications = @stats[:occurrences][:unique_medications]['*']['*']['*']['*']['*'].keys
          medications.each do |medication|
            write_prevalences(all_prevalence, medication.to_s.titleize, :unique_medications, medication)
          end

          all_prevalence.close

          template_file = File.join('.', 'resources', 'prevalence_template.csv')
          if File.exist?(template_file)
            puts "Outputting Targeted Prevalence Data to #{folder}/targeted_prevalences.csv"

            # open new csv for writing
            CSV.open(File.join(folder, 'targeted_prevalences.csv'), 'wb') do |prevalence_file|
              # iterating existing csv rows - fill in the template and add to output file
              CSV.foreach(template_file, :headers => true, :return_headers => true, :header_converters => :symbol) do |row|
                if row[:status] && row[:status] != 'STATUS' # no processing for blank or header rows, just copy them over
                  category = row[:category].downcase.tr(' ', '_').to_sym
                  code = row[:item].downcase.tr(' ', '_').to_sym
                  precondition = row[:given_condition]
                  precondition = precondition.downcase.tr(' ', '_').to_sym unless precondition == '*'
                  count, population = *retrieve_prevalence(status: row[:status], age: row[:age_group], gender: row[:gender],
                                                           race: row[:race], given_condition: precondition,
                                                           category: category, code: code)

                  row[:synthea_occurrences] = count
                  row[:synthea_population] = population

                  if population.zero?
                    row[:synthea_prevalence_rate] = 0
                    row[:synthea_prevalence_percent] = 0
                  else
                    result = count.to_f / population.to_f
                    row[:synthea_prevalence_rate] = result.round(2)
                    row[:synthea_prevalence_percent] = (100 * result).round(2)
                  end

                  difference = (row[:synthea_prevalence_percent] - row[:actual_prevalence_percent].to_f).round(2) unless row[:actual_prevalence_percent].nil?
                  row[:difference] = difference if difference
                end

                prevalence_file << row
              end
            end
          end
        end
        # export hospital information
        Synthea::Output::HospitalExporter.export
      end

      def write_prevalences(file, description, category, type)
        # prevalence("#{description.tr(',', '')},TOTAL", @stats[:occurrences][category][type], @stats[:population_count])
        write_prevalence(file, "#{description.tr(',', '')},LIVING", *retrieve_prevalence(status: 'living', category: category, code: type))
        # prevalence("#{description.tr(',', '')},DEAD", @stats[:dead_occurrences][category][type], @stats[:dead])
      end

      def write_prevalence(file, description, numerator, denominator)
        numerator = 0 if numerator.nil?
        result = numerator.to_f / denominator.to_f
        file.write("#{description},#{numerator},#{denominator},#{result},#{(100 * result).round(2)}\n")
      end

      # Will only generate dead patients.
      def run_random
        i = 0
        # Depending on the only_dead_patients value which is set in the configuration file,
        # the symbol to use for indexing the @stats hash will either be :dead or :living
        stats_to_use = @only_dead_patients ? :dead : :living
        # While loop will keep running until all of the requested patients have been generated.
        while @stats[stats_to_use] < @population_count
          person = build_person
          run_task(@export_workers) do
            @export_count.increment
            log_thread_pool(@export_workers, 'Export Workers') if @enable_debug_logging && (@export_count.value % @export_log_interval).zero?
            next if @only_dead_patients && person.alive?(@end_date)
            Synthea::Output::Exporter.export(person)
          end
          # Check if the person has died before the end date and that the death has a recorded cause of death.
          next if @only_dead_patients && (person.alive?(@end_date) || !person[:cause_of_death])
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

          is_dead = person.had_event?(:death)

          occurrences = record_stats(person)
          occurrences[:number] = i + 1
          occurrences[:is_dead] = is_dead
          occurrences[:city_name] = city_name
          occurrences[:city_pop] = population
          log_patient(person, occurrences)

          break unless is_dead
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

      def track_occurrences(patient, top_level_conditions)
        conditions = patient.record_synthea.conditions
        medications = patient.record_synthea.medications

        # Track Diagnosed Conditions
        active_occurrences = conditions.select { |c| c['end_time'].nil? }.map { |c| c['type'] }
        total_occurrences = conditions.map { |c| c['type'] }
        unique_conditions = total_occurrences.uniq

        # Track Medications
        active_medications = medications.select { |c| c['stop'].nil? }.map { |c| c['type'] }
        total_medications = medications.map { |c| c['type'] }
        unique_medications = total_medications.uniq

        if Synthea::Config.sequential.track_detailed_prevalence
          age = patient[:age] >= 18 ? 'adult' : 'child'
          gender = patient[:gender]
          status = patient.had_event?(:death) ? 'dead' : 'living'
          race = patient[:race].to_s

          tlcs = top_level_conditions & unique_conditions # intersection of conditions we care about, and the ones they have

          increment_counts(:population, :population, status, age, gender, race, tlcs)

          active_occurrences.each { |c| increment_counts(:active_conditions, c, status, age, gender, race, tlcs) }
          total_occurrences.each { |c| increment_counts(:total_conditions, c, status, age, gender, race, tlcs) }
          unique_conditions.each { |c| increment_counts(:unique_conditions, c, status, age, gender, race, tlcs) }

          active_medications.each { |c| increment_counts(:active_medications, c, status, age, gender, race, tlcs) }
          total_medications.each { |c| increment_counts(:total_medications, c, status, age, gender, race, tlcs) }
          unique_medications.each { |c| increment_counts(:unique_medications, c, status, age, gender, race, tlcs) }
        end

        {
          :active_conditions => active_occurrences,
          :total_conditions => total_occurrences,
          :people_afflicted => unique_conditions,
          :active_medications => active_medications,
          :total_medications => total_medications,
          :people_prescribed => unique_medications
        }
      end

      def increment_counts(category, code, living, age, gender, race, top_level_conditions)
        # we have a n-dimensional map of (variables) -> count.
        # for each variable, keep track of the overall total with *
        # examples:
        #   all living people with diabetes ==> @stats[][living][*][*][*][*][diabetes]
        #   all white adults with esrd given diabetes ==> @stats[][*][adult][*][white][diabetes][esrd]
        # we can make these more granular if we want to. ex include age by specific year or age range instead of just adult/child

        statuses = [living, '*']
        ages = [age, '*']
        genders = [gender, '*']
        races = [race, '*']
        conditions = top_level_conditions + ['*']

        # this looks like a massive loop, but each list will only have a couple items in it
        statuses.each do |s|
          ages.each do |a|
            genders.each do |g|
              races.each do |r|
                conditions.each do |c|
                  @stats[:occurrences][category][s][a][g][r][c][code].increment
                end
              end
            end
          end
        end
      end

      def retrieve_prevalence(options)
        status = options[:status] || '*'
        age = options[:age] || '*'
        gender = options[:gender] || '*'
        race = options[:race] || '*'
        condition = options[:given_condition] || '*'
        category = options[:category]
        code = options[:code]

        count = @stats[:occurrences][category][status][age][gender][race][condition][code]

        population = @stats[:occurrences][:population][status][age][gender][race][condition][:population]

        [count.value, population.value]
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
        str << " #{weight} lbs. -- #{options[:active_conditions].map(&:to_s).join(', ')}"

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
          if patient[:age] >= 18
            @stats[:living_adults] += 1
            @stats[:living_adults_by_race][patient[:race]] += 1
          end
        end
        @stats[:age_sum] += patient[:age] # useful for tracking the total # of person-years simulated vs real-world clock time
        @stats[:age][(patient[:age] / 10) * 10] += 1
        @stats[:adults] += 1 if patient[:age] >= 18
        @stats[:gender][patient[:gender]] += 1
        @stats[:race][patient[:race]] += 1
        @stats[:language][patient[:first_language]] += 1
        @stats[:ethnicity][patient[:ethnicity]] += 1
        @stats[:blood_type][patient[:blood_type]] += 1

        if patient[:gender] == 'F'
          current_rate = if @stats[:birth_rate].nil?
                           patient[:number_of_children]
                         else
                           @stats[:birth_rate]
                         end
          children = if patient[:number_of_children].nil?
                       0
                     else
                       patient[:number_of_children]
                     end
          @stats[:birth_rate] = ((current_rate + children) / 2.0)
        end

        occurrences = track_occurrences(patient, @top_level_conditions)
        add_occurrences(@stats[:all_occurrences], occurrences)
        if patient.had_event?(:death)
          add_occurrences(@stats[:dead_occurrences], occurrences)
        else
          add_occurrences(@stats[:living_occurrences], occurrences)
        end
        occurrences
      end

      def add_occurrences(total, occurrences)
        occurrences.each do |category, list|
          list.each do |key|
            total[category][key] += 1
          end
        end
      end

      def timestamp
        Time.now.strftime('[%F %T]')
      end
    end
  end
end
