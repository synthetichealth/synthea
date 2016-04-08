module Synthea
  module Likelihood
    class Birth
      
      def self.daily_births_per_square_mile
        ma_births_per_year = 73000.0
        ma_square_miles = 10554.0
        days_in_year = 365.0

        ma_births_per_year/ma_square_miles/days_in_year
      end

      def self.likelihood(area, stddev)
        Synthea::Distributions.gaussian(daily_births_per_square_mile*area, stddev)
      end

    end
  end
end