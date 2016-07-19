module Synthea
  module Generic
    class Context
      attr_accessor :config, :current_state, :history

      def initialize (config, time)
        @config = config
        @history = []
        @current_state = self.create_state("Initial", time)
      end

      def process(time, entity)
        while ! @current_state.nil? && @current_state.process(time, entity) do
          @history << @current_state
          @current_state = self.next(time)
        end
      end

      def next(time)
        if @current_state.is_a? States::Terminal
          return nil
        end

        c = @config['states'][@current_state.name]
        if c.has_key? 'direct_transition'
          return self.create_state(c['direct_transition'], time)
        elsif c.has_key? 'distributed_transition'
          # distributed_transition is an array of distributions that should total 1.0.
          # So... pick a random float from 0.0 to 1.0 and walk up the scale.
          choice = rand()
          high = 0.0
          c['distributed_transition'].each do |dt|
            high += dt['distribution']
            if choice < high
              return self.create_state(dt['transition'], time)
            end
          end
          # We only get here if the numbers didn't add to 1.0 or if one of the numbers caused
          # floating point imprecision (very, very rare).  Just go with the last one.
          return self.create_state(c['distributed_transition'].last['transition'], time)
        else
          # No transition.  Go to the default terminal state
          return States::Terminal.new(self, "Terminal", time)
        end
      end

      def most_recent_by_name(name)
        @history.reverse.find { |h| h.name == name }
      end

      def create_state(name, time)
        clazz = @config['states'][name]['type']
        Object::const_get("Synthea::Generic::States::#{clazz}").new(self, name, time)
      end
    end
  end
end