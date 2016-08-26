namespace :synthea do
  
  desc 'console'
  task :console, [] do |t, args|
    binding.pry
  end

  desc 'generate'
  task :generate, [] do |t, args|
    start = Time.now
    world = Synthea::World::Population.new
    world.run
    finish = Time.now
    minutes = ((finish-start)/60)
    seconds = (minutes - minutes.floor) * 60
    puts "Completed in #{minutes.floor} minute(s) #{seconds.floor} second(s)."
    puts "Saving patient records..."
    fhir_export(world.people | world.dead) if Synthea::Config.export.fhir
    ccda_export(world.people | world.dead) if Synthea::Config.export.ccda || Synthea::Config.export.html
    binding.pry
    puts 'Finished.'
  end

  desc 'sequential generation'
  task :sequential, [:datafile] do |t, args|
    args.with_defaults(datafile: nil)

    datafile = args.datafile
    if datafile
      raise "File not found: #{datafile}" if !File.file?(datafile)
      datafile = File.read(datafile)
    end

    start = Time.now
    world = Synthea::World::Sequential.new(datafile)
    world.run
    finish = Time.now
    minutes = ((finish-start)/60)
    seconds = (minutes - minutes.floor) * 60
    puts "Completed in #{minutes.floor} minute(s) #{seconds.floor} second(s)."
    puts 'Finished.'    
  end

  desc 'upload to FHIR server'
  task :fhirupload, [:url] do |t,args|
    output = File.join('output','fhir')
    if File.exists? output
      start = Time.now
      files = File.join(output, '**', '*.json')
      client = FHIR::Client.new(args.url)
      client.default_format = FHIR::Formats::ResourceFormat::RESOURCE_JSON
      puts 'Uploading Patient records...'
      count = 0
      Dir.glob(files).each do | file |
        json = File.open(file,'r:UTF-8',&:read)
        bundle = FHIR.from_contents(json)
        client.begin_transaction
        bundle.entry.each do |entry|
          #defined our own 'add to transaction' function to preserve our entry information
          add_entry_transaction('POST',nil,entry,client)
        end
        begin
          reply = client.end_transaction
          puts "  Error: #{reply.code}" if reply.code!=200
        rescue Exception => e
          puts "  Error: #{e.message}"
        end
        count += 1
      end
      finish = Time.now
      minutes = ((finish-start)/60)
      seconds = (minutes - minutes.floor) * 60
      each_time = ((finish-start)/count)
      each_minutes = each_time / 60
      each_seconds = (each_minutes - each_minutes.floor) * 60
      puts "Completed in #{minutes.floor} minute(s) #{seconds.floor} second(s)."
      puts "Average time per record: #{each_minutes.floor} minute(s) #{each_seconds.floor} second(s)."
    else
      puts 'No FHIR patient records have been generated yet.'
      puts 'Run synthea:generate task.'
    end
  end

  #enter host name as command line argument to rake task
  desc 'upload CCDA records using sftp'
  task :ccdaupload, [:url] do |t,args|
    output = File.join('output','ccda')
    if File.exists? output
      files = File.join(output, '**', '*.xml')
      print "Username: "
      username = STDIN.gets.chomp
      password = ask("Password: ") {|q| q.echo = false}
      start = Time.now
      Net::SFTP.start(args.url, username, :password => password) do |sftp|
        puts "Uploading files..."
        fileset = Dir.glob(files)
        fileset.each_with_index do | file, index |
          filename = File.basename(file)
          puts "  (#{index+1}/#{fileset.length}) #{filename}"
          sftp.upload!(file, "/ccda/" + filename)
        end
      end
      finish = Time.now
      minutes = ((finish-start)/60)
      seconds = (minutes - minutes.floor) * 60
      puts "Completed in #{minutes.floor} minute(s) #{seconds.floor} second(s)."
    end
  end

  def ccda_export(patients)
    # we need to configure mongo to export for some reason... not ideal
    Mongoid.configure { |config| config.connect_to("synthea_test") }

    ['html','fhir','CCDA'].each do |type|
      out_dir = File.join('output',type)
      FileUtils.rm_r out_dir if File.exists? out_dir
      FileUtils.mkdir_p out_dir
    end
    patients.each do |patient|
      Synthea::Output::Exporter.export(patient)
    end
  end

  def fhir_export(patients)
    out_dir = File.join('output','fhir')
    FileUtils.rm_r out_dir if File.exists? out_dir
    FileUtils.mkdir_p out_dir
    patients.each do |patient|
      fhir_record = Synthea::Output::FhirRecord.convert_to_fhir(patient)
      data = fhir_record.to_json
      File.open(File.join(out_dir, "#{patient.record_synthea.patient_info[:uuid]}.json"), 'w') { |file| file.write(data) }
    end
  end

  def add_entry_transaction(method, url, entry=nil, client)
    request = FHIR::Bundle::Entry::Request.new
    request.local_method = 'POST'
    if url.nil? && !entry.resource.nil?
      options = Hash.new
      options[:resource] = entry.resource.class
      options[:id] = entry.resource.id if request.local_method != 'POST'
      request.url = client.resource_url(options)
      request.url = request.url[1..-1] if request.url.starts_with?('/')
    else
      request.url = url
    end
    entry.request = request
    client.transaction_bundle.entry << entry
    entry
  end

  desc 'clear_server'
  task :clear_server, [:url] do |t, args|
    client = FHIR::Client.new(args.url)
    FHIR::RESOURCES.each do | klass |
      clear_resource(klass, client)
    end
  end

  def clear_resource(resource, client)
    reply = client.read_feed(resource)
      while !reply.nil? && !reply.resource.nil? && reply.resource.entry.length > 0
        reply.resource.entry.each do |entry|
          diagnostics = client.destroy(resource,entry.resource.id) unless entry.resource.nil?
          #check for a reference to resource error
          if diagnostics.response[:body].include?("Unable to delete")
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
  task :census, [] do |t,args|
    options = {:headers=>true,:header_converters=>:symbol}
    towns = {}
    counties = {}
    townfile = File.open("./resources/SUB-EST2015_25.csv","r:UTF-8")
    CSV.foreach(townfile,options) do |row|
      if row[:primgeo_flag].to_i == 1
        town_name = row[:name].split.keep_if{|x|!['town','city'].include?(x.downcase)}.join(' ')
        towns[ town_name ] = { :population => row[:popestimate2015].to_i, :state => row[:stname], :county => row[:county] }
      end
      if row[:sumlev].to_i==50 #county
        counties[ row[:county] ] = row[:name]
      end
    end
    # remap county identifiers to county names
    towns.each do |k,v|
      v[:county] = counties[ v[:county] ]
      v[:ages] = {}
    end
    townfile.close
    ageGroups = [ "Total", (0..4),(5..9),(10..14),(15..19),(20..24),(25..29),(30..34),(35..39),(40..44),(45..49),(50..54),(55..59),(60..64),(65..69),(70..74),(75..79),(80..84),(85..110) ]
    countyfile = File.open("./resources/CC-EST2015-ALLDATA-25.csv","r:UTF-8")
    CSV.foreach(countyfile,options) do |row|
      # if (2015 estimate) && (total overall demographics)
      if row[:year].to_i==8 && row[:agegrp].to_i==0
        gender = {
          :male => ( row[:tot_male].to_f / row[:tot_pop].to_f ),
          :female => ( row[:tot_female].to_f / row[:tot_pop].to_f ),          
        }
        race = {
          :white => (( row[:wa_male].to_f + row[:wa_female].to_f ) / row[:tot_pop].to_f),
          :hispanic => (( row[:h_male].to_f + row[:h_female].to_f ) / row[:tot_pop].to_f),
          :black => (( row[:ba_male].to_f + row[:ba_female].to_f ) / row[:tot_pop].to_f),
          :asian => (( row[:aa_male].to_f + row[:aa_female].to_f ) / row[:tot_pop].to_f),
          :native => (( row[:ia_male].to_f + row[:ia_female].to_f + row[:na_male].to_f + row[:na_female].to_f ) / row[:tot_pop].to_f),
          :other => 0.001
        }
        towns.each do |k,v|
          if v[:county]==row[:ctyname]
            v[:gender] = gender
            v[:race] = race
          end
        end
      elsif row[:year].to_i==8 # (2015 estimate)
        towns.each do |k,v|
          if v[:county]==row[:ctyname]
            v[:ages][ ageGroups[row[:agegrp].to_i] ] = row[:tot_pop].to_f
          end
        end        
      end
    end
    countyfile.close
    # convert the age groups to probability
    towns.each do |k,v|
      total = v[:ages].values.inject(0){|i,j| i + j}
      v[:ages].each{|i,j| v[:ages][i] = (j / total)}
    end  
    output = File.open("./config/towns.json","w:UTF-8")
    output.write( JSON.pretty_unparse(towns) )
    output.close
    puts "Wrote JSON to ./config/towns.json"
    counties.each do |k,county|
      output = File.open("./config/#{county.gsub(' ','_')}.json","w:UTF-8")
      output.write( JSON.pretty_unparse(towns.select{|k,t|t[:county]==county}) )
      output.close
      puts "Wrote JSON for #{county}."
    end
    puts "Done."
  end
  
end
