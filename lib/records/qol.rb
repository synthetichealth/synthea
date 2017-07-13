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

      # calculates daly after person has been generated at any time point of their life (end_date)
      def calculate_daly(person, end_date = Synthea::Config.end_date)
        # age depends on time at which daly is calculated, default is Synthea's end time
        age = person[:age_mos] / 12
        unless end_date == Synthea::Config.end_date
          birth_time = person.events.events[:birth][0].time
          age_seconds = end_date - birth_time
          age = age_seconds / 31_548_096
        end

        # life expectancy equation calculated from GBD life table data
        # 6E-5x^3 - 0.0054x^2 - 0.8502x + 86.16
        # R^2 = 0.99978
        l = 0.00006 * age * age * age - 0.0054 * age * age - 0.8502 * age + 86.16

        # Disability-Adjusted Life Year = DALY = YLL + YLD
        # Years of Life Lost = YLL = (1) * (standard life expectancy at age of death in years)
        # Years Lost due to Disability = YLD = (disability weight) * (average duration of case)
        # from http://www.who.int/healthinfo/global_burden_disease/metrics_daly/en/
        yll = 0
        yld = 0
        if person.had_event?(:death)
          yll = l if person.events.events[:death][0].time <= end_date
        end

        person.record_synthea.conditions.each do |condition|
          if @disability_weights.key?(condition['type'].to_s)
            dw = @disability_weights[condition['type'].to_s]['disability_weight']
            condition_start_time = condition['time']
            next if condition_start_time > end_date
            condition_end_time = end_date
            if condition['end_time']
              if condition['end_time'] <= end_date
                condition_end_time = condition['end_time']
              end
            end
            duration_seconds = condition_end_time - condition_start_time
            duration_years = duration_seconds / 31_548_096
            yld += dw * duration_years
          end
          next
        end
        daly = yll + yld
        person.record_synthea.observation(:DALY, end_date, daly, 'fhir' => :observation, 'ccda' => :no_action, 'category' => 'survey')
        daly
      end

      def calculate_qaly(person, daly, end_date = Synthea::Config.end_date)
        # age depends on time at which daly is calculated, default is Synthea's end time
        a = person[:age_mos] / 12
        unless end_date == Synthea::Config.end_date
          birth_time = person.events.events[:birth][0].time
          age_seconds = end_date - birth_time
          a = age_seconds / 31_548_096
        end

        # DALYs averted = QALYs gained * ca
        # ca = C * a * e^(-B * a) where C = 0.1658 and B = 0.04
        # from https://www.ncbi.nlm.nih.gov/pmc/articles/PMC3863845/
        ca = 0.1658 * a * Math.exp(-0.04 * a)
        qaly = daly / ca
        person.record_synthea.observation(:QALY, end_date, qaly, 'fhir' => :observation, 'ccda' => :no_action, 'category' => 'survey')
        qaly
      end

      # cost effectiveness ratio
      # CE Ratio = (cost of intervention - cost of comparator) / (DALY averted with intervention - DALY averted comparator)
      # http://healtheconomics.tuftsmedicalcenter.org/orchard/the-daly
    end
  end
end
