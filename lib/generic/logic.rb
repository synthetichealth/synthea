module Synthea
  module Generic
    module Logic
      def self.test(condition, context, time, entity)
        case condition['condition_type']
        when 'And'
          self.testAnd(condition, context, time, entity)
        when 'Or'
          self.testOr(condition, context, time, entity)
        when 'Gender'
          self.testGender(condition, context, time, entity)
        when 'Age'
          self.testAge(condition, context, time, entity)
        else
          raise "Unsupported condition type: #{condition['condition_type']}"
        end
      end

      def self.testAnd(condition, context, time, entity)
        condition['conditions'].each do |c|
          if ! self.test(c, context, time, entity)
            return false
          end
        end
        return true
      end

      def self.testOr(condition, context, time, entity)
        condition['conditions'].each do |c|
          if test(c, context, time, entity)
            return true
          end
        end
        return false
      end

      def self.testGender(condition, context, time, entity)
        return condition['gender'] == entity[:gender]
      end

      def self.testAge(condition, context, time, entity)
        birthdate = entity.event(:birth).time
        age = Synthea::Modules::Lifecycle.age(time, birthdate, nil, condition['unit'].to_sym)
        self.compare(age, condition['quantity'], condition['operator'])
      end

      def self.compare(lhs, rhs, operator)
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
        else
          raise "Unsupported operator: #{operator}"
        end
      end
    end
  end
end