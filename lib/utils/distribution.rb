module Synthea
  module Utils
    class Distribution
      # Return a Proc object which returns a random number drawn
      # from the normal distribution given the mean and standard
      # deviation, i.e. from N(mean, sigma^2).
      def self.normal(mean = 0, sigma = 1, seed = nil)
        seed = Random.new_seed if seed.nil?
        r = Random.new(seed)
        returned = 0
        y1 = 0.0
        y2 = 0.0
        lambda do
          if returned.zero?
            w = 0.0
            x1 = 0.0
            x2 = 0.0
            loop do
              x1 = 2.0 * r.rand - 1.0
              x2 = 2.0 * r.rand - 1.0
              w = x1 * x1 + x2 * x2
              break unless w >= 1.0
            end
            w = Math.sqrt((-2.0 * Math.log(w)) / w)
            y1 = x1 * w
            y2 = x2 * w
            returned = 1
            y1 * sigma + mean
          else
            returned = 0
            y2 * sigma + mean
          end
        end
      end
    end
  end
end
