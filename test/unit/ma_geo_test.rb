require_relative '../test_helper'

class MA_Geo < Minitest::Test
  def setup
    @ma = Synthea::Location.class_variable_get(:@@geom)
  end

  def test_all_valid_cities
    @ma.features.each do |city|
      zipcode = Synthea::Location.get_zipcode(city.properties['cs_name'])
      assert(zipcode)
    end
  end

  def test_location_address
    bedford = @ma.features.find{|c| c.properties['cs_name']=="Bedford" }.geometry.geometries[0]
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

  def test_get_zipcode
    (1..10).each do |i|
      zip = Synthea::Location.get_zipcode("Bedford")
      assert([ "01730", "01731"].include?(zip))
    end
  end

  def test_select_point
    data = Synthea::Location.selectPoint
    city = @ma.features.find{|c| c.properties['cs_name']== data["city"] }.geometry.geometries[0]
    assert(city.contains_point?(data["point"]))
  end

end
