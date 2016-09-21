module Synthea
  module Generic
    module States
      # define a base state with common functionality that can be inherited by all other states
      class State
        attr_accessor :name, :entered, :exited, :start_time

        def initialize (context, name)
          @context = context
          @name = name
        end

        def run(time, entity)
          @entered ||= time
          @start_time ||= time
          exit = process(time, entity)
          if exit
            # Special handling for Delay, which may expire between run cycles
            if self.is_a? Delay
              @exited = @expiration
              @expiration = nil
              @start_time = @exited if @start_time > @exited
            else
              @exited = time
            end
          end

          c = @context.state_config(@name)
          if c && c['assign_to_attribute']
            entity[c['assign_to_attribute']] = self.symbol.to_s
          end

          return exit
        end

        # creates a symbol to be used in lookup tables (mainly for referencing codes)
        def symbol ()
          # prefer a code display value since the state name may not be unique between modules
          if ! @codes.nil? && ! @codes.empty?
            @codes.first['display'].gsub(/\s+/,"_").downcase.to_sym
          else
            @name.gsub(/\s+/,"_").downcase.to_sym
          end
        end

        def concurrent_with_target_encounter(time)
          if ! @target_encounter.nil?
            past = @context.most_recent_by_name(@target_encounter)
            return ! past.nil? && past.time == time
          end
        end

        # the record methods require the use of lookup tables.  Other (non-generic) modules statically define
        # lookups in the tables, but we must define the lookups dynamically before the record method is invoked.
        def add_lookup_code(lookup_hash)
          return if @codes.nil? || @codes.empty?

          sym = self.symbol()
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
        def process(time, entity)
          # initial state always goes to next
          true
        end
      end

      class Simple < State
        def process(time, entity)
          # simple state always goes to next
          true
        end
      end

      class Terminal < State
        def process(time, entity)
          # never pass through the terminal state
          false
        end
      end

      class Delay < State
        def initialize (context, name)
          super
          @range = context.state_config(name)['range']
          if @range.nil?
            @exact = context.state_config(name)['exact']
          end
        end

        def process(time, entity)
          if @expiration.nil?
            if ! @range.nil?
              # choose a random duration within the specified range
              choice = rand(@range['low'] .. @range['high'])
              @expiration = choice.method(@range['unit']).call().since(@start_time)
            elsif ! @exact.nil?
              @expiration = @exact['quantity'].method(@exact['unit']).call().since(@start_time)
            else
              @expiration = @start_time
            end
          end
          return time >= @expiration
        end
      end

      class Guard < State
        def process(time, entity)
          # only indicate successful processing if the condition evaluates to true
          c = @context.state_config(@name)['allow']
          return Synthea::Generic::Logic::test(c, @context, time, entity)
        end
      end

      class SetAttribute < State
        def process(time, entity)
          c = @context.state_config(@name)
          entity[ c['attribute'] ] = c['value']
          return true
        end
      end

      class Encounter < State
        attr_reader :wellness, :processed, :time, :codes

        def initialize (context, name)
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
          if !@wellness
            # This is a stand-alone (non-wellness) encounter, so proceed immediately!
            self.perform_encounter(time, entity)
          end
          return @processed
        end

        # perform_encounter can be called from this state's process method, but it also might
        # be called as a result of a wellness encounter occurring
        def perform_encounter(time, entity, record_encounter=true)
          # set process to true so that the process method knows we've done a successful encounter
          @processed = true
          @time = time

          if record_encounter
            value = self.add_lookup_code(ENCOUNTER_LOOKUP)
            value[:class] = @class
            entity.record_synthea.encounter(self.symbol(), time)
          end

          # Look through the history for conditions to diagnose
          @context.history.each do |h|
            if h.is_a?(ConditionOnset) && ! h.diagnosed && h.target_encounter == @name
              h.diagnose(time, entity)
            end
          end
        end
      end

      class ConditionOnset < State
        attr_reader :diagnosed, :target_encounter

        def initialize (context, name)
          super
          @codes = context.state_config(name)['codes']
          @target_encounter = context.state_config(name)['target_encounter']
          @diagnosed = false
        end

        def process(time, entity)
          if self.concurrent_with_target_encounter(time)
            self.diagnose(time, entity)
          end
          return true
        end

        def diagnose(time, entity)
          self.add_lookup_code(Synthea::COND_LOOKUP)
          entity.record_synthea.condition(self.symbol(), time)
          @diagnosed = true
        end
      end

      class MedicationOrder < State
        attr_reader :prescribed, :target_encounter

        def initialize (context, name)
          super
          @codes = context.state_config(name)['codes']
          @target_encounter = context.state_config(name)['target_encounter']
          @reason = context.state_config(name)['reason']
          @prescribed = false
        end

        def process(time, entity)
          if self.concurrent_with_target_encounter(time)
            self.prescribe(time, entity)
          end
          return true
        end

        def prescribe(time, entity)
          self.add_lookup_code(Synthea::MEDICATION_LOOKUP)
          cond = @context.most_recent_by_name(@reason) unless @reason.nil?
          if cond.nil?
            entity.record_synthea.medication_start(self.symbol(), time, [])
          else
            entity.record_synthea.medication_start(self.symbol(), time, [cond.symbol()])
          end
          @prescribed = true
        end
      end

      class MedicationEnd < State
        def initialize (context, name)
          super
          cfg = context.state_config(name)
          @referenced_by = cfg['referenced_by_attribute']
          @medication_order = cfg['medication_order']
          @reason = (cfg['reason'] || 'prescription_expired').to_sym
          @codes = cfg['codes']
        end

        def process(time, entity)
          self.end_prescription(time, entity)
          return true
        end

        def end_prescription(time, entity)
          if @referenced_by
            @type = entity[@referenced_by].to_sym
          elsif @medication_order
            @type = @context.most_recent_by_name(@medication_order).symbol()
          elsif @codes
            @type = self.symbol()
          else
            raise "Medication End must define the medication to end either by code, a referenced entity attribute, or the name of the original MedicationOrder state"
          end

          entity.record_synthea.medication_stop(@type, time, [@reason])
        end
      end

      class Procedure < State
        attr_reader :operated, :target_encounter

        def initialize (context, name)
          super
          @codes = context.state_config(name)['codes']
          @target_encounter = context.state_config(name)['target_encounter']
          @reason = context.state_config(name)['reason']
          @operated = false
        end

        def process(time, entity)
          if self.concurrent_with_target_encounter(time)
            self.operate(time, entity)
          end
          return true
        end

        def operate(time, entity)
          self.add_lookup_code(Synthea::PROCEDURE_LOOKUP)
          cond = @context.most_recent_by_name(@reason) unless @reason.nil?
          if cond.nil?
            entity.record_synthea.procedure(self.symbol(), time, nil)
          else
            entity.record_synthea.procedure(self.symbol(), time, cond.symbol())
          end
          @operated = true
        end
      end

      class Death < State
        def process(time, entity)
          entity[:is_alive] = false
          entity.events.create(time, :death, :generic, true)
          Synthea::Modules::Lifecycle.record_death(entity, time)
          true
        end
      end
    end
  end
end
