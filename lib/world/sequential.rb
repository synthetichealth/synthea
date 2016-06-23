module Synthea
  module World
    class Sequential

      attr_reader :stats
        
      def initialize()
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

        ['html','fhir','CCDA'].each do |type|
          out_dir = File.join('output',type)
          FileUtils.rm_r out_dir if File.exists? out_dir
          FileUtils.mkdir_p out_dir
        end
        Mongoid.configure { |config| config.connect_to("synthea_test") }
      end

      def run
        puts "Generating #{@population_count} patients..."
        @population_count.times do |i|
          @date = @start_date + rand(@end_date - @start_date)
          person = Synthea::Person.new
          while !person.had_event?(:death) && @date<=@end_date
            @date += @time_step.days
            Synthea::Rules.apply(@date,person)
          end
          record_stats(person)
          puts "##{i+1}  #{person[:name_last]}, #{person[:name_first]}. #{person[:race].to_s.capitalize} #{person[:ethnicity].to_s.gsub('_',' ').capitalize}. #{person[:age]} y/o #{person[:gender]}."
          export(person)
        end
        puts "Generated Demographics:"
        puts JSON.pretty_unparse(@stats)
      end

      def record_stats(patient)
        @stats[:population_count] += 1
        if patient.had_event?(:death)
          @stats[:dead] += 1
        else
          @stats[:living] += 1
        end
        @stats[:age][ (patient[:age]/10)*10 ] += 1
        @stats[:gender][ patient[:gender] ] += 1
        @stats[:race][ patient[:race] ] += 1
        @stats[:ethnicity][ patient[:ethnicity] ] += 1
        @stats[:blood_type][ patient[:blood_type] ] += 1
      end

      def export(patient)
        out_dir = File.join('output','html')
        html = HealthDataStandards::Export::HTML.new.export(patient.record)
        File.open(File.join(out_dir, "#{patient[:name_last]}_#{patient[:name_first]}_#{!patient[:diabetes].nil?}.html"), 'w') { |file| file.write(html) }
        
        out_dir = File.join('output','fhir')
        data = patient.fhir_record.to_json
        File.open(File.join(out_dir, "#{patient[:name_last]}_#{patient[:name_first]}_#{!patient[:diabetes].nil?}.json"), 'w') { |file| file.write(data) }
        
        out_dir = File.join('output','CCDA')
        xml = HealthDataStandards::Export::CCDA.new.export(patient.record)
        File.open(File.join(out_dir, "#{patient[:name_last]}_#{patient[:name_first]}_#{!patient[:diabetes].nil?}.xml"), 'w') { |file| file.write(xml) }
      end

    end
  end
end