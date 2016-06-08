module Synthea
  module World
    class BirthRate
      def initialize
        @area = Synthea::Config.population.area
        @population_variance = Synthea::Config.population.birth_variance
        @rate_per_sq_mile = (Synthea::Config.population.daily_births_per_square_mile * Synthea::Config.time_step)

        mean = @rate_per_sq_mile*@area
        @distribution = Distribution::Normal.rng(mean, mean*@population_variance)
      end

      def births()
        @distribution.call
      end
    end
  end
end