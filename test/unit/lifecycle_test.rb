require_relative '../test_helper'

class LifecycleTest < Minitest::Test
	def setup
		@time = Time.now
		@person = Synthea::Person.new
		Synthea::Rules.apply(@time, @person)
	end

	#assume location is being picked in Bedford
	def test_location_address
		bedford = Synthea::BEDFORD
		bedford_point = GeoRuby::SimpleFeatures::Point.from_x_y(-71.2760,42.4906)
		west_of_Bedford = GeoRuby::SimpleFeatures::Point.from_x_y(-74.2760,42.4906)
		east_of_Bedford = GeoRuby::SimpleFeatures::Point.from_x_y(-67.2760,42.4906)
		north_of_Bedford = GeoRuby::SimpleFeatures::Point.from_x_y(-71.2760,45.4906)
		south_of_Bedford = GeoRuby::SimpleFeatures::Point.from_x_y(-71.2760,39.4906)

		assert(bedford.contains_point?(bedford_point))
		assert(!bedford.contains_point?(west_of_Bedford))
		assert(!bedford.contains_point?(east_of_Bedford))
		assert(!bedford.contains_point?(south_of_Bedford))
		assert(!bedford.contains_point?(north_of_Bedford))
	end

end