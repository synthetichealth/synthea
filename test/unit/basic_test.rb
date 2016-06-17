require_relative '../test_helper'

class BasicTest < Minitest::Test

  def setup
  end

  def test_noting
  end
  def test_10_year_risk_probability
  	weekly_risk = 1-((1-0.2) ** (7.to_f/3650))
  	puts weekly_risk
  	deaths = 0
  	10000.times do |x|
 		532.times do |y|
			if rand < weekly_risk
				deaths += 1
				break
			end
 		end
  	end
  	puts deaths
  end
end
