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
    fhir_export(world.people | world.dead)
    ccda_export(world.people | world.dead)
    binding.pry
    puts 'Finished.'
  end

  desc 'upload to FHIR server'
  task :fhirupload, [:url] do |t,args|
    output = File.join('output','fhir')
    if File.exists? output
      start = Time.now
      files = File.join(output, '**', '*.json')
      client = FHIR::Client.new(args.url)
      puts 'Uploading Patient records...'
      Dir.glob(files).each do | file |
        json = File.open(file,'r:UTF-8',&:read)
        bundle = FHIR.from_contents(json)
        client.begin_transaction
        bundle.entry.each do |entry|
          #defined our own 'add to transaction' function to preserve our entry information
          add_entry_transaction('POST',nil,entry,client)
        end
        reply = client.end_transaction
      end
      finish = Time.now
      minutes = ((finish-start)/60)
      seconds = (minutes - minutes.floor) * 60
      puts "Completed in #{minutes.floor} minute(s) #{seconds.floor} second(s)."
    else
      puts 'No FHIR patient records have been generated yet.'
      puts 'Run synthea:generate task.'
    end
  end

  def ccda_export(patients)
    # we need to configure mongo to export for some reason... not ideal
    Mongoid.configure { |config| config.connect_to("synthea_test") }

    html_out_dir = File.join('output','html')
    FileUtils.rm_r html_out_dir if File.exists? html_out_dir
    FileUtils.mkdir_p html_out_dir
    ccda_out_dir = File.join('output','CCDA')
    FileUtils.rm_r ccda_out_dir if File.exists? ccda_out_dir
    FileUtils.mkdir_p ccda_out_dir
    patients.each do |patient|
      ccda_record = Synthea::Output::CcdaRecord.convert_to_ccda(patient)
      html = HealthDataStandards::Export::HTML.new.export(ccda_record)
      xml = HealthDataStandards::Export::CCDA.new.export(ccda_record)
      File.open(File.join(html_out_dir, "#{patient[:name_last]}_#{patient[:name_first]}_#{!patient[:diabetes].nil?}.html"), 'w') { |file| file.write(html) }
      File.open(File.join(ccda_out_dir, "#{patient[:name_last]}_#{patient[:name_first]}_#{!patient[:diabetes].nil?}.xml"), 'w') { |file| file.write(xml) }
    end
  end

  def fhir_export(patients)
    out_dir = File.join('output','fhir')
    FileUtils.rm_r out_dir if File.exists? out_dir
    FileUtils.mkdir_p out_dir
    patients.each do |patient|
      fhir_record = Synthea::Output::FhirRecord.convert_to_fhir(patient)
      data = fhir_record.to_json
      File.open(File.join(out_dir, "#{patient[:name_last]}_#{patient[:name_first]}_#{!patient[:diabetes].nil?}.json"), 'w') { |file| file.write(data) }
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
  
end
