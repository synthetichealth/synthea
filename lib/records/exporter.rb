module Synthea
  module Output
    module Exporter
      def self.export(patient)
        patient = filter_for_export(patient) unless Synthea::Config.export.years_of_history <= 0

        if Synthea::Config.export.ccda || Synthea::Config.export.html
          ccda_record = Synthea::Output::CcdaRecord.convert_to_ccda(patient)

          if Synthea::Config.export.ccda
            out_dir = File.join('output','CCDA')
            xml = HealthDataStandards::Export::CCDA.new.export(ccda_record)
            File.open(File.join(out_dir, "#{patient.record_synthea.patient_info[:uuid]}.xml"), 'w') { |file| file.write(xml) }
          end

          if Synthea::Config.export.html
            out_dir = File.join('output','html')
            html = HealthDataStandards::Export::HTML.new.export(ccda_record)
            File.open(File.join(out_dir, "#{patient.record_synthea.patient_info[:uuid]}.html"), 'w') { |file| file.write(html) }
          end
        end

        if Synthea::Config.export.fhir
          fhir_record = Synthea::Output::FhirRecord.convert_to_fhir(patient)

          out_dir = File.join('output','fhir')
          data = fhir_record.to_json
          File.open(File.join(out_dir, "#{patient.record_synthea.patient_info[:uuid]}.json"), 'w') { |file| file.write(data) }
        end
      end


      def self.filter_for_export(patient)
        # filter the patient's history to only the last __ years
        # but also include relevant history from before that

        cutoff_date = Time.now - Synthea::Config.export.years_of_history.years

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
