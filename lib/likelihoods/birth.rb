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
        gaussian(daily_births_per_square_mile*area, stddev)
      end

      def self.gaussian(mean, stddev, rand=lambda {Kernel.rand})
        theta = 2 * Math::PI * rand.call
        rho = Math.sqrt(-2 * Math.log(1 - rand.call))
        scale = stddev * rho
        x = mean + scale * Math.cos(theta)
        #y = mean + scale * Math.sin(theta)
        return x
      end


    end


  end
end