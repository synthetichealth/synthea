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

    binding.pry

    puts "Saving patient records..."
    export(world.people | world.dead)
    fhir_export(world.people | world.dead)

    binding.pry

    puts "Uploading patient records..."
    uploadFhirServer(world.people | world.dead)
  end

  def export(patients)
    # we need to configure mongo to export for some reason... not ideal
    Mongoid.configure { |config| config.connect_to("synthea_test") }

    out_dir = File.join('output','html')
    FileUtils.rm_r out_dir if File.exists? out_dir
    FileUtils.mkdir_p out_dir
    patients.each do |patient|
      html = HealthDataStandards::Export::HTML.new.export(patient.record)
      File.open(File.join(out_dir, "#{patient[:name_last]}_#{patient[:name_first]}_#{!patient[:diabetes].nil?}.html"), 'w') { |file| file.write(html) }
    end
  end

  def fhir_export(patients)
    out_dir = File.join('output','fhir')
    FileUtils.rm_r out_dir if File.exists? out_dir
    FileUtils.mkdir_p out_dir
    patients.each do |patient|
      data = patient.fhir_record.to_json
      File.open(File.join(out_dir, "#{patient[:name_last]}_#{patient[:name_first]}_#{!patient[:diabetes].nil?}.json"), 'w') { |file| file.write(data) }
    end
  end

  def uploadFhirServer(patients)
    client = FHIR::Client.new('http://bonfire.mitre.org:8100/fhir/baseDstu3')
    patients.each do |patient|
      client.begin_transaction
      patient.fhir_record.entry.each do |entry|
        #defined our own 'add to transaction' function to preserve our entry information
        add_entry_transaction('POST',nil,entry,client)
      end
      reply = client.end_transaction

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
  task :clear_server, [] do |t, args|
    client = FHIR::Client.new("http://bonfire.mitre.org:8100/fhir/baseDstu3")
    binding.pry
    FHIR::RESOURCES.map do | klass |
      reply = client.read_feed(klass)
      while !reply.nil? && !reply.resource.nil? && len(reply.resource.entry) > 0
        reply.resource.entry.each do |entry|
          client.destroy(klass,entry.resource.id) unless entry.resource.nil?
        
        end
        reply = client.read_feed(klass)
      end
    end
  end

  
end
