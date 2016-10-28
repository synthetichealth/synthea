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

          if @current_state.name == next_state
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
            @current_state = create_state(next_state)
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
        transition = @current_state.transition
        return nil unless transition
        # no defined transition

        transition.follow(self, entity, time)
      end

      def most_recent_by_name(name)
        @history.reverse.find { |h| h.name == name }
      end

      def state_config(name)
        @config['states'][name]
      end

      def create_state(name)
        return States::Terminal.new(self, 'Terminal') if name.nil?

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
