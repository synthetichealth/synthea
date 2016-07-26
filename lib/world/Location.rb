module Synthea
  class Location
    @@geom = GeoRuby::SimpleFeatures::Geometry.from_geojson(Synthea::MA_geo)
    @@running_total = 0
    @@geom.features.each do |feat|
      @@running_total += feat.properties["pop"]
    end
    @@city_zipcode_hash = JSON.parse(File.read('lib/world/city_zip.json'))

    def self.get_zipcode(city)
      @@city_zipcode_hash[city].sample
    end

    def self.selectPoint
      #randomly select a city
      feat_index, city_name = nil, nil
      rand_num = rand(@@running_total)
      @@geom.features.each_with_index do |val, index|
        rand_num -= val.properties["pop"]
        if rand_num < 0
          feat_index = index
          city_name = val.properties["cs_name"]
          break
        end
      end
      #determine rough boundaries of city
      city = @@geom.features[feat_index].geometry.geometries[0]
      max_y, max_x = -999, -999
      min_y, min_x = 999, 999
      city.rings[0].points.each do |point|
        max_x = point.x if point.x > max_x
        max_y = point.y if point.y > max_y
        min_x = point.x if point.x < min_x
        min_y = point.y if point.y < min_y
      end

      #randomly pick points within boundaries until one falls within city borders and return
      while true do
        x = rand(min_x..max_x)
        y = rand(min_y..max_y)
        point = GeoRuby::SimpleFeatures::Point.from_x_y(x,y)
        return {'point' => point, "city" => city_name} if city.contains_point?(point)
      end
    end
  end
end