module Synthea
  module Output
    module HospitalExporter
      def self.export
        hospital_list = Synthea::Hospital.hospital_list

        if Synthea::Config.exporter.fhir.export || Synthea::Config.exporter.fhir.upload
          fhir_record = convert_to_fhir(hospital_list)

          if Synthea::Config.exporter.fhir.upload
            fhir_upload(fhir_record, Synthea::Config.exporter.fhir.upload)
          end

          if Synthea::Config.exporter.fhir.export
            out_dir = get_output_folder('fhir')
            data = fhir_record.to_json
            out_file = File.join(out_dir, 'hospital_information.json')
            File.open(out_file, 'w') { |file| file.write(data) }
          end
        end

        if Synthea::Config.exporter.text.export
          text_record = convert_to_text(hospital_list)
          out_dir = get_output_folder('text')
          out_file = File.join(out_dir, 'hospital_information.txt')
          File.open(out_file, 'w') { |file| file.write(text_record) }
        end
      end

      def self.convert_to_fhir(hospital_list)
        fhir_record = FHIR::Bundle.new
        fhir_record.type = 'collection'
        resource_id = SecureRandom.uuid

        hospital_list.each do |h|
          hospital_resource = FHIR::Organization.new(
            'id' => resource_id,
            'name' => h.attributes['name'],
            'type' => {
              'coding' => [{
                'code' => 'prov',
                'display' => 'Healthcare Provider',
                'system' => 'http://hl7.org/fhir/ValueSet/organization-type'
              }],
              'text' => 'Healthcare Provider'
            }
          )

          entry = FHIR::Bundle::Entry.new
          entry.fullUrl = "urn::uuid:#{resource_id}"
          entry.resource = hospital_resource
          fhir_record.entry << entry
        end

        fhir_record
      end

      def self.convert_to_text(hospital_list)
        text_record = []
        text_record << 'Hospital Information'
        breakline(text_record)
        hospital_list.each do |h|
          unless h.utilization[:encounters].zero?
            text_record << h.attributes['name']
            text_record << "Encounters: #{h.utilization[:encounters]}, Procedures: #{h.utilization[:procedures]}, Labs: #{h.utilization[:labs]}, Diabetes Labs: #{h.utilization[:diabetes_labs]}, Prescriptions: #{h.utilization[:prescriptions]}"
            breakline(text_record)
          end
          next
        end
        text_record.join("\n")
      end

      def self.breakline(text_record)
        text_record << '-' * 80
      end

      def self.get_output_folder(folder_name)
        base = if Synthea::Config.docker.dockerized
                 Synthea::Config.docker.location
               else
                 Synthea::Config.exporter.location
               end
        dirs = [base, folder_name]

        folder = File.join(*dirs)

        FileUtils.mkdir_p folder unless File.exist? folder

        folder
      end

      def self.fhir_upload(bundle, fhir_server_url, fhir_client = nil)
        # create a new client object for each upload
        # unless they provide us a client to use
        unless fhir_client
          fhir_client = FHIR::Client.new(fhir_server_url)
          fhir_client.default_format = FHIR::Formats::ResourceFormat::RESOURCE_JSON
        end

        fhir_client.begin_transaction
        bundle.entry.each do |entry|
          # defined our own 'add to transaction' function to preserve our entry information
          add_entry_transaction('POST', nil, entry, fhir_client)
        end
        begin
          reply = fhir_client.end_transaction
          puts "  Error: #{reply.code}" if reply.code != 200
        rescue StandardError => e
          puts "  Error: #{e.message}"
        end
      end

      def self.add_entry_transaction(_method, url, entry = nil, client)
        request = FHIR::Bundle::Entry::Request.new
        request.local_method = 'POST'
        if url.nil? && !entry.resource.nil?
          options = {}
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
    end
  end
end
