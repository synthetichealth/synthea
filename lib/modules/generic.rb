module Synthea
  module Modules
    class Generic < Synthea::Rules

      def initialize
        super
        @gmodules = []
        Dir.glob(File.join(File.expand_path("../../generic/modules", __FILE__), '*.json')).each do |file|
          m = JSON.parse(File.read(file))
          puts "Loaded #{m['name']} module from #{file}"
          @gmodules << m
        end
      end

      # process module defined in json
      rule :generic, [:generic], [:generic, :death] do |time, entity|
        if ! entity[:is_alive]
          return
        end
        
        entity[:generic] ||= {}
        @gmodules.each do |m|
          entity[:generic][m['name']] ||= Synthea::Generic::Context.new(m)
          entity[:generic][m['name']].run(time, entity)
        end
      end

      #-----------------------------------------------------------------------#
      
      def self.perform_wellness_encounter(entity, time)
        return if entity[:generic].nil?

        entity[:generic].each do | name, ctx |
          st = ctx.current_state
          if st.is_a?(Synthea::Generic::States::Encounter) && st.wellness && !st.processed
            st.perform_encounter(time, entity, false)
            # The encounter got unjammed.  Better keep going!
            ctx.run(time, entity)
          end
        end
      end
    end
  end
end
