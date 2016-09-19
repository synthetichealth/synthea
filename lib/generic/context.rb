module Synthea
  module Generic
    class Context
      attr_reader :config, :current_state, :history, :logged

      def initialize (config)
        @config = config
        @history = []
        @current_state = self.create_state("Initial")
      end

      def run(time, entity)
        # if @current_state.run returns true, it means we should progress to the next state
        while @current_state.run(time, entity) do
          @history << @current_state
          @current_state = self.next(time, entity)
          if @history.last.exited < time
            # This must be a delay state that expired between cycles, so temporarily rewind time
            self.run(@history.last.exited, entity)
          end
        end
        if Synthea::Config.generic.log && @current_state.is_a?(Synthea::Generic::States::Terminal) && @logged.nil?
          self.log_history()
          @logged = true
        end
      end

      def next(time, entity)
        c = state_config(@current_state.name)
        if c.has_key? 'direct_transition'
          return self.create_state(c['direct_transition'])
        elsif c.has_key? 'distributed_transition'
          return pick_distributed_transition(c['distributed_transition'])
        elsif c.has_key? 'conditional_transition'
          c['conditional_transition'].each do |ct|
            cond = ct['condition']
            if cond.nil? || Synthea::Generic::Logic::test(cond, self, time, entity)
              return self.create_state(ct['transition'])
            end
          end
          # No satisfied condition or fallback transition.  Go to the default terminal state.
          return States::Terminal.new(self, "Terminal")
        elsif c.has_key? 'complex_transition'
          c['complex_transition'].each do |ct|
            cond = ct['condition']
            if cond.nil? || Synthea::Generic::Logic::test(cond, self, time, entity)
              return pick_distributed_transition(ct['distributions'])
            end
          end
          # No satisfied condition or fallback transition.  Go to the default terminal state.
          return States::Terminal.new(self, "Terminal")
        else
          # No transition was specified.  Go to the default terminal state.
          return States::Terminal.new(self, "Terminal")
        end
      end

      def pick_distributed_transition(transitions)
        # distributed_transition is an array of distributions that should total 1.0.
        # So... pick a random float from 0.0 to 1.0 and walk up the scale.
        choice = rand()
        high = 0.0
        transitions.each do |dt|
          high += dt['distribution']
          if choice < high
            return self.create_state(dt['transition'])
          end
        end
        # We only get here if the numbers didn't add to 1.0 or if one of the numbers caused
        # floating point imprecision (very, very rare).  Just go with the last one.
        return self.create_state(transitions.last['transition'])
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

      def log_history()
        puts "/==============================================================================="
        puts "| #{@config['name']} Log"
        puts "|==============================================================================="
        puts "| Entered                   | Exited                    | State"
        puts "|---------------------------|---------------------------|-----------------------"
        @history.each do |h|
          self.log_state(h)
        end
        self.log_state(@current_state)
        puts "\\==============================================================================="
      end

      def log_state(state)
        exit_str = state.exited ? state.exited.strftime('%FT%T%:z') : "                         "
        puts "| #{state.entered.strftime('%FT%T%:z')} | #{exit_str} | #{state.name}"
      end

      def inspect
        "#<Synthea::Generic::Context::#{object_id}> #{@current_state.name}"
      end
    end
  end
end
