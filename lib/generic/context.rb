module Synthea
  module Generic
    class Context
      attr_reader :config, :name, :current_module, :logged
      attr_accessor :history, :current_state, :current_encounter

      def initialize(module_name)
        @config = Synthea::MODULES[module_name] # The JSON representation of a GMF module, as a hash
        @name = @config['name']
        @current_module = module_name
        @current_state = create_state('Initial')
        @current_encounter = nil
        @history = []
        @stack = []
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
            @history << @current_state unless @current_state.is_a?(Synthea::Generic::States::CallSubmodule)
            @current_state = create_state(next_state)
            if @history.last.exited < time
              # This must be a delay state that expired between cycles, so temporarily rewind time
              run(@history.last.exited, entity)
            end
          end
        end

        if Synthea::Config.generic.log && !active? && @logged.nil?
          log_history
          @logged = true
        end
      end

      def next(time, entity)
        # Returns the name of the next state to load after the current state
        transition = @current_state.transition
        return nil unless transition # no defined transition
        transition.follow(self, entity, time)
      end

      def active?
        # A context is no longer active if the Terminal state of the main module has been reached
        if @current_state.is_a?(Synthea::Generic::States::Terminal) && !active_submodule?
          false
        else
          true
        end
      end

      def active_submodule?
        # Returns true if a submodule is currently being processed
        !@stack.empty?
      end

      def call_submodule(time, entity, submodule_name)
        # Exit the callsubmodule state and push it onto the history, but save it as the
        # current_state on the stack so its transition may be used when the submodule
        # returns.
        @current_state.exited = time
        @history << @current_state

        # Save the current state of the calling module.
        @stack.push(
          'module_name' => @current_module,
          'current_state' => @current_state # should always be a CallSubmodule state
        )

        # Load the new submodule into the context.
        @current_module = submodule_name
        @config = Synthea::MODULES[submodule_name]
        raise "Cannot find submodule #{submodule_name}" if @config.nil?

        # Resume processing states for the current time and entity starting
        # with the submodule's Initial state.
        @current_state = create_state('Initial')
        run(time, entity)
      end

      def return_from_submodule(time, entity)
        # Exit the submodule's Terminal state and push it onto the history.
        @current_state.exited = time
        @history << @current_state

        # Restore the calling module from its saved state.
        saved_state = @stack.pop
        @current_module = saved_state['module_name']
        @config = Synthea::MODULES[saved_state['module_name']]
        @current_state = saved_state['current_state']

        # Resume execution from that current state.
        run(time, entity)
      end

      def state_config(state_name)
        @config['states'][state_name]
      end

      def create_state(state_name)
        # creates a new state using that state's hash found in the context's current @config
        return States::Terminal.new(self, 'Terminal') if state_name.nil?
        clazz = state_config(state_name)['type']

        Object.const_get("Synthea::Generic::States::#{clazz}").new(self, state_name)
      end

      def most_recent_by_name(name)
        @history.reverse.find { |h| h.name == name }
      end

      def all_states
        @config['states'].keys
      end

      def validate
        messages = []

        reachable = ['Initial']

        all_states.each do |state_name|
          state = create_state(state_name)
          messages.push(*state.validate(self, []))

          reachable.push(*state.transition.all_transitions) if state.transition && state.transition.all_transitions != []
        end

        unreachable = all_states - reachable
        unreachable.each { |st| messages << "State '#{st}' is unreachable" }

        messages.uniq
      end

      def inspect
        "#<Synthea::Generic::Context::#{object_id}> #{@current_state.name}"
      end

      def log_history
        puts '/==============================================================================='
        puts "| #{@name} Log"
        puts '|==============================================================================='
        puts '| Entered                   | Exited                    | State'
        puts '|---------------------------|---------------------------|-----------------------'
        @history.each do |state|
          log_state(state)
        end
        log_state(@current_state)
        puts '\\==============================================================================='
        @logged = true
      end

      def log_state(state)
        exit_str = state.exited ? state.exited.strftime('%FT%T%:z') : '                         '
        puts "| #{state.entered.strftime('%FT%T%:z')} | #{exit_str} | #{state.name}"
      end
    end
  end
end
