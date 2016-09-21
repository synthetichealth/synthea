module Synthea
  module Generic
    module Logic
      def self.test(condition, context, time, entity)
        case condition['condition_type']
        when 'And'
          self.testAnd(condition, context, time, entity)
        when 'Or'
          self.testOr(condition, context, time, entity)
        when 'Not'
          self.testNot(condition, context, time, entity)
        when 'Gender'
          self.testGender(condition, context, time, entity)
        when 'Age'
          self.testAge(condition, context, time, entity)
        when 'Socioeconomic Status'
          self.testSES(condition, context, time, entity)
        when 'Date'
          self.testDate(condition, context, time, entity)
        when 'Attribute'
          self.testAttribute(condition, context, time, entity)
        when 'True'
          self.testTrue(condition, context, time, entity)
        when 'False'
          self.testFalse(condition, context, time, entity)
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

      def self.testNot(condition, context, time, entity)
        return ! test(condition['condition'], context, time, entity)
      end

      def self.testGender(condition, context, time, entity)
        return condition['gender'] == entity[:gender]
      end

      def self.testAge(condition, context, time, entity)
        birthdate = entity.event(:birth).time
        age = Synthea::Modules::Lifecycle.age(time, birthdate, nil, condition['unit'].to_sym)
        self.compare(age, condition['quantity'], condition['operator'])
      end

      def self.testSES(condition, context, time, entity)
        raise "Unsupported category: #{condition['category']}" if !%w(High Middle Low).include?(condition['category'])
        ses_category = Synthea::Modules::Lifecycle.socioeconomic_category(entity)
        self.compare(ses_category, condition['category'], '==')
      end

      def self.testDate(condition, context, time, entity)
        self.compare(time.year, condition['year'], condition['operator'])
      end

      def self.testAttribute(condition, context, time, entity)
        self.compare(entity[ condition['attribute'] ], condition['value'], condition['operator'])
      end

      def self.testTrue(condition, context, time, entity)
        return true
      end

      def self.testFalse(condition, context, time, entity)
        return false
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
        when 'is nil'
          return lhs.nil?
        when 'is not nil'
          return !lhs.nil?
        else
          raise "Unsupported operator: #{operator}"
        end
      end
    end
  end
end
