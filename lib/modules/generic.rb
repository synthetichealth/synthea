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

      #-----------------------------------------------------------------------#
      
      def self.perform_wellness_encounter(entity, time)
        return if entity[:generic].nil?

        entity[:generic].each do | name, ctx |
          st = ctx.current_state
          if st.is_a?(Synthea::States::Encounter) && st.wellness && !st.processed
            st.perform_encounter(time, entity)
            # The encounter got unjammed.  Better keep going!
            ctx.process(time, entity)
          end
        end
      end

      def self.add_lookup_code(symbol, state_codes, lookup_hash)
        return if state_codes.empty?

        lookup_hash[symbol] = {
          description: state_codes.first['display'],
          codes: {}
        }
        state_codes.each do |c|
          lookup_hash[symbol][:codes][c['system']] ||= []
          lookup_hash[symbol][:codes][c['system']] << c['code']
        end
      end
    end
  end
  
  module States
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
        if @current_state.is_a? Terminal
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
          return Terminal.new(self, "Terminal", time)
        end
      end

      def most_recent_by_name(name)
        @history.reverse.find { |h| h.name == name }
      end

      def create_state(name, time)
        c = @config['states'][name]
        case c['type']
        when "Initial"
          Initial.new(self, name, time)
        when "Terminal"
          Terminal.new(self, name, time)
        when "Delay"
          Delay.new(self, name, time)
        when "Encounter"
          Encounter.new(self, name, time)
        when "Guard"
          Guard.new(self, name, time)
        when "ConditionOnset"
          ConditionOnset.new(self, name, time)
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

      def symbol ()
        if ! @codes.nil? && ! @codes.empty?
          @codes.first['display'].gsub(/\s+/,"_").downcase.to_sym
        else
          @name.gsub(/\s+/,"_").downcase.to_sym
        end
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
        # TODO: Support delays that go between run cycles (e.g., 3-day delay when the
        # cycle runs every 7 days).  Currently, the delay would expire 4 days late.
        return time >= @end
      end
    end

    class Guard < Synthea::States::State
      def process(time, entity)
        c = @context.config['states'][@name]['if']
        return test(c, time, entity)
      end

      def test(condition, time, entity)
        case condition['condition_type']
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

    class Encounter < Synthea::States::State
      attr_reader :wellness, :processed, :time, :codes

      def initialize (context, name, start)
        super
        @processed = false
        if context.config['states'][name]['wellness']
          @wellness = true
        else
          @wellness = false
          @codes = context.config['states'][name]['codes']
        end
      end

      def process(time, entity)
        if !@wellness
          # No need to wait for a wellness encounter.  Do it now!
          self.perform_encounter(time, entity)
        end
        return @processed
      end

      def perform_encounter(time, entity)
        puts "⬇ Encounter #{name} at age #{entity[:age]} on #{time}"
        @processed = true
        @time = time
        self.record_encounter_activities(time, entity)
      end

      def record_encounter_activities(time, entity)
        # Look through the history for things to record
        @context.history.each do |h|
          # Diagnose conditions
          if h.is_a?(ConditionOnset) && ! h.diagnosed && h.target_encounter == @name
            h.diagnose(time, entity)
          # Prescribe medications
          elsif h.is_a?(MedicationOrder) && ! h.prescribed && target_encounter == @name
            h.prescribe(time, entity)
          end
        end
        @processed = true
      end
    end

    class ConditionOnset < Synthea::States::State
      attr_reader :diagnosed, :target_encounter

      def initialize (context, name, start)
        super
        @codes = context.config['states'][name]['codes']
        @target_encounter = context.config['states'][name]['target_encounter']
        @diagnosed = false
      end

      def process(time, entity)
        puts "⬇ Condition Onset #{@name} at age #{entity[:age]} on #{@start}"
        # If targeted for an encounter before it, and the encounter time is the same
        # as the onset time, then record it (it happened during the encounter)
        if ! @target_encounter.nil?
          past = @context.most_recent_by_name(@target_encounter)
          if ! past.nil? && past.time == time
            self.diagnose(time, entity)
          end
        end
        return true
      end

      def diagnose(time, entity)
        puts "⬇ Diagnosed #{@name} at age #{entity[:age]} on #{time}"
        Synthea::Modules::Generic::add_lookup_code(self.symbol(), @codes, Synthea::COND_LOOKUP)
        entity.record_synthea.condition(self.symbol(), time)
        @diagnosed = true
      end
    end

    class MedicationOrder < Synthea::States::State
      attr_reader :prescribed, :target_encounter

      def initialize (context, name, start)
        super
        @codes = context.config['states'][name]['codes']
        @target_encounter = context.config['states'][name]['target_encounter']
        @reason = context.config['states'][name]['reason']
        @prescribed = false
      end

      def process(time, entity)
        # If targeted for an encounter before it, and the encounter time is the same
        # as the start time, then record it (it happened during the encounter)
        if ! @target_encounter.nil?
          past = @context.most_recent_by_name(@target_encounter)
          if ! past.nil? && past.time == time
            self.prescribe(time, entity)
          end
        end
        return true
      end

      def prescribe(time, entity)
        puts "⬇ Prescribed #{@name} at age #{entity[:age]} on #{time}"
        Synthea::Modules::Generic::add_lookup_code(self.symbol(), @codes, Synthea::MEDICATION_LOOKUP)
        if ! @reason.nil?
          cond = @context.most_recent_by_name(@reason)
          entity.record_synthea.medication_start(self.symbol(), time, cond.symbol())
        else
          entity.record_synthea.medication_start(self.symbol(), time)
        end
        @prescribed = true
      end
    end

    class Procedure < Synthea::States::State
      def initialize (context, name, start)
        super
        @codes = context.config['states'][name]['codes']
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
