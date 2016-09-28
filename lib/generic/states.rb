module Synthea
  module Generic
    module States
      # define a base state with common functionality that can be inherited by all other states
      class State
        attr_accessor :name, :entered, :exited, :start_time

        def initialize(context, name)
          @context = context
          @name = name
        end

        def run(time, entity)
          @entered ||= time
          @start_time ||= time
          exit = process(time, entity)
          if exit
            # Special handling for Delay, which may expire between run cycles
            if is_a? Delay
              @exited = @expiration
              @expiration = nil
              @start_time = @exited if @start_time > @exited
            else
              @exited = time
            end
          end

          c = @context.state_config(@name)
          if c && c['assign_to_attribute']
            entity[c['assign_to_attribute']] = symbol.to_s
          end

          exit
        end

        # creates a symbol to be used in lookup tables (mainly for referencing codes)
        def symbol
          # prefer a code display value since the state name may not be unique between modules
          if !@codes.nil? && !@codes.empty?
            @codes.first['display'].gsub(/\s+/, '_').downcase.to_sym
          else
            @name.gsub(/\s+/, '_').downcase.to_sym
          end
        end

        def concurrent_with_target_encounter(time)
          unless @target_encounter.nil?
            past = @context.most_recent_by_name(@target_encounter)
            return !past.nil? && past.time == time
          end
        end

        # the record methods require the use of lookup tables.  Other (non-generic) modules statically define
        # lookups in the tables, but we must define the lookups dynamically before the record method is invoked.
        def add_lookup_code(lookup_hash)
          return if @codes.nil? || @codes.empty?

          sym = symbol
          value = {
            description: @codes.first['display'],
            codes: {}
          }
          @codes.each do |c|
            value[:codes][c['system']] ||= []
            value[:codes][c['system']] << c['code']
          end

          # intentionally returning the value for further modification (see Encounter.perform_encounter)
          lookup_hash[sym] = value
        end
      end

      class Initial < State
        def process(_time, _entity)
          # initial state always goes to next
          true
        end
      end

      class Simple < State
        def process(_time, _entity)
          # simple state always goes to next
          true
        end
      end

      class Terminal < State
        def process(_time, _entity)
          # never pass through the terminal state
          false
        end
      end

      class Delay < State
        def initialize(context, name)
          super
          @range = context.state_config(name)['range']
          @exact = context.state_config(name)['exact'] if @range.nil?
        end

        def process(time, _entity)
          if @expiration.nil?
            if !@range.nil?
              # choose a random duration within the specified range
              choice = rand(@range['low']..@range['high'])
              @expiration = choice.method(@range['unit']).call.since(@start_time)
            elsif !@exact.nil?
              @expiration = @exact['quantity'].method(@exact['unit']).call.since(@start_time)
            else
              @expiration = @start_time
            end
          end
          time >= @expiration
        end
      end

      class Guard < State
        def process(time, entity)
          # only indicate successful processing if the condition evaluates to true
          c = @context.state_config(@name)['allow']
          Synthea::Generic::Logic.test(c, @context, time, entity)
        end
      end

      class SetAttribute < State
        def process(_time, entity)
          c = @context.state_config(@name)
          entity[c['attribute']] = c['value']
          true
        end
      end

      class Encounter < State
        attr_reader :wellness, :processed, :time, :codes

        def initialize(context, name)
          super
          @processed = false
          if context.state_config(name)['wellness']
            @wellness = true
          else
            @wellness = false
            @codes = context.state_config(name)['codes']
            @class = context.state_config(name)['class']
          end
        end

        def process(time, entity)
          unless @wellness
            # This is a stand-alone (non-wellness) encounter, so proceed immediately!
            perform_encounter(time, entity)
          end
          @processed
        end

        # perform_encounter can be called from this state's process method, but it also might
        # be called as a result of a wellness encounter occurring
        def perform_encounter(time, entity, record_encounter = true)
          # set process to true so that the process method knows we've done a successful encounter
          @processed = true
          @time = time

          if record_encounter
            value = add_lookup_code(ENCOUNTER_LOOKUP)
            value[:class] = @class
            entity.record_synthea.encounter(symbol, time)
          end

          # Look through the history for conditions to diagnose
          @context.history.each do |h|
            if h.is_a?(ConditionOnset) && !h.diagnosed && h.target_encounter == @name
              h.diagnose(time, entity)
            end
          end
        end
      end

      class ConditionOnset < State
        attr_reader :diagnosed, :target_encounter

        def initialize(context, name)
          super
          @codes = context.state_config(name)['codes']
          @target_encounter = context.state_config(name)['target_encounter']
          @diagnosed = false
        end

        def process(time, entity)
          diagnose(time, entity) if concurrent_with_target_encounter(time)
          true
        end

        def diagnose(time, entity)
          add_lookup_code(Synthea::COND_LOOKUP)
          entity.record_synthea.condition(symbol, time)
          @diagnosed = true
        end
      end

      class MedicationOrder < State
        attr_reader :prescribed, :target_encounter

        def initialize(context, name)
          super
          @codes = context.state_config(name)['codes']
          @target_encounter = context.state_config(name)['target_encounter']
          @reason = context.state_config(name)['reason']
          @prescribed = false
        end

        def process(time, entity)
          prescribe(time, entity) if concurrent_with_target_encounter(time)
          true
        end

        def prescribe(time, entity)
          add_lookup_code(Synthea::MEDICATION_LOOKUP)
          unless @reason.nil?
            cond = @context.most_recent_by_name(@reason)
            cond = cond.symbol if cond
            cond = entity[@reason].to_sym if cond.nil? && entity[@reason]
          end
          if cond.nil?
            entity.record_synthea.medication_start(symbol, time, [])
          else
            entity.record_synthea.medication_start(symbol, time, [cond])
          end
          @prescribed = true
        end
      end

      class MedicationEnd < State
        def initialize(context, name)
          super
          cfg = context.state_config(name)
          @referenced_by = cfg['referenced_by_attribute']
          @medication_order = cfg['medication_order']
          @reason = (cfg['reason'] || 'prescription_expired').to_sym
          @codes = cfg['codes']
        end

        def process(time, entity)
          end_prescription(time, entity)
          true
        end

        def end_prescription(time, entity)
          if @referenced_by
            @type = entity[@referenced_by].to_sym
          elsif @medication_order
            @type = @context.most_recent_by_name(@medication_order).symbol
          elsif @codes
            @type = symbol
          else
            raise 'Medication End must define the medication to end either by code, a referenced entity attribute, or the name of the original MedicationOrder state'
          end

          entity.record_synthea.medication_stop(@type, time, [@reason])
        end
      end

      class Procedure < State
        attr_reader :operated, :target_encounter

        def initialize(context, name)
          super
          @codes = context.state_config(name)['codes']
          @target_encounter = context.state_config(name)['target_encounter']
          @reason = context.state_config(name)['reason']
          @operated = false
        end

        def process(time, entity)
          operate(time, entity) if concurrent_with_target_encounter(time)
          true
        end

        def operate(time, entity)
          add_lookup_code(Synthea::PROCEDURE_LOOKUP)
          cond = @context.most_recent_by_name(@reason) unless @reason.nil?
          unless @reason.nil?
            cond = @context.most_recent_by_name(@reason)
            cond = cond.symbol if cond
            cond = entity[@reason].to_sym if cond.nil? && entity[@reason]
          end
          if cond.nil?
            entity.record_synthea.procedure(symbol, time, nil)
          else
            entity.record_synthea.procedure(symbol, time, cond)
          end
          @operated = true
        end
      end

      class Observation < State
        def initialize(context, name)
          super
          cfg = context.state_config(name)
          @codes = cfg['codes']
          range = cfg['range']
          exact = cfg['exact']
          if range
            @value = rand(range['low']..range['high'])
          elsif exact
            @value = exact['quantity']
          else
            raise 'Observation state must specify value using either "range" or "exact"'
          end
          @unit = cfg['unit']
          @target_encounter = cfg['target_encounter']
          @type = symbol
        end

        def add_lookup_code(lookup_hash)
          # TODO: update the observation lookup hash so it's the same format as all the others
          # and then delete this method
          return if @codes.nil? || @codes.empty?
          code = @codes.first
          lookup_hash[@type] = { description: code['display'], code: code['code'], unit: @unit }
        end

        def process(time, entity)
          add_lookup_code(Synthea::OBS_LOOKUP)
          if concurrent_with_target_encounter(time)
            entity.record_synthea.observation(@type, time, @value)
          end
          true
        end
      end

      class Symptom < State
        def initialize(context, name)
          super
          cfg = context.state_config(name)
          @symptom = cfg['symptom']
          @cause = cfg['cause'] || context.config['name']
          range = cfg['range']
          exact = cfg['exact']
          if range
            @value = rand(range['low']..range['high'])
          elsif exact
            @value = exact['quantity']
          else
            raise 'Symptom state must specify value using either "range" or "exact"'
          end
        end

        def process(_time, entity)
          entity.set_symptom_value(@cause, @symptom, @value)
        end
      end

      class Death < State
        def initialize(context, name)
          super
          cfg = context.state_config(name)
          @range = cfg['range']
          if @range.nil?
            @exact = cfg['exact']
          end
        end

        def process(time, entity)
          if @range
            value = rand(@range['low'] .. @range['high']).method(@range['unit']).call().since(time)
          elsif @exact
            value = @exact['quantity'].method(@exact['unit']).call().since(time)
          end
          if value
            # Record the future death... if there is a condition with a known life-expectancy
            # and you want the model to keep running in the mean time.
            entity.events.create(value, :death, :generic, false)
            Synthea::Modules::Lifecycle.record_death(entity, value)
          else
            entity.events.create(time, :death, :generic, true)
            Synthea::Modules::Lifecycle.record_death(entity, time)
          end
          true
        end
      end

    end
  end
end
