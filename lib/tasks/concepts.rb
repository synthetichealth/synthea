module Synthea
  module Tasks
    class Concepts

      def self.inventory
        puts "Performing an inventory of concepts into `concepts.csv`..."
        count = 0

        concepts = {}

        inventory_obs(concepts)
        inventory_lookup(concepts, Synthea::COND_LOOKUP)
        inventory_lookup(concepts, Synthea::CAREPLAN_LOOKUP)
        inventory_lookup(concepts, Synthea::REASON_LOOKUP)
        inventory_lookup(concepts, Synthea::MEDICATION_LOOKUP)
        inventory_lookup(concepts, Synthea::INSTRUCTION_LOOKUP)
        inventory_lookup(concepts, Synthea::PROCEDURE_LOOKUP)
        inventory_lookup(concepts, Synthea::ENCOUNTER_LOOKUP)
        inventory_generic_modules(concepts)

        concept_file = File.open('concepts.csv','w:UTF-8')
        concepts.each do |system,codes|
          codes.each do |code,display|
            count += 1
            concept_file.write("#{system},#{code},#{display.gsub(',',' ')}\n")
          end
        end
        concept_file.close

        puts "Cataloged #{count} concepts."
        puts 'Done.'
      end

      def self.inventory_obs(concepts)
        concepts['LOINC'] = Hash.new unless concepts['LOINC']
        Synthea::OBS_LOOKUP.each do |key,value|
          concepts['LOINC'][value[:code]] = value[:description]
        end

        concepts['CVX'] = Hash.new unless concepts['CVX']
        Synthea::IMM_SCHEDULE.each do |key,value|
          concepts['CVX'][value[:code]['code']] = value[:code]['display']
        end
      end

      def self.inventory_lookup(concepts,lookup)
        lookup.each do |key,value|
          value[:codes].each do |system,codes|
            concepts[system] = Hash.new unless concepts[system]
            codes.each do |code|
              concepts[system][code] = value[:description]
            end
          end
        end
      end

      def self.inventory_generic_modules(concepts)
        module_dir = File.expand_path('../../generic/modules', __FILE__)

        # all modules and submodules
        Dir.glob(File.join(module_dir, '**', '*.json')) do |module_file|
          inventory_module(concepts, module_file)
        end
      end

      def self.inventory_module(concepts, module_file)
        wf = JSON.parse(File.read(module_file))
        wf['states'].each do |name, state|
          inventory_state(concepts, state)
        end
      end

      def self.inventory_state(concepts, state)
        if state.has_key? 'codes'
          state['codes'].each do |code|
            concepts[code['system']] = Hash.new unless concepts[code['system']]
            concepts[code['system']][code['code']] = code['display']
          end
        end

        if state.has_key? 'activities'
          state['activities'].each do |code|
            concepts[code['system']] = Hash.new unless concepts[code['system']]
            concepts[code['system']][code['code']] = code['display']
          end
        end
      end

    end
  end
end
