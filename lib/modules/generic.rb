module Synthea
  module Modules
    class Generic < Synthea::Rules
      def initialize
        super
        # load all the JSON module files in lib/generic/modules/
        @gmodules = []
        Dir.glob(File.join(File.expand_path('../../generic/modules', __FILE__), '*.json')).each do |file|
          m = JSON.parse(File.read(file))
          puts "Loaded #{m['name']} module from #{file}"
          @gmodules << m
        end
      end

      # this rule loops through the generic modules, processing one at a time
      rule :generic, [:generic], [:generic, :death] do |time, entity|
        return unless entity.alive?(time)

        entity[:generic] ||= {}
        @gmodules.each do |m|
          entity[:generic][m['name']] ||= Synthea::Generic::Context.new(m)
          entity[:generic][m['name']].run(time, entity)
        end
      end

      #-----------------------------------------------------------------------#

      def self.perform_wellness_encounter(entity, time)
        return if entity[:generic].nil?

        # find all of the generic modules that are currently waiting for a wellness encounter
        entity[:generic].each do |_name, ctx|
          st = ctx.current_state
          next unless st.is_a?(Synthea::Generic::States::Encounter) && st.wellness && !st.processed
          st.perform_encounter(time, entity, false)
          # The encounter got unjammed -- progress through the subsequent states
          ctx.run(time, entity)
        end
      end
    end
  end
end
