module Synthea
  module Modules
    class Generic < Synthea::Rules
      def initialize
        super
        # load all the main JSON module files in lib/generic/modules/. Any directories
        # in lib/generic/modules/ are expected to contain submodules that may be called by
        # any main module.
        module_dir = File.expand_path('../../generic/modules', __FILE__)

        # load all modules and submodules in lib/generic/modules/
        Dir.glob(File.join(module_dir, '**', '*.json')).each do |file|
          load_module(module_dir, file)
        end
      end

      def load_module(module_dir, file)
        # loads a module into the global MODULES hash given an absolute path
        key = module_key(module_dir, file)
        Synthea::MODULES[key] = JSON.parse(File.read(file))
        name = Synthea::MODULES[key]['name']
        type = if key.include?('/')
                 'submodule'
               else
                 'module'
               end
        puts "Loaded \"#{name}\" #{type} from #{file}"
      end

      def module_key(module_dir, file)
        # Returns a unique key to use in the MODULES hash based on the module's
        # filename and local path inside the lib/generic/modules/ directory.
        # For example:
        # "lib/generic/modules/module.json" becomes "module"
        # lib/generic/modules/submodules/submodule.json" becomes "submodules/submodule"
        file.sub(module_dir + File::SEPARATOR, '').sub('.json', '')
      end

      def main_modules
        # Returns keys to all main modules (not submodules) in the global MODULES hash
        Synthea::MODULES.keys.select { |k| !k.include?('/') }
      end

      # this rule loops through the generic modules, processing one at a time
      rule :generic, [:generic], [:generic, :death] do |time, entity|
        return unless entity.alive?(time)

        entity[:generic] ||= {}
        main_modules.each do |module_name|
          # For each entity we initialize a new set of contexts, one for each main module.
          # The entity is then run through each context until the entity dies or the
          # simulation's end time is reached.
          entity[:generic][module_name] ||= Synthea::Generic::Context.new(module_name)
          begin
            entity[:generic][module_name].run(time, entity)
          rescue
            puts "FATAL ERROR in Module: #{module_name}"
            raise
          end
        end
      end

      #-----------------------------------------------------------------------#

      def self.log_modules(entity)
        if entity && Synthea::Config.generic.log
          entity[:generic].each do |_key, context|
            context.log_history if context.logged.nil?
          end
        end
      end

      def self.perform_wellness_encounter(entity, time)
        return if entity[:generic].nil?

        # find all of the generic modules that are currently waiting for a wellness encounter
        entity[:generic].each do |_key, context|
          next unless context.active?

          st = context.current_state
          next unless st.is_a?(Synthea::Generic::States::Encounter) && st.wellness && !st.processed
          st.perform_encounter(time, entity, false)
          # The encounter got unjammed -- progress through the subsequent states
          context.run(time, entity)
        end
      end
    end
  end
end
