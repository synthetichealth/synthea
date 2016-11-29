require_relative '../test_helper'

class UtilsTest < Minitest::Test

  def test_normal_distribution
    samples = 100
    sum = 0
    ss = 0
    exp_mean = rand(10) - 5
    exp_sd = 1
    rng = Synthea::Utils::Distribution.normal(exp_mean, exp_sd)

    samples.times do
      v = rng.call
      sum += v
      ss += (v - exp_mean)**2
    end

    mean = sum.to_f / samples
    sd = Math.sqrt(ss.to_f / samples)
    # The mean should reasonably be within 0.5 of the expected mean
    assert (exp_mean - mean).abs < 0.5, "Expected mean not within a reasonable range"
    # The standard deviation should reasonably be within 0.3 of expected
    assert (exp_sd - sd).abs < 0.3, "Expected std not within a reasonable range"
  end

  def test_normal_distribution_with_seed
    seed = Random.new_seed
    rng1 = Synthea::Utils::Distribution.normal(0, 1, seed)
    rng2 = Synthea::Utils::Distribution.normal(0, 1, seed)
    assert_equal rng1.call, rng2.call
  end
end
