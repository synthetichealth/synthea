module Synthea
  module Output
    class QOL
      attr_accessor :disability_weights

      def initialize
        # this file is at at synthea/lib/records/daly.rb
        # gbd_disability_weights.json located at synthea/lib/resources/gbd_disability_weights.json
        # disability weights from Global Burden of Disease
        # at http://ghdx.healthdata.org/record/global-burden-disease-study-2015-gbd-2015-disability-weights
        dw_file = File.join(File.dirname(__FILE__), '..', '..', 'resources', 'gbd_disability_weights.json')
        @disability_weights = JSON.parse(File.read(dw_file))
      end

      # calculates DALY and QALY after person has been generated
      def calculate(person, end_date = Synthea::Config.end_date)
        # Disability-Adjusted Life Year = DALY = YLL + YLD
        # Years of Life Lost = YLL = (1) * (standard life expectancy at age of death in years)
        # Years Lost due to Disability = YLD = (disability weight) * (average duration of case)
        # from http://www.who.int/healthinfo/global_burden_disease/metrics_daly/en/
        yll = 0
        yld = 0
        age = person[:age]
        birth_time = person.events.events[:birth][0].time
        age = ((end_date - birth_time) / 31_557_600).round if end_date != Synthea::Config.end_date

        if person.had_event?(:death)
          # life expectancy equation derived from IHME GBD 2015 Reference Life Table
          # 6E-5x^3 - 0.0054x^2 - 0.8502x + 86.16
          # R^2 = 0.99978
          l = (0.00006 * age * age * age) - (0.0054 * age * age) - (0.8502 * age) + 86.16
          yll = l
        end

        all_conditions = person.record_synthea.conditions
        (0...age).each do |year|
          year_start = birth_time + year.years
          year_end = birth_time + (year + 1).years
          conditions_in_year = conditions_in_year(all_conditions, year_start, year_end)

          conditions_in_year.each do |condition|
            dw = @disability_weights[condition['type'].to_s]['disability_weight']
            weight = weight(dw, year + 1)
            # duration is 1 year
            yld += weight
          end
        end

        daly = yll + yld
        qaly = age - yld

        person.record_synthea.observation(:DALY, end_date, daly, 'fhir' => :observation, 'ccda' => :no_action, 'category' => 'survey')
        person.record_synthea.observation(:QALY, end_date, qaly, 'fhir' => :observation, 'ccda' => :no_action, 'category' => 'survey')
      end

      # returns list of all conditions that occur in a given year
      def conditions_in_year(conditions, year_start, year_end)
        conditions_in_year = []
        conditions.each do |condition|
          if @disability_weights.key?(condition['type'].to_s)
            condition_start_time = condition['time']
            condition_end_time = condition['end_time']
            condition_end_time = Synthea::Config.end_date if condition_end_time.nil?
            conditions_in_year << condition if year_start >= condition_start_time && condition_start_time < year_end && condition_end_time > year_start
          end
          next
        end
        conditions_in_year
      end

      # accounts for age weight
      def weight(disability_weight, age)
        # age_weight = 0.1658 * age * e^(-0.04 * age)
        # from http://www.who.int/quantifying_ehimpacts/publications/9241546204/en/
        # weight = age_weight * disability_weight
        age_weight = 0.1658 * age * Math.exp(-0.04 * age)
        weight = age_weight * disability_weight
        weight
      end
    end
  end
end
