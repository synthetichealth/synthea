module Synthea
  module Generic
    module Transitions
      class Transition
        include Synthea::Generic::Validation
      end

      class DirectTransition < Transition
        def initialize(transition)
          @transition = transition
        end

        def follow(_context, _entity, _time)
          @transition
        end
      end

      class DistributedTransition < Transition
        def initialize(transition)
          @distributions = transition
        end

        def follow(_context, _entity, _time)
          pick_distributed_transition(@distributions)
        end

        def pick_distributed_transition(transitions)
          # distributed_transition is an array of distributions that should total 1.0.
          # So... pick a random float from 0.0 to 1.0 and walk up the scale.
          choice = rand
          high = 0.0
          transitions.each do |dt|
            high += dt['distribution']
            return dt['transition'] if choice < high
          end
          # We only get here if the numbers didn't add to 1.0 or if one of the numbers caused
          # floating point imprecision (very, very rare).  Just go with the last one.
          transitions.last['transition']
        end
      end

      class ConditionalTransition < Transition
        def initialize(transition)
          @transitions = transition
        end

        def follow(context, entity, time)
          @transitions.each do |ct|
            cond = ct['condition']
            if cond.nil? || Synthea::Generic::Logic.test(cond, context, time, entity)
              return ct['transition']
            end
          end
          nil # no condition met
        end
      end

      class ComplexTransition < DistributedTransition
        # inherit from distributed to get access to pick_distributed_transition
        def initialize(transition)
          @transitions = transition
        end

        def follow(context, entity, time)
          @transitions.each do |ct|
            cond = ct['condition']
            if cond.nil? || Synthea::Generic::Logic.test(cond, context, time, entity)
              return ct['transition'] if ct['transition']
              return pick_distributed_transition(ct['distributions'])
            end
          end
          nil # no condition met
        end
      end
    end
  end
end
