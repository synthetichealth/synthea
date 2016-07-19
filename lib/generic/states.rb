module Synthea
  module Generic
    module States
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

        def concurrent_with_target_encounter(time)
          if ! @target_encounter.nil?
            past = @context.most_recent_by_name(@target_encounter)
            return ! past.nil? && past.time == time
          end
        end

        def add_lookup_code(lookup_hash)
          return if @codes.nil? || @codes.empty?

          sym = self.symbol()
          lookup_hash[sym] = {
            description: @codes.first['display'],
            codes: {}
          }
          @codes.each do |c|
            lookup_hash[sym][:codes][c['system']] ||= []
            lookup_hash[sym][:codes][c['system']] << c['code']
          end
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
          true
        end
      end

      class Delay < State
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

      class Guard < State
        def process(time, entity)
          c = @context.config['states'][@name]['if']
          return Synthea::Generic::Logic::test(c, @context, time, entity)
        end
      end

      class Encounter < State
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

      class ConditionOnset < State
        attr_reader :diagnosed, :target_encounter

        def initialize (context, name, start)
          super
          @codes = context.config['states'][name]['codes']
          @target_encounter = context.config['states'][name]['target_encounter']
          @diagnosed = false
        end

        def process(time, entity)
          puts "⬇ Condition Onset #{@name} at age #{entity[:age]} on #{@start}"
          if self.concurrent_with_target_encounter(time)
            self.diagnose(time, entity)
          end
          return true
        end

        def diagnose(time, entity)
          self.add_lookup_code(Synthea::COND_LOOKUP)
          entity.record_synthea.condition(self.symbol(), time)
          puts "⬇ Diagnosed #{@name} at age #{entity[:age]} on #{time}"
          @diagnosed = true
        end
      end

      class MedicationOrder < State
        attr_reader :prescribed, :target_encounter

        def initialize (context, name, start)
          super
          @codes = context.config['states'][name]['codes']
          @target_encounter = context.config['states'][name]['target_encounter']
          @reason = context.config['states'][name]['reason']
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
          puts "⬇ Prescribed #{@name} at age #{entity[:age]} on #{time}"
          @prescribed = true
        end
      end

      class Procedure < State
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

      class Death < State
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
end