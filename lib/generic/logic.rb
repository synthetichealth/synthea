module Synthea
  module Generic
    module Logic
      def self.test(condition, context, time, entity)
        case condition['condition_type']
        when 'And'
          test_and(condition, context, time, entity)
        when 'Or'
          test_or(condition, context, time, entity)
        when 'Not'
          test_not(condition, context, time, entity)
        when 'Gender'
          test_gender(condition, context, time, entity)
        when 'Age'
          test_age(condition, context, time, entity)
        when 'Socioeconomic Status'
          test_ses(condition, context, time, entity)
        when 'Date'
          test_date(condition, context, time, entity)
        when 'Attribute'
          test_attribute(condition, context, time, entity)
        when 'Symptom'
          test_symptom(condition, context, time, entity)
        when 'PriorState'
          test_prior_state(condition, context, time, entity)
        when 'True'
          test_true(condition, context, time, entity)
        when 'False'
          test_false(condition, context, time, entity)
        else
          raise "Unsupported condition type: #{condition['condition_type']}"
        end
      end

      def self.test_and(condition, context, time, entity)
        condition['conditions'].each do |c|
          return false unless test(c, context, time, entity)
        end
        true
      end

      def self.test_or(condition, context, time, entity)
        condition['conditions'].each do |c|
          return true if test(c, context, time, entity)
        end
        false
      end

      def self.test_not(condition, context, time, entity)
        !test(condition['condition'], context, time, entity)
      end

      def self.test_gender(condition, _context, _time, entity)
        condition['gender'] == entity[:gender]
      end

      def self.test_age(condition, _context, time, entity)
        birthdate = entity.event(:birth).time
        age = Synthea::Modules::Lifecycle.age(time, birthdate, nil, condition['unit'].to_sym)
        compare(age, condition['quantity'], condition['operator'])
      end

      def self.test_ses(condition, _context, _time, entity)
        raise "Unsupported category: #{condition['category']}" unless %w(High Middle Low).include?(condition['category'])
        ses_category = Synthea::Modules::Lifecycle.socioeconomic_category(entity)
        compare(ses_category, condition['category'], '==')
      end

      def self.test_date(condition, _context, time, _entity)
        compare(time.year, condition['year'], condition['operator'])
      end

      def self.test_attribute(condition, _context, _time, entity)
        attribute = entity[condition['attribute']] || entity[condition['attribute'].to_sym]
        compare(attribute, condition['value'], condition['operator'])
      end

      def self.test_symptom(condition, _context, _time, entity)
        compare(entity.get_symptom_value(condition['symptom']), condition['value'], condition['operator'])
      end

      def self.test_prior_state(condition, context, _time, _entity)
        !context.most_recent_by_name(condition['name']).nil?
      end

      def self.test_true(_condition, _context, _time, _entity)
        true
      end

      def self.test_false(_condition, _context, _time, _entity)
        false
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
