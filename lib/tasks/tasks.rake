namespace :synthea do
  desc 'console'
  task :console, [] do |t, args|
  end

  desc 'fingerprint'
  task :fingerprint, [] do |_t, _args|
    fingerprint = Synthea::Fingerprint.generate
    fingerprint.save('fingerprint_sample.png')
  end

  desc 'generate'
  task :generate, [] do |_t, _args|
    start = Time.now
    world = Synthea::World::Population.new
    world.run
    finish = Time.now
    minutes = ((finish - start) / 60)
    seconds = (minutes - minutes.floor) * 60
    puts "Completed in #{minutes.floor} minute(s) #{seconds.floor} second(s)."
    puts 'Saving patient records...'
    export(world.people | world.dead)
    puts 'Finished.'
  end

  desc 'sequential generation'
  task :sequential, [:datafile] do |_t, args|
    args.with_defaults(datafile: nil)

    datafile = args.datafile
    if datafile
      raise "File not found: #{datafile}" unless File.file?(datafile)
      datafile = File.read(datafile)
    end
    # we need to configure mongo to export for some reason... not ideal
    Mongoid.configure { |config| config.connect_to('synthea_test') }
    %w(html fhir CCDA).each do |type|
      out_dir = File.join('output', type)
      FileUtils.rm_r out_dir if File.exist? out_dir
      FileUtils.mkdir_p out_dir
    end

    start = Time.now
    world = Synthea::World::Sequential.new(datafile)
    world.run
    finish = Time.now
    minutes = ((finish - start) / 60)
    seconds = (minutes - minutes.floor) * 60
    puts "Completed in #{minutes.floor} minute(s) #{seconds.floor} second(s)."
    puts 'Finished.'
  end

  desc 'upload to FHIR server'
  task :fhirupload, [:url] do |_t, args|
    output = Synthea::Output::Exporter.get_output_folder('fhir')
    if File.exist? output
      start = Time.now
      files = File.join(output, '**', '*.json')
      client = FHIR::Client.new(args.url)
      client.default_format = FHIR::Formats::ResourceFormat::RESOURCE_JSON
      puts 'Uploading Patient records...'
      count = 0
      Dir.glob(files).each do |file|
        json = File.open(file, 'r:UTF-8', &:read)
        bundle = FHIR.from_contents(json)
        client.begin_transaction
        bundle.entry.each do |entry|
          # defined our own 'add to transaction' function to preserve our entry information
          add_entry_transaction('POST', nil, entry, client)
        end
        begin
          reply = client.end_transaction
          puts "  Error: #{reply.code}" if reply.code != 200
        rescue Exception => e
          puts "  Error: #{e.message}"
        end
        count += 1
      end
      finish = Time.now
      minutes = ((finish - start) / 60)
      seconds = (minutes - minutes.floor) * 60
      each_time = ((finish - start) / count)
      each_minutes = each_time / 60
      each_seconds = (each_minutes - each_minutes.floor) * 60
      puts "Completed in #{minutes.floor} minute(s) #{seconds.floor} second(s)."
      puts "Average time per record: #{each_minutes.floor} minute(s) #{each_seconds.floor} second(s)."
    else
      puts 'No FHIR patient records have been generated yet.'
      puts 'Run synthea:generate task.'
    end
  end

  # enter host name as command line argument to rake task
  desc 'upload CCDA records using sftp'
  task :ccdaupload, [:url] do |_t, args|
    output = Synthea::Output::Exporter.get_output_folder('CCDA')
    if File.exist? output
      files = File.join(output, '**', '*.xml')
      print 'Username: '
      username = STDIN.gets.chomp
      password = ask('Password: ') { |q| q.echo = false }
      start = Time.now
      Net::SFTP.start(args.url, username, password: password) do |sftp|
        puts 'Uploading files...'
        fileset = Dir.glob(files)
        fileset.each_with_index do |file, index|
          filename = File.basename(file)
          puts "  (#{index + 1}/#{fileset.length}) #{filename}"
          sftp.upload!(file, '/ccda/' + filename)
        end
      end
      finish = Time.now
      minutes = ((finish - start) / 60)
      seconds = (minutes - minutes.floor) * 60
      puts "Completed in #{minutes.floor} minute(s) #{seconds.floor} second(s)."
    end
  end

  def export(patients)
    # we need to configure mongo to export for some reason... not ideal
    if Synthea::Config.exporter.ccda.export || Synthea::Config.exporter.ccda.upload || Synthea::Config.exporter.html.export
      Mongoid.configure { |config| config.connect_to('synthea_test') }
    end

    patients.each do |patient|
      Synthea::Output::Exporter.export(patient)
    end
  end

  desc 'clear_server'
  task :clear_server, [:url] do |_t, args|
    client = FHIR::Client.new(args.url)
    FHIR::RESOURCES.each do |klass|
      clear_resource(klass, client)
    end
  end

  def clear_resource(resource, client)
    reply = client.read_feed(resource)
    while !reply.nil? && !reply.resource.nil? && !reply.resource.entry.empty?
      reply.resource.entry.each do |entry|
        diagnostics = client.destroy(resource, entry.resource.id) unless entry.resource.nil?
        # check for a reference to resource error
        if diagnostics.response[:body].include?('Unable to delete')
          resource_to_delete = diagnostics.response[:body].scan(/First reference found was resource .* in path (\w+)\./)[0][0]
          clear_resource(resource_to_delete, client) unless resource_to_delete.nil?
        end
      end
      reply = client.read_feed(resource)
    end
  end

  # This task requires CSV data downloaded from the US Census Bureau
  # and placed into a folder called `resources`.
  # One file contains town population estimates, another file contains
  # demographic (gender, race) distributions per county.
  # This task merges the two datasets and generates the input files
  # per county within the `config` folder.
  desc 'transform census data'
  task :census, [] do |_t, _args|
    options = { :headers => true, :header_converters => :symbol }
    towns = {}
    counties = {}
    townfile = File.open('./resources/SUB-EST2015_25.csv', 'r:UTF-8')
    CSV.foreach(townfile, options) do |row|
      if row[:primgeo_flag].to_i == 1
        town_name = row[:name].split.keep_if { |x| !%w(town city).include?(x.downcase) }.join(' ')
        towns[town_name] = { population: row[:popestimate2015].to_i, state: row[:stname], county: row[:county] }
      end
      counties[row[:county]] = row[:name] if row[:sumlev].to_i == 50 # county
    end
    # remap county identifiers to county names
    towns.each do |_k, v|
      v[:county] = counties[v[:county]]
      v[:ages] = {}
    end
    townfile.close
    ageGroups = ['Total', (0..4), (5..9), (10..14), (15..19), (20..24), (25..29), (30..34), (35..39), (40..44), (45..49), (50..54), (55..59), (60..64), (65..69), (70..74), (75..79), (80..84), (85..110)]
    countyfile = File.open('./resources/CC-EST2015-ALLDATA-25.csv', 'r:UTF-8')
    CSV.foreach(countyfile, options) do |row|
      # if (2015 estimate) && (total overall demographics)
      if row[:year].to_i == 8 && row[:agegrp].to_i.zero?
        gender = {
          male: (row[:tot_male].to_f / row[:tot_pop].to_f),
          female: (row[:tot_female].to_f / row[:tot_pop].to_f)
        }
        race = {
          white: ((row[:wa_male].to_f + row[:wa_female].to_f) / row[:tot_pop].to_f),
          hispanic: ((row[:h_male].to_f + row[:h_female].to_f) / row[:tot_pop].to_f),
          black: ((row[:ba_male].to_f + row[:ba_female].to_f) / row[:tot_pop].to_f),
          asian: ((row[:aa_male].to_f + row[:aa_female].to_f) / row[:tot_pop].to_f),
          native: ((row[:ia_male].to_f + row[:ia_female].to_f + row[:na_male].to_f + row[:na_female].to_f) / row[:tot_pop].to_f),
          other: 0.001
        }
        towns.each do |_k, v|
          if v[:county] == row[:ctyname]
            v[:gender] = gender
            v[:race] = race
          end
        end
      elsif row[:year].to_i == 8 # (2015 estimate)
        towns.each do |_k, v|
          if v[:county] == row[:ctyname]
            v[:ages][ageGroups[row[:agegrp].to_i]] = row[:tot_pop].to_f
          end
        end
      end
    end
    countyfile.close

    incomefile = File.open('./resources/ACS_14_5YR_S1901_with_ann.csv', 'r:UTF-8')
    CSV.foreach(incomefile, options) do |row|
      next if row[:geoid] == 'Id' # this CSV has 2 header rows
      next if row[:geodisplaylabel].include?('not defined')

      town_name = row[:geodisplaylabel].split(',')[0].split.keep_if { |x| !%w(town city).include?(x.downcase) }.join(' ')

      # these numbers are given at the household level
      # the keys represent 10s of thousands, ie 50..75 means 50,000 to 75,000
      towns[town_name][:income] = { mean: row[:hc01_est_vc15].to_i,
                                    median: row[:hc01_est_vc13].to_i,
                                    '00..10'  => row[:hc01_est_vc02].to_f / 100,
                                    '10..15'  => row[:hc01_est_vc03].to_f / 100,
                                    '15..25'  => row[:hc01_est_vc04].to_f / 100,
                                    '25..35'  => row[:hc01_est_vc05].to_f / 100,
                                    '35..50'  => row[:hc01_est_vc06].to_f / 100,
                                    '50..75'  => row[:hc01_est_vc07].to_f / 100,
                                    '75..100' => row[:hc01_est_vc08].to_f / 100,
                                    '100..150' => row[:hc01_est_vc09].to_f / 100,
                                    '150..200' => row[:hc01_est_vc10].to_f / 100,
                                    '200..999' => row[:hc01_est_vc11].to_f / 100 }
    end
    incomefile.close

    educationfile = File.open('./resources/ACS_14_5YR_S1501_with_ann.csv', 'r:UTF-8')
    CSV.foreach(educationfile, options) do |row|
      next if row[:geoid] == 'Id' # this CSV has 2 header rows
      next if row[:geodisplaylabel].include?('not defined')

      town_name = row[:geodisplaylabel].split(',')[0].split.keep_if { |x| !%w(town city).include?(x.downcase) }.join(' ')

      # the data allows for more granular categories (like 24-35 graduate degree) but these overall %s are good enough for our purposes
      towns[town_name][:education] = { less_than_hs: row[:hc01_est_vc02].to_f / 100,
                                       hs_degree: row[:hc01_est_vc03].to_f / 100,
                                       some_college: row[:hc01_est_vc04].to_f / 100,
                                       bs_degree: row[:hc01_est_vc05].to_f / 100 }
    end
    educationfile.close

    # convert the age groups to probability
    towns.each do |_k, v|
      total = v[:ages].values.inject(0) { |i, j| i + j }
      v[:ages].each { |i, j| v[:ages][i] = (j / total) }
    end
    output = File.open('./config/towns.json', 'w:UTF-8')
    output.write(JSON.pretty_unparse(towns))
    output.close
    puts 'Wrote JSON to ./config/towns.json'
    counties.each do |_k, county|
      output = File.open("./config/#{county.tr(' ', '_')}.json", 'w:UTF-8')
      output.write(JSON.pretty_unparse(towns.select { |_k, t| t[:county] == county }))
      output.close
      puts "Wrote JSON for #{county}."
    end
    puts 'Done.'
  end
end
