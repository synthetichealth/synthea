module Synthea
  module Generic
    class Context
      attr_accessor :config, :current_state, :history

      def initialize (config)
        @config = config
        @history = []
        @current_state = self.create_state("Initial")
      end

      def run(time, entity)
        while @current_state.run(time, entity) do
          @history << @current_state
          @current_state = self.next(time)
        end
      end

      def next(time)
        c = state_config(@current_state.name)
        if c.has_key? 'direct_transition'
          return self.create_state(c['direct_transition'])
        elsif c.has_key? 'distributed_transition'
          # distributed_transition is an array of distributions that should total 1.0.
          # So... pick a random float from 0.0 to 1.0 and walk up the scale.
          choice = rand()
          high = 0.0
          c['distributed_transition'].each do |dt|
            high += dt['distribution']
            if choice < high
              return self.create_state(dt['transition'])
            end
          end
          # We only get here if the numbers didn't add to 1.0 or if one of the numbers caused
          # floating point imprecision (very, very rare).  Just go with the last one.
          return self.create_state(c['distributed_transition'].last['transition'])
        else
          # No transition.  Go to the default terminal state
          return States::Terminal.new(self, "Terminal")
        end
      end

      def most_recent_by_name(name)
        @history.reverse.find { |h| h.name == name }
      end

      def state_config(name)
        return @config['states'][name]
      end

      def create_state(name)
        clazz = state_config(name)['type']
        Object::const_get("Synthea::Generic::States::#{clazz}").new(self, name)
      end
    end
  end
end