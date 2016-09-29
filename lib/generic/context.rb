module Synthea
  module Generic
    class Context
      attr_reader :config, :current_state, :history, :logged

      def initialize(config)
        @config = config
        @history = []
        @current_state = create_state('Initial')
      end

      def run(time, entity)
        # if @current_state.run returns true, it means we should progress to the next state
        while @current_state.run(time, entity)
          next_state = self.next(time, entity)

          if @current_state.name == next_state.name
            # looped from a state back to itself, so for perf reasons (memory usage)
            # just stay in the same state and change the dates instead of keeping another object

            if @current_state.exited < time
              # This must be a delay state that expired between cycles, so temporarily rewind time
              run(@current_state.exited, entity)
            end

            @current_state.start_time = @current_state.exited
            @current_state.exited = nil
          else
            @history << @current_state
            @current_state = next_state
            if @history.last.exited < time
              # This must be a delay state that expired between cycles, so temporarily rewind time
              run(@history.last.exited, entity)
            end
          end
        end
        if Synthea::Config.generic.log && @current_state.is_a?(Synthea::Generic::States::Terminal) && @logged.nil?
          log_history
          @logged = true
        end
      end

      def next(time, entity)
        c = state_config(@current_state.name)
        if c.key? 'direct_transition'
          return create_state(c['direct_transition'])
        elsif c.key? 'distributed_transition'
          return pick_distributed_transition(c['distributed_transition'])
        elsif c.key? 'conditional_transition'
          c['conditional_transition'].each do |ct|
            cond = ct['condition']
            if cond.nil? || Synthea::Generic::Logic.test(cond, self, time, entity)
              return create_state(ct['transition'])
            end
          end
          # No satisfied condition or fallback transition.  Go to the default terminal state.
          return States::Terminal.new(self, 'Terminal')
        elsif c.key? 'complex_transition'
          c['complex_transition'].each do |ct|
            cond = ct['condition']
            if cond.nil? || Synthea::Generic::Logic.test(cond, self, time, entity)
              return pick_distributed_transition(ct['distributions'])
            end
          end
          # No satisfied condition or fallback transition.  Go to the default terminal state.
          return States::Terminal.new(self, 'Terminal')
        else
          # No transition was specified.  Go to the default terminal state.
          return States::Terminal.new(self, 'Terminal')
        end
      end

      def pick_distributed_transition(transitions)
        # distributed_transition is an array of distributions that should total 1.0.
        # So... pick a random float from 0.0 to 1.0 and walk up the scale.
        choice = rand
        high = 0.0
        transitions.each do |dt|
          high += dt['distribution']
          return create_state(dt['transition']) if choice < high
        end
        # We only get here if the numbers didn't add to 1.0 or if one of the numbers caused
        # floating point imprecision (very, very rare).  Just go with the last one.
        create_state(transitions.last['transition'])
      end

      def most_recent_by_name(name)
        @history.reverse.find { |h| h.name == name }
      end

      def state_config(name)
        @config['states'][name]
      end

      def create_state(name)
        clazz = state_config(name)['type']
        Object.const_get("Synthea::Generic::States::#{clazz}").new(self, name)
      end

      def log_history
        puts '/==============================================================================='
        puts "| #{@config['name']} Log"
        puts '|==============================================================================='
        puts '| Entered                   | Exited                    | State'
        puts '|---------------------------|---------------------------|-----------------------'
        @history.each do |h|
          log_state(h)
        end
        log_state(@current_state)
        puts '\\==============================================================================='
      end

      def log_state(state)
        exit_str = state.exited ? state.exited.strftime('%FT%T%:z') : '                         '
        puts "| #{state.entered.strftime('%FT%T%:z')} | #{exit_str} | #{state.name}"
      end

      def inspect
        "#<Synthea::Generic::Context::#{object_id}> #{@current_state.name}"
      end
    end
  end
end
