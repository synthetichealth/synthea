module Synthea
  module Generic
    module States
      # define a base state with common functionality that can be inherited by all other states
      class State
        include Synthea::Generic::Validation

        attr_accessor :name, :entered, :exited, :start_time,
                      :assign_to_attribute,
                      :transition

        required_field :name
        required_field :transition

        def initialize(context, name)
          @context = context
          @name = name

          @config = context.state_config(name) || {}

          # automatically assign the keys in the state to attributes
          @config.each do |key, value|
            next if %w(type remarks).include?(key)
            if key.include?('_transition')
              set_transition(key, value)
            else
              send("#{key}=", value)
            end
          end
        end

        def set_transition(type, transition)
          @transition = Object.const_get("Synthea::Generic::Transitions::#{type.camelize}").new(transition)
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

          entity[@assign_to_attribute] = symbol.to_s if @assign_to_attribute
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
        required_fields.delete(:transition)

        def process(_time, _entity)
          # never pass through the terminal state
          false
        end
      end

      class Delay < State
        attr_accessor :range, :exact

        required_field or: [:range, :exact]

        def process(time, _entity)
          if @expiration.nil?
            if !@range.nil?
              # choose a random duration within the specified range
              low = @range['low'].send(@range['unit'])
              high = @range['high'].send(@range['unit'])
              @expiration = rand(low..high).since(@start_time)
            elsif !@exact.nil?
              @expiration = @exact['quantity'].send(@exact['unit']).since(@start_time)
            else
              @expiration = @start_time
            end
          end
          time >= @expiration
        end
      end

      class Guard < State
        attr_accessor :allow

        required_field :allow

        def process(time, entity)
          # only indicate successful processing if the condition evaluates to true
          Synthea::Generic::Logic.test(@allow, @context, time, entity)
        end
      end

      class SetAttribute < State
        attr_accessor :attribute, :value

        def process(_time, entity)
          entity[@attribute] = @value
          true
        end
      end

      class Encounter < State
        attr_accessor :wellness, :processed, :time, :codes, :encounter_class

        required_field or: [:wellness, and: [:codes, :encounter_class]]

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
            value[:class] = @encounter_class
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
        attr_accessor :diagnosed, :target_encounter, :codes

        required_field and: [:target_encounter, :codes]

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

      class ConditionEnd < State
        attr_accessor :referenced_by_attribute, :condition_onset, :codes

        required_field or: [:referenced_by_attribute, :condition_onset, :codes]

        def process(time, entity)
          if @referenced_by_attribute
            @type = entity[@referenced_by_attribute].to_sym
          elsif @condition_onset
            @type = @context.most_recent_by_name(@condition_onset).symbol
          elsif @codes
            @type = symbol
          else
            raise 'Condition End must define the condition to end either by code, a referenced entity attribute, or the name of the original ConditionOnset state'
          end

          entity.record_synthea.end_condition(@type, time)
          true
        end
      end

      class MedicationOrder < State
        attr_accessor :prescribed, :target_encounter, :codes, :reason

        required_field and: [:target_encounter, :codes]

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
        attr_accessor :referenced_by_attribute, :medication_order, :reason, :codes

        def initialize(context, name)
          super
          @reason ||= :prescription_expired
        end

        def process(time, entity)
          end_prescription(time, entity)
          true
        end

        def end_prescription(time, entity)
          if @referenced_by_attribute
            @type = entity[@referenced_by_attribute].to_sym
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

      class CarePlanStart < State
        attr_accessor :target_encounter, :codes, :activities, :reason

        def process(time, entity)
          start_plan(time, entity) if concurrent_with_target_encounter(time)
          true
        end

        def start_plan(time, entity)
          add_lookup_code(Synthea::CAREPLAN_LOOKUP)
          activities = add_activity_lookup_codes(Synthea::CAREPLAN_LOOKUP)

          unless @reason.nil?
            rsn = @context.most_recent_by_name(@reason)
            rsn = rsn.symbol if rsn
            rsn = entity[@reason].to_sym if rsn.nil? && entity[@reason]
          end

          if rsn
            entity.record_synthea.careplan_start(symbol, activities, time, [rsn])
          else
            entity.record_synthea.careplan_start(symbol, activities, time, [])
          end
        end

        def add_activity_lookup_codes(lookup_hash)
          return if @activities.nil? || @activities.empty?
          symbols = []
          @activities.each do |a|
            sym = activity_symbol(a)
            symbols << sym
            value = {
              description: a['display'],
              codes: {}
            }
            value[:codes][a['system']] ||= []
            value[:codes][a['system']] << a['code']
            lookup_hash[sym] = value
          end
          symbols
        end

        def activity_symbol(activity)
          if activity.key?('display')
            activity['display'].gsub(/\s+/, '_').downcase.to_sym
          else
            raise 'Activity must have a display name to hash'
          end
        end
      end

      class CarePlanEnd < State
        attr_accessor :careplan, :reason, :codes, :referenced_by_attribute

        def initialize(context, name)
          super
          @reason ||= :careplan_ended
        end

        def process(time, entity)
          end_plan(time, entity)
          true
        end

        def end_plan(time, entity)
          if @referenced_by_attribute
            @type = entity[@referenced_by_attribute].to_sym
          elsif @careplan
            @type = @context.most_recent_by_name(@careplan).symbol
          elsif @codes
            @type = symbol
          else
            raise 'CarePlanEnd must define the CarePlan to end either by code, a referenced entity attribute, or the name of the original CarePlanStart state'
          end

          entity.record_synthea.careplan_stop(@type, time)
        end
      end

      class Procedure < State
        attr_accessor :operated, :target_encounter, :reason, :codes

        required_field and: [:target_encounter, :codes]

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
        attr_accessor :codes, :range, :exact, :unit, :target_encounter

        required_field and: [:target_encounter, :codes, :unit]
        required_field or: [:range, :exact]

        def initialize(context, name)
          super
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

          if @range
            @value = rand(@range['low']..@range['high'])
          elsif exact
            @value = @exact['quantity']
          else
            raise 'Observation state must specify value using either "range" or "exact"'
          end

          if concurrent_with_target_encounter(time)
            entity.record_synthea.observation(@type, time, @value)
          end
          true
        end
      end

      class Symptom < State
        attr_accessor :symptom, :cause, :range, :exact

        def initialize(context, name)
          super
          @cause ||= context.config['name']
        end

        def process(_time, entity)
          if range
            @value = rand(@range['low']..@range['high'])
          elsif exact
            @value = @exact['quantity']
          else
            raise 'Symptom state must specify value using either "range" or "exact"'
          end
          entity.set_symptom_value(@cause, @symptom, @value)
        end
      end

      class Death < State
        attr_accessor :range, :exact, :referenced_by_attribute, :condition_onset, :codes

        def process(time, entity)
          if @range
            low = @range['low'].send(@range['unit'])
            high = @range['high'].send(@range['unit'])
            value = rand(low..high).since(time)
          elsif @exact
            value = @exact['quantity'].send(@exact['unit']).since(time)
          end

          # this is the same as the ConditionEnd logic, maybe we want to extract this somewhere
          if @referenced_by_attribute
            @reason = entity[@referenced_by_attribute].to_sym
          elsif @condition_onset
            @reason = @context.most_recent_by_name(@condition_onset).symbol
          elsif @codes
            @reason = symbol
          end

          if value
            # Record the future death... if there is a condition with a known life-expectancy
            # and you want the model to keep running in the mean time.
            entity.events.create(value, :death, :generic, false)
            Synthea::Modules::Lifecycle.record_death(entity, value, @reason)
          else
            entity.events.create(time, :death, :generic, true)
            Synthea::Modules::Lifecycle.record_death(entity, time, @reason)
          end
          true
        end
      end
    end
  end
end
