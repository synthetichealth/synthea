module Synthea
  class Distributions

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
