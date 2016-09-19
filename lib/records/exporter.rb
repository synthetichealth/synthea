module Synthea
  module Output
    module Exporter
      def self.export(patient)
        patient = filter_for_export(patient) unless Synthea::Config.exporter.years_of_history <= 0

        if Synthea::Config.exporter.ccda.export || Synthea::Config.exporter.ccda.upload || Synthea::Config.exporter.html.export
          ccda_record = Synthea::Output::CcdaRecord.convert_to_ccda(patient)

          if Synthea::Config.exporter.ccda.export
            out_dir = get_output_folder('CCDA', patient)
            xml = HealthDataStandards::Export::CCDA.new.export(ccda_record)
            out_file = File.join(out_dir, "#{patient.record_synthea.patient_info[:uuid]}.xml")
            File.open(out_file, 'w') { |file| file.write(xml) }
          end

          if Synthea::Config.exporter.html.export
            out_dir = get_output_folder('html', patient)
            html = HealthDataStandards::Export::HTML.new.export(ccda_record)
            out_file = File.join(out_dir, "#{patient.record_synthea.patient_info[:uuid]}.html")
            File.open(out_file, 'w') { |file| file.write(html) }
          end
        end

        if Synthea::Config.exporter.fhir.export || Synthea::Config.exporter.fhir.upload
          fhir_record = Synthea::Output::FhirRecord.convert_to_fhir(patient)

          if Synthea::Config.exporter.fhir.upload
            fhir_upload(fhir_record, Synthea::Config.exporter.fhir.upload)
          end

          if Synthea::Config.exporter.fhir.export
            out_dir = get_output_folder('fhir', patient)
            data = fhir_record.to_json
            out_file = File.join(out_dir, "#{patient.record_synthea.patient_info[:uuid]}.json")
            File.open(out_file, 'w') { |file| file.write(data) }
          end
        end
      end

      def self.get_output_folder(folder_name, patient=nil)
        base = Synthea::Config.exporter.location

        dirs = [base, folder_name]

        if patient
          dirs << patient[:city] if Synthea::Config.exporter.folder_per_city

          # take the first 2 characters of the patient uuid for subfolders
          # uuid = hex so this gives us 256 subfolders
          dirs << patient.record_synthea.patient_info[:uuid][0,2] if Synthea::Config.exporter.subfolder_by_id
        end

        folder = File.join(*dirs)

        FileUtils.mkdir_p folder unless File.exists? folder

        folder
      end

      def self.fhir_upload(bundle, fhir_server_url)
        # create a new client object for each upload
        # it's probably slower than keeping 1 or a fixed # around
        # but it means we don't have to worry about thread-safety on this part
        fhir_client = FHIR::Client.new(fhir_server_url)
        fhir_client.default_format = FHIR::Formats::ResourceFormat::RESOURCE_JSON

        fhir_client.begin_transaction
        start_time = Time.now
        bundle.entry.each do |entry|
          #defined our own 'add to transaction' function to preserve our entry information
          add_entry_transaction('POST',nil,entry,fhir_client)
        end
        begin
          reply = fhir_client.end_transaction
          puts "  Error: #{reply.code}" if reply.code!=200
        rescue Exception => e
          puts "  Error: #{e.message}"
        end
      end

      def self.add_entry_transaction(method, url, entry=nil, client)
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


      def self.filter_for_export(patient)
        # filter the patient's history to only the last __ years
        # but also include relevant history from before that

        cutoff_date = Time.now - Synthea::Config.exporter.years_of_history.years

        # dup the patient so that we export only the last _ years but the rest still exists, just in case
        patient = patient.dup
        patient.record_synthea = patient.record_synthea.dup

        present = patient.record_synthea.present

        [:encounters, :conditions, :observations, :procedures, :immunizations, :careplans, :medications].each do |attribute|
          entries = patient.record_synthea.send(attribute).dup

          entries.keep_if { |e| should_keep_entry(e, attribute, patient.record_synthea, cutoff_date) }
          patient.record_synthea.send("#{attribute.to_s}=", entries)
        end

        patient
      end

      def self.should_keep_entry(e, attribute, record, cutoff_date)
        return true if e['time'] > cutoff_date # trivial case, when we're within the last __ years

        # if the entry has a stop time, check if the effective date range overlapped the last __ years
        return true if e['stop'] && e['stop'] > cutoff_date

        # - encounters, observations, immunizations are single dates and have no "reason"
        #    so they can only be filtered by the single date
        # - procedures are always listed in "record.present" so they are only filtered by date.
        #    procedures that have "permanent side effects" such as appendectomy, amputation,
        #    should also add a condition code such as "history of ___" (ex 429280009)
        case attribute
        when :medications
          return record.medication_active?(e['type'])
        when :careplans
          return record.careplan_active?(e['type'])
        when :conditions
          return record.present[ e['type'] ] || (e['end_time'] && e['end_time'] > cutoff_date)
        end

        false
      end
    end
  end
end
