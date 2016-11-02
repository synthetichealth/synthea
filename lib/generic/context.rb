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

            exited = @current_state.exited

            @current_state.start_time = exited
            @current_state.exited = nil

            if exited < time
              # This must be a delay state that expired between cycles, so temporarily rewind time
              run(exited, entity)
            end
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
        if @current_state.direct_transition
          return create_state(@current_state.direct_transition)
        elsif @current_state.distributed_transition
          return pick_distributed_transition(@current_state.distributed_transition)
        elsif @current_state.conditional_transition
          @current_state.conditional_transition.each do |ct|
            cond = ct['condition']
            if cond.nil? || Synthea::Generic::Logic.test(cond, self, time, entity)
              return create_state(ct['transition'])
            end
          end
          # No satisfied condition or fallback transition.  Go to the default terminal state.
          return States::Terminal.new(self, 'Terminal')
        elsif @current_state.complex_transition
          return pick_complex_transition(@current_state.complex_transition, time, entity)
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

      def pick_complex_transition(transitions, time, entity)
        transitions.each do |ct|
          cond = ct['condition']
          next unless cond.nil? || Synthea::Generic::Logic.test(cond, self, time, entity)

          if ct['transition']
            return create_state(ct['transition'])
          else
            return pick_distributed_transition(ct['distributions'])
          end
        end
        # No satisfied condition or fallback transition.  Go to the default terminal state.
        States::Terminal.new(self, 'Terminal')
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
