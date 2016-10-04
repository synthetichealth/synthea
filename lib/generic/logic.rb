module Synthea
  module Generic
    module Logic
      def self.test(condition, context, time, entity)
        func = "test_#{condition['condition_type'].gsub(/\s+/, '_').downcase}"

        raise "Unsupported condition type: #{condition['condition_type']}" unless respond_to?(func)

        send(func, condition, context, time, entity)
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

      def self.test_socioeconomic_status(condition, _context, _time, entity)
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

      def self.test_observation(condition, _context, _time, entity)
        # find the most recent instance of the given observation
        obstype = if condition['codes']
                    # based on state.symbol
                    condition['codes'].first['display'].gsub(/\s+/, '_').downcase.to_sym
                  elsif condition['referenced_by_attribute']
                    entity[condition['referenced_by_attribute']] || entity[condition['referenced_by_attribute'].to_sym]
                  else
                    raise 'Observation condition must be specified by code or attribute'
                  end

        obs = entity.record_synthea.observations.select { |o| o['type'] == obstype }
        operator = condition['operator']

        if obs.empty?
          if ['is nil', 'is not nil'].include?(operator)
            compare(nil, condition['value'], operator)
          else
            raise "No observations exist for type #{obstype}, cannot compare values"
          end
        else
          compare(obs.last['value'], condition['value'], operator)
        end
      end

      def self.test_condition(condition, _context, _time, entity)
        # return true if the given condition is currently active
        contype = if condition['codes']
                    # based on state.symbol
                    condition['codes'].first['display'].gsub(/\s+/, '_').downcase.to_sym
                  elsif condition['referenced_by_attribute']
                    entity[condition['referenced_by_attribute']] || entity[condition['referenced_by_attribute'].to_sym]
                  else
                    raise 'Condition condition must be specified by code or attribute'
                  end

        entity.record_synthea.present[contype]
      end

      def self.test_priorstate(condition, context, _time, _entity)
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
