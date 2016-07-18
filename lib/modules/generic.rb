module Synthea
  module Modules
    class Generic < Synthea::Rules

      def initialize
        super
        @gmodules = []
        Dir.glob(File.join(File.expand_path("../generic_modules", __FILE__), '*.json')).each do |file|
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
          entity[:generic][m['name']] ||= Synthea::States::Context.new(m, time)
          entity[:generic][m['name']].process(time, entity)
        end
      end

      def self.record_generic(entity, time)
        
      end
    end
  end
  
  module States
    class Context
      attr_accessor :config, :current_state, :history

      def initialize (config, time)
        @config = config
        @history = []
        @current_state = self.createState("Initial", time)
      end

      def process(time, entity)
        while ! @current_state.nil? && @current_state.process(time, entity) do
          @history << @current_state
          @current_state = self.next(time)
        end
      end

      def next(time)
        if @current_state.is_a? Terminal
          return nil
        end

        c = @config['states'][@current_state.name]
        if c.has_key? 'direct_transition'
          return self.createState(c['direct_transition'], time)
        elsif c.has_key? 'distributed_transition'
          # distributed_transition is an array of distributions that should total 1.0.
          # So... pick a random float from 0.0 to 1.0 and walk up the scale.
          choice = rand()
          high = 0.0
          c['distributed_transition'].each do |dt|
            high += dt['distribution']
            if choice < high
              return self.createState(dt['transition'], time)
            end
          end
          # We only get here if the numbers didn't add to 1.0 or if one of the numbers caused
          # floating point imprecision (very, very rare).  Just go with the last one.
          return self.createState(c['distributed_transition'].last['transition'], time)
        else
          # No transition.  Go to the default terminal state
          return Terminal.new(self, "Terminal", time)
        end
      end

      def createState(name, time)
        c = @config['states'][name]
        case c['type']
        when "Initial"
          Initial.new(self, name, time)
        when "Terminal"
          Terminal.new(self, name, time)
        when "Delay"
          Delay.new(self, name, time)
        when "Guard"
          Guard.new(self, name, time)
        when "Diagnosis"
          Diagnosis.new(self, name, time)
        when "MedicationOrder"
          MedicationOrder.new(self, name, time)
        when "Procedure"
          Procedure.new(self, name, time)
        when "Death"
          Death.new(self, name, time)
        else
          raise "Unsupported state type: #{c['type']}"
        end
      end
    end

    class State
      attr_accessor :name, :start

      def initialize (context, name, start)
        @context = context
        @name = name
        @start = start
      end
    end

    class Initial < Synthea::States::State
      def process(time, entity)
        # initial state always goes to next
        true
      end
    end

    class Terminal < Synthea::States::State
      def process(time, entity)
        true
      end
    end

    class Delay < Synthea::States::State
      def initialize (context, name, start)
        super
        range = context.config['states'][name]['range']
        if ! range.nil?
          choice = rand(range['low'] .. range['high'])
          @end = choice.method(range['unit']).call().since(start)
        else
          @end = start
        end
      end

      def process(time, entity)
        return time >= @end
      end
    end

    class Guard < Synthea::States::State
      def process(time, entity)
        c = @context.config['states'][@name]['if']
        return test(c, time, entity)
      end

      def test(condition, time, entity)
        case condition['conditionType']
        when 'And'
          condition['conditions'].each do |c|
            if ! test(c, time, entity)
              return false
            end
          end
          return true
        when 'Or'
          condition['conditions'].each do |c|
            if test(c, time, entity)
              return true
            end
          end
          return false
        when 'Gender'
          return condition['gender'] == entity[:gender]
        when 'Age'
          birthdate = entity.event(:birth).time
          age = Synthea::Modules::Lifecycle.age(time, birthdate, nil, condition['unit'].to_sym)
          target = condition['quantity']
          case condition['operator']
          when '<'
            return age < target
          when '<='
            return age <= target
          when '=='
            return age == target
          when '>='
            return age >= target
          when '>'
            return age > target
          end
          return false
        else
          return false
        end
      end
    end

    class Diagnosis < Synthea::States::State
      def initialize (context, name, start)
        super
        @code = context.config['states'][name]['code']
      end

      def process(time, entity)
        puts "⬇ Diagnosed #{@name} at age #{entity[:age]} on #{@start}"
        return true
      end
    end

    class MedicationOrder < Synthea::States::State
      def initialize (context, name, start)
        super
        @code = context.config['states'][name]['code']
        if ! context.config['states'][name]['reason'].nil?
          @reason = context.history.find {|h| h.name == context.config['states'][name]['reason'] }
        end
      end

      def process(time, entity)
        puts "⬇ Prescribed #{@name} at age #{entity[:age]} on #{@start}"
        return true
      end
    end

    class Procedure < Synthea::States::State
      def initialize (context, name, start)
        super
        @code = context.config['states'][name]['code']
        if ! context.config['states'][name]['reason'].nil?
          @reason = context.history.find {|h| h.name == context.config['states'][name]['reason'] }
        end
      end

      def process(time, entity)
        puts "⬇ Performed #{@name} at age #{entity[:age]} on #{@start}"
        return true
      end
    end

    class Death < Synthea::States::State
      def process(time, entity)
        entity[:is_alive] = false
        entity.events.create(time, :death, :generic, true)
        Synthea::Modules::Lifecycle.record_death(entity, time)
        puts "⬇ Died at age #{entity[:age]} on #{@start}"
        true
      end
    end
  end
end
