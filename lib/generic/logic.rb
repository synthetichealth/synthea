module Synthea
  module Generic
    module Logic
      class Condition
        include Synthea::Generic::Metadata
        include Synthea::Generic::Hashable

        metadata 'condition_type', ignore: true

        def initialize(condition)
          from_hash(condition)
        end

        def compare(lhs, rhs, operator)
          case operator
          when '<'
            return lhs < rhs
          when '<='
            return lhs <= rhs
          when '=='
            return lhs == rhs
          when '>='
            return lhs >= rhs
          when '>'
            return lhs > rhs
          when '!='
            return lhs != rhs
          when 'is nil'
            return lhs.nil?
          when 'is not nil'
            return !lhs.nil?
          else
            raise "Unsupported operator: #{operator}"
          end
        end

        def find_referenced_type(entity, name)
          if codes
            # based on state.symbol
            codes.first.display.gsub(/\s+/, '_').downcase.to_sym
          elsif referenced_by_attribute
            entity[referenced_by_attribute] || entity[referenced_by_attribute.to_sym]
          else
            raise "#{name} condition must be specified by code or attribute"
          end
        end
      end

      class GroupedCondition < Condition
        attr_accessor :conditions
        required_field :conditions

        metadata 'conditions', type: 'Logic::Condition', polymorphism: { key: 'condition_type', package: 'Logic' }, min: 1, max: Float::INFINITY
      end

      class And < GroupedCondition
        def test(context, time, entity)
          conditions.each do |c|
            return false unless c.test(context, time, entity)
          end
          true
        end
      end

      class Or < GroupedCondition
        def test(context, time, entity)
          conditions.each do |c|
            return true if c.test(context, time, entity)
          end
          false
        end
      end

      class AtLeast < GroupedCondition
        attr_accessor :minimum
        required_field :minimum

        def test(context, time, entity)
          minimum <= conditions.count { |c| c.test(context, time, entity) }
        end
      end

      class AtMost < GroupedCondition
        attr_accessor :maximum
        required_field :maximum

        def test(context, time, entity)
          maximum >= conditions.count { |c| c.test(context, time, entity) }
        end
      end

      class Not < Condition
        attr_accessor :condition
        required_field :condition

        metadata 'condition', type: 'Logic::Condition', polymorphism: { key: 'condition_type', package: 'Logic' }, min: 1, max: 1

        def test(context, time, entity)
          !condition.test(context, time, entity)
        end
      end

      class Gender < Condition
        attr_accessor :gender
        required_field :gender

        def test(_context, _time, entity)
          gender == entity[:gender]
        end
      end

      class Age < Condition
        attr_accessor :quantity, :unit, :operator
        required_field and: [:quantity, :unit, :operator]

        def test(_context, time, entity)
          birthdate = entity.event(:birth).time
          age = Synthea::Modules::Lifecycle.age(time, birthdate, nil, unit.to_sym)
          compare(age, quantity, operator)
        end
      end

      class SocioeconomicStatus < Condition
        attr_accessor :category
        required_field :category

        def test(_context, _time, entity)
          raise "Unsupported category: #{category}" unless %w(High Middle Low).include?(category)
          ses_category = Synthea::Modules::Lifecycle.socioeconomic_category(entity)
          compare(ses_category, category, '==')
        end
      end

      class Date < Condition
        attr_accessor :year, :operator
        required_field and: [:year, :operator]

        def test(_context, time, _entity)
          compare(time.year, year, operator)
        end
      end

      class Attribute < Condition
        attr_accessor :attribute, :value, :operator
        required_field and: [:attribute, :operator] # value is allowed to be omitted if operator is 'is nil'

        def test(_context, _time, entity)
          entity_value = entity[attribute] || entity[attribute.to_sym]
          compare(entity_value, value, operator)
        end
      end

      class Symptom < Condition
        attr_accessor :symptom, :value, :operator
        required_field and: [:symptom, :value, :operator]

        def test(_context, _time, entity)
          compare(entity.get_symptom_value(symptom), value, operator)
        end
      end

      class Observation < Condition
        attr_accessor :codes, :referenced_by_attribute, :operator, :value
        required_field and: [:operator, :value, or: [:codes, :referenced_by_attribute]]

        metadata 'codes', type: 'Components::Code', min: 0, max: Float::INFINITY

        def test(_context, _time, entity)
          # find the most recent instance of the given observation
          obstype = find_referenced_type(entity, 'Observation')

          obs = entity.record_synthea.observations.select { |o| o['type'] == obstype }

          if obs.empty?
            if ['is nil', 'is not nil'].include?(operator)
              compare(nil, value, operator)
            else
              raise "No observations exist for type #{obstype}, cannot compare values"
            end
          else
            compare(obs.last['value'], value, operator)
          end
        end
      end

      class ActiveCondition < Condition
        attr_accessor :codes, :referenced_by_attribute
        required_field or: [:codes, :referenced_by_attribute]

        metadata 'codes', type: 'Components::Code', min: 0, max: Float::INFINITY

        def test(_context, _time, entity)
          contype = find_referenced_type(entity, 'Active Condition')
          entity.record_synthea.present[contype]
        end
      end

      class PriorState < Condition
        attr_accessor :name
        required_field :name

        metadata 'name', reference_to_state_type: 'State', min: 1, max: 1

        def test(context, _time, _entity)
          !context.most_recent_by_name(name).nil?
        end
      end

      class ActiveCareplan < Condition
        attr_accessor :codes, :referenced_by_attribute
        required_field or: [:codes, :referenced_by_attribute]

        metadata 'codes', type: 'Components::Code', min: 0, max: Float::INFINITY

        def test(_context, _time, entity)
          contype = find_referenced_type(entity, 'Active Careplan')
          entity.record_synthea.careplan_active?(contype)
        end
      end

      class ActiveMedication < Condition
        attr_accessor :codes, :referenced_by_attribute
        required_field or: [:codes, :referenced_by_attribute]

        metadata 'codes', type: 'Components::Code', min: 0, max: Float::INFINITY

        def test(_context, _time, entity)
          medtype = find_referenced_type(entity, 'Active Medication')
          entity.record_synthea.medication_active?(medtype)
        end
      end

      class True < Condition
        def test(_context, _time, _entity)
          true
        end
      end

      class False < Condition
        def test(_context, _time, _entity)
          false
        end
      end
    end
  end
end
