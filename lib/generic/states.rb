module Synthea
  module Generic
    module States
      class State
        attr_accessor :name, :entered, :exited

        def initialize (context, name)
          @context = context
          @name = name
        end

        def run(time, entity)
          @entered ||= time
          exit = process(time, entity)
          if exit
            # Special handling for Delay, which may expire between run cycles
            if self.is_a? Delay
              @exited = @expiration
              @entered = @exited if @entered > @exited
            else
              @exited = time
            end
          end
          return exit
        end

        def symbol ()
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
              choice = rand(@range['low'] .. @range['high'])
              @expiration = choice.method(@range['unit']).call().since(@entered)
            elsif ! @exact.nil?
              @expiration = @exact['quantity'].method(@exact['unit']).call().since(@entered)
            else
              @expiration = @entered
            end
          end
          # TODO: Support delays that go between run cycles (e.g., 3-day delay when the
          # cycle runs every 7 days).  Currently, the delay would expire 4 days late.
          return time >= @expiration
        end
      end

      class Guard < State
        def process(time, entity)
          c = @context.state_config(@name)['allow']
          return Synthea::Generic::Logic::test(c, @context, time, entity)
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
            # No need to wait for a wellness encounter.  Do it now!
            # TODO: Record the encounter first...
            self.perform_encounter(time, entity)
          end
          return @processed
        end

        def perform_encounter(time, entity, record_encounter=true)
          @processed = true
          @time = time
          if record_encounter
            value = self.add_lookup_code(ENCOUNTER_LOOKUP)
            value[:class] = @class
            entity.record_synthea.encounter(self.symbol(), time)
          end
          self.record_encounter_activities(time, entity)
        end

        def record_encounter_activities(time, entity)
          # Look through the history for things to record
          # TODO: Consider if we should even allow medications and procedures to be defined *before* encounter
          @context.history.each do |h|
            # Diagnose conditions
            if h.is_a?(ConditionOnset) && ! h.diagnosed && h.target_encounter == @name
              h.diagnose(time, entity)
            # Prescribe medications
            elsif h.is_a?(MedicationOrder) && ! h.prescribed && target_encounter == @name
              h.prescribe(time, entity)
            # Operate!
            elsif h.is_a?(Procedure) && ! h.operated && target_encounter == @name
              h.operate(time, entity)
            end
          end
          @processed = true
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
          if ! @reason.nil?
            cond = @context.most_recent_by_name(@reason)
            entity.record_synthea.medication_start(self.symbol(), time, cond.symbol())
          else
            entity.record_synthea.medication_start(self.symbol(), time)
          end
          @prescribed = true
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
          if ! @reason.nil?
            cond = @context.most_recent_by_name(@reason)
            entity.record_synthea.procedure(self.symbol(), time, cond.symbol())
          else
            entity.record_synthea.procedure(self.symbol(), time)
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