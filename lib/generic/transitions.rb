module Synthea
  module Generic
    module Transitions
      class Transition
        include Synthea::Generic::Metadata
        include Synthea::Generic::Hashable

        def initialize(transition)
          from_hash(transition)
        end
      end

      class DirectTransition < Transition
        attr_accessor :transition

        def initialize(transition)
          super('transition' => transition)
        end

        def follow(_context, _entity, _time)
          @transition
        end

        def all_transitions
          [@transition]
        end
      end

      class TransitionOption
        include Synthea::Generic::Metadata
        include Synthea::Generic::Hashable

        attr_accessor :transition

        def initialize(transition)
          from_hash(transition)
        end
      end

      class DistributedTransitionOption < TransitionOption
        include Synthea::Generic::Metadata
        include Synthea::Generic::Hashable

        attr_accessor :distribution
        required_field :transition
        required_field :distribution
      end

      class DistributedTransition < Transition
        attr_accessor :transitions

        metadata 'transitions', type: 'Transitions::DistributedTransitionOption', min: 1, max: 999

        def initialize(transition)
          super('transitions' => transition)
        end

        def follow(_context, _entity, _time)
          pick_distributed_transition(@transitions)
        end

        def all_transitions
          @transitions.collect(&:transition)
        end

        def pick_distributed_transition(distributions)
          # distributed_transition is an array of distributions that should total 1.0.
          # So... pick a random float from 0.0 to 1.0 and walk up the scale.
          choice = rand
          high = 0.0
          distributions.each do |dt|
            high += dt.distribution
            return dt.transition if choice < high
          end
          # We only get here if the numbers didn't add to 1.0 or if one of the numbers caused
          # floating point imprecision (very, very rare).  Just go with the last one.
          distributions.last.transition
        end
      end

      class ConditionalTransitionOption < TransitionOption
        attr_accessor :condition

        metadata 'condition', type: 'Logic::Condition', polymorphism: { key: 'condition_type', package: 'Logic' }, min: 0, max: 1
      end

      class ConditionalTransition < Transition
        attr_accessor :transitions

        metadata 'transitions', type: 'Transitions::ConditionalTransitionOption', min: 1, max: 999

        def initialize(transition)
          super('transitions' => transition)
        end

        def all_transitions
          @transitions.collect(&:transition)
        end

        def follow(context, entity, time)
          @transitions.each do |ct|
            cond = ct.condition
            if cond.nil? || cond.test(context, time, entity)
              return ct.transition
            end
          end
          nil # no condition met
        end
      end

      class ComplexTransitionOption < TransitionOption
        attr_accessor :condition, :distributions

        metadata 'condition', type: 'Logic::Condition', polymorphism: { key: 'condition_type', package: 'Logic' }, min: 0, max: 1
        metadata 'distributions', type: 'Transitions::DistributedTransitionOption', min: 1, max: 999
      end

      class ComplexTransition < DistributedTransition
        # inherit from distributed to get access to pick_distributed_transition
        attr_accessor :transitions

        metadata 'transitions', type: 'Transitions::ComplexTransitionOption', min: 1, max: 999

        def all_transitions
          ts = @transitions.collect do |ct|
            if ct.transition
              ct.transition
            else
              ct.distributions.collect(&:transition)
            end
          end
          ts.flatten.uniq
        end

        def follow(context, entity, time)
          @transitions.each do |ct|
            cond = ct.condition
            if cond.nil? || cond.test(context, time, entity)
              return ct.transition if ct.transition
              return pick_distributed_transition(ct.distributions)
            end
          end
          nil # no condition met
        end
      end
    end
  end
end
